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
 * 订单业务实现。协调商品与订单的 Repository，将查询结果和下单快照转换为响应 DTO。
 *
 * <p>下单方法的事务覆盖扣库存、插入订单和正式编号回填，运行时异常使这些写操作一起回滚。
 */
@Service
public class OrderServiceImpl implements OrderService {

    // 通过构造器注入商品和订单两个 Repository，分别用于读取商品与扣减库存、持久化订单。
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public OrderServiceImpl(ProductRepository productRepository, OrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    /** 创建订单：检查商品并按库中单价计算金额，再扣减库存并保存订单快照。 */
    @Override
    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        Long productId = request.productId();
        int quantity = request.quantity();

        // ① 查询商品，先判定不存在和已下架两种失败情况。
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商品不存在"));
        if (Product.STATUS_OFF.equals(product.status())) {
            throw new BusinessException(ErrorCode.PRODUCT_OFF_SALE, "商品已下架");
        }

        // ② 服务端算价
        // 使用数据库单价，以整数分计算总额；乘法溢出按参数错误处理。
        long unitPrice = product.price();
        long totalAmount;
        try {
            totalAmount = Math.multiplyExact(unitPrice, quantity);
        } catch (ArithmeticException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "购买数量过大，订单总额超出范围");
        }

        // ③ 原子扣减库存
        // 在同一条条件 UPDATE 中检查上架状态和剩余库存，避免并发请求依据旧库存分别扣减。
        int deducted = productRepository.deductStockIfAvailable(productId, quantity);
        if (deducted == 0) {
            throw resolveDeductFailure(productId);
        }

        // ④ 写入订单快照
        // 保存下单时的商品名称、单价和总额。正式编号依赖自增主键，插入时先使用临时编号。
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
        // ⑤ 生成并回填正式订单号
        // 只有回填恰好一行才返回成功。异常上抛后由事务管理器撤销已执行的写操作。
        String orderNo = OrderNoGenerator.generate(id, now);
        int updated = orderRepository.updateOrderNo(id, orderNo);
        if (updated != 1) {
            throw new IllegalStateException("回填订单编号失败，影响行数=" + updated + "，id=" + id);
        }

        return OrderResponse.from(order.withId(id).withOrderNo(orderNo));
    }

    /** 订单详情：按正式编号读取保存的订单快照；根据正式订单号查询订单详情。 */
    @Override
    public OrderResponse getByOrderNo(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "订单不存在"));
        return OrderResponse.from(order);
    }

    /** 买家订单列表：将页码换算为偏移量，并把当前页领域对象映射为响应 DTO；根据买家手机号分页查询订单，并转换为响应 DTO */
    @Override
    public OrderPageResponse list(Integer page, Integer pageSize, String buyerPhone) {
        int p = page == null ? 1 : page;
        int size = pageSize == null ? 10 : pageSize;
        // 分页偏移从 0 开始；在乘法前转换为 long，避免大页码导致 int 溢出。
        long offset = (long) (p - 1) * size;

        PageResult<Order> result = orderRepository.findPage(offset, size, buyerPhone);
        List<OrderResponse> items = result.items().stream().map(OrderResponse::from).toList();
        return new OrderPageResponse(result.total(), p, size, items);
    }

    /**
     * 条件更新影响行数为 0 时的失败原因定位。
     *
     * <p>前置已排除「不存在 / 下架」，但扣减与前置读取之间可能发生库存变化或下架。
     * 扣减未命中时重新查询商品，按重读结果选择业务码：商品缺失为 40401，已下架为 40902，
     * 其余情况统一返回 40901（库存不足）。
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
     * UUID 降低临时订单号的冲突概率，数据库唯一约束拒绝重复编号。
     */
    private static String placeholderOrderNo() {
        return "P" + UUID.randomUUID().toString().replace("-", "");
    }
}
