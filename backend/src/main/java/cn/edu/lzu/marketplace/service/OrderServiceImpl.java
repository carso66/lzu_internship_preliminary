package cn.edu.lzu.marketplace.service;

import cn.edu.lzu.marketplace.common.BusinessException;
import cn.edu.lzu.marketplace.common.ErrorCode;
import cn.edu.lzu.marketplace.dto.CreateOrderRequest;
import cn.edu.lzu.marketplace.dto.OrderPageResponse;
import cn.edu.lzu.marketplace.dto.OrderResponse;
import cn.edu.lzu.marketplace.model.Order;
import cn.edu.lzu.marketplace.model.PageResult;
import cn.edu.lzu.marketplace.model.Product;
import cn.edu.lzu.marketplace.repository.OrderRepository;
import cn.edu.lzu.marketplace.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 订单业务实现。
 *
 * <p>下单核心流程（整个方法处于同一数据库事务，任一步失败即回滚，含已扣减的库存）：
 * <ol>
 *   <li>查商品：不存在 → 404 40401；已下架 → 409 40902。</li>
 *   <li>服务端算价 totalAmount = unitPrice × quantity，乘法溢出 → 400 40001。</li>
 *   <li>原子扣减库存（条件更新，同时校验 status='on' 与 stock ≥ quantity）。</li>
 *   <li>条件更新影响行数为 0 时，重读商品区分真实原因，避免并发下误判业务码。</li>
 *   <li>插入订单（先用临时占位订单号），拿到自增主键后生成正式订单号并回填。</li>
 * </ol>
 */
@Service
public class OrderServiceImpl implements OrderService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public OrderServiceImpl(ProductRepository productRepository, OrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        Long productId = request.productId();
        int quantity = request.quantity();

        // 1. 查商品：不存在 / 已下架 先行判定（商品不会被删除，此判定可靠）
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商品不存在"));
        if (Product.STATUS_OFF.equals(product.status())) {
            throw new BusinessException(ErrorCode.PRODUCT_OFF_SALE, "商品已下架");
        }

        // 2. 服务端算价，拒绝客户端传价；乘法溢出按参数错误处理
        long unitPrice = product.price();
        long totalAmount;
        try {
            totalAmount = Math.multiplyExact(unitPrice, quantity);
        } catch (ArithmeticException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "购买数量过大，订单总额超出范围");
        }

        // 3. 原子扣减库存：仅当上架且库存充足时生效，避免「先查库存再无条件更新」的竞态
        int deducted = productRepository.deductStockIfAvailable(productId, quantity);
        if (deducted == 0) {
            throw resolveDeductFailure(productId);
        }

        // 4. 插入订单 + 回填正式订单号（同一事务）
        LocalDateTime now = LocalDateTime.now();
        Order order = new Order(
                null,
                placeholderOrderNo(),
                productId,
                product.title(),
                quantity,
                unitPrice,
                totalAmount,
                request.buyerName(),
                request.buyerPhone(),
                request.address(),
                Order.STATUS_CREATED,
                now,
                null);
        Long id = orderRepository.insert(order);
        String orderNo = OrderNoGenerator.generate(id, now);
        int updated = orderRepository.updateOrderNo(id, orderNo);
        if (updated != 1) {
            // 回填影响行数不为 1 说明订单行未按预期更新：若不抛异常，事务可能提交，
            // 导致返回给客户端的正式订单号与库中实际保存值不一致。抛运行时异常触发回滚。
            throw new IllegalStateException("回填订单编号失败，影响行数=" + updated + "，id=" + id);
        }

        return OrderResponse.from(order.withId(id).withOrderNo(orderNo));
    }

    @Override
    public OrderResponse getByOrderNo(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "订单不存在"));
        return OrderResponse.from(order);
    }

    @Override
    public OrderPageResponse list(Integer page, Integer pageSize, String buyerPhone) {
        int p = page == null ? 1 : page;
        int size = pageSize == null ? 10 : pageSize;
        // 用 long 计算偏移量，防止 (p-1)*size 在 page 接近 Integer.MAX_VALUE 时 int 溢出变负
        long offset = (long) (p - 1) * size;

        PageResult<Order> result = orderRepository.findPage(offset, size, buyerPhone);
        List<OrderResponse> items = result.items().stream().map(OrderResponse::from).toList();
        return new OrderPageResponse(result.total(), p, size, items);
    }

    /**
     * 条件更新影响行数为 0 时的失败原因定位。
     *
     * <p>前置已排除「不存在 / 下架」，但扣减动作与前置读取之间存在并发窗口：
     * 可能因他人把库存扣到不足、或把商品下架而失败。这里重读一次，区分出
     * 404（不应发生，防御性）、40902（并发下架）、40901（库存不足，含并发超卖）。
     */
    private BusinessException resolveDeductFailure(Long productId) {
        Product p = productRepository.findById(productId).orElse(null);
        if (p == null) {
            return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商品不存在");
        }
        if (Product.STATUS_OFF.equals(p.status())) {
            return new BusinessException(ErrorCode.PRODUCT_OFF_SALE, "商品已下架");
        }
        return new BusinessException(ErrorCode.STOCK_INSUFFICIENT, "库存不足");
    }

    /**
     * 生成临时占位订单号，仅用于满足 order_no 非空唯一约束；
     * 插入拿到自增主键后立即在同事务内被正式编号覆盖，不会对外暴露。
     *
     * <p>长度为 33 字符（"P" + 32 位十六进制 UUID），不超出 order_no 列声明的
     * VARCHAR(48)，详见 JdbcOrderRepository 表结构说明中的长度兼容性备注。
     */
    private static String placeholderOrderNo() {
        return "P" + UUID.randomUUID().toString().replace("-", "");
    }
}
