package com.lzu.marketplace.order.service;

import com.lzu.marketplace.common.BusinessException;
import com.lzu.marketplace.common.PageResult;
import com.lzu.marketplace.order.dto.CreateOrderRequest;
import com.lzu.marketplace.order.model.Order;
import com.lzu.marketplace.order.repository.OrderRepository;
import com.lzu.marketplace.product.model.Product;
import com.lzu.marketplace.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单业务逻辑：下单扣库存（事务）、订单详情、按手机号分页查订单。
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** 商品上架状态值，与 products.status 取值一致。 */
    private static final String PRODUCT_STATUS_ON = "on";
    /** 条件扣库存失败后的最大重试次数，用于兜住并发下的瞬时状态变化。 */
    private static final int MAX_DEDUCT_ATTEMPTS = 3;
    /** 库存不足 */
    private static final int CODE_STOCK_NOT_ENOUGH = 40901;
    /** 商品已下架 */
    private static final int CODE_PRODUCT_OFF_SHELF = 40902;
    /** 并发冲突（商品状态在扣减瞬间发生变化） */
    private static final int CODE_CONCURRENT_CONFLICT = 40900;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    /**
     * 下单：校验商品 → 算金额 → 扣库存 → 插订单。
     *
     * <p>整个方法是一个数据库事务：扣库存和插订单要么都成功，要么一起回滚，
     * 不会出现"库存扣了但订单没写进去"的情况。</p>
     */
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        long productId = request.productId();
        int quantity = request.quantity();
        if (quantity < 1) {
            throw BusinessException.badRequest("quantity 必须大于等于 1");
        }

        // 1. 查商品，判断存在性、上下架和库存
        Product product = productRepository.findById(productId);
        if (product == null) {
            throw BusinessException.notFound("商品不存在");
        }
        if (!PRODUCT_STATUS_ON.equals(product.status())) {
            throw BusinessException.conflict(CODE_PRODUCT_OFF_SHELF, "商品已下架");
        }
        if (product.stock() < quantity) {
            throw BusinessException.conflict(CODE_STOCK_NOT_ENOUGH, "库存不足");
        }

        // 2. 算金额：价格只取数据库里的值，不接受客户端传入的价格
        long unitPrice = product.price();
        long totalAmount = calculateTotalAmount(unitPrice, quantity);

        // 3. 扣库存：带条件的原子 UPDATE，靠受影响行数判断成败
        deductStock(productId, quantity);

        // 4. 生成订单号并落库（insert 内部完成取主键和回读）
        String orderNo = orderRepository.nextOrderNo();
        return orderRepository.insert(
                orderNo,
                product.id(),
                product.title(),
                quantity,
                unitPrice,
                totalAmount,
                request.buyerName().trim(),
                request.buyerPhone().trim(),
                request.address().trim());
    }

    /** 按订单号查详情，不存在返回 404 + 40401。 */
    public Order getByOrderNo(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        return order;
    }

    /** 按手机号分页查订单，按创建时间倒序。 */
    public PageResult<Order> list(String buyerPhone, Integer page, Integer pageSize) {
        String phone = (buyerPhone == null) ? null : buyerPhone.trim();
        if (phone == null || phone.isEmpty()) {
            throw BusinessException.badRequest("buyerPhone 不能为空");
        }
        if (phone.length() > 20) {
            throw BusinessException.badRequest("buyerPhone 长度不能超过 20");
        }

        int p = (page == null) ? 1 : page;
        int size = (pageSize == null) ? 10 : pageSize;
        if (p < 1) {
            throw BusinessException.badRequest("page 必须大于等于 1");
        }
        if (size < 1 || size > 50) {
            throw BusinessException.badRequest("pageSize 必须在 1 到 50 之间");
        }

        // offset 用 long 计算，page 很大时也不会发生 int 溢出
        long offset = (long) (p - 1) * size;
        return orderRepository.findPageByPhone(phone, p, size, offset);
    }

    /**
     * 金额 = 单价 × 数量，全程整数分。
     *
     * <p>用 Math.multiplyExact 检测乘法溢出（BigInt 列只能挡住"存不下"，
     * 挡不住"乘法过程本身溢出"）。溢出时按参数错误返回 400，
     * 并且这一步排在扣库存之前，不会留下已扣的库存。</p>
     */
    private static long calculateTotalAmount(long unitPrice, int quantity) {
        try {
            return Math.multiplyExact(unitPrice, (long) quantity);
        } catch (ArithmeticException ex) {
            throw BusinessException.badRequest("金额计算超出范围，请检查商品单价和购买数量");
        }
    }

    /**
     * 扣库存：用带条件的原子 UPDATE，成功与否只看受影响行数。
     *
     * <p>UPDATE 语句带 {@code status = 'on' AND stock >= ?} 条件，
     * 由数据库在拿到行锁后按最新数据判定，并发下单时不会超卖。</p>
     *
     * <p>受影响行数为 0 时重新查一次商品来区分原因：
     * 商品没了 → 404；商品下架 → 40902；库存不够 → 40901；
     * 三者都不满足（条件全对却没扣上）说明并发下状态发生了瞬时变化，重试即可。</p>
     */
    private void deductStock(long productId, int quantity) {
        for (int attempt = 1; attempt <= MAX_DEDUCT_ATTEMPTS; attempt++) {
            int rows = productRepository.deductStock(productId, quantity);
            if (rows == 1) {
                return;
            }

            Product latest = productRepository.findById(productId);
            if (latest == null) {
                throw BusinessException.notFound("商品不存在");
            }
            if (!PRODUCT_STATUS_ON.equals(latest.status())) {
                throw BusinessException.conflict(CODE_PRODUCT_OFF_SHELF, "商品已下架");
            }
            if (latest.stock() < quantity) {
                throw BusinessException.conflict(CODE_STOCK_NOT_ENOUGH, "库存不足");
            }

            // 条件全部满足却没扣上：并发下有其它请求刚改了这行，重试一次
            log.debug("扣库存未命中且条件满足，重试第 {} 次，productId={}", attempt, productId);
        }
        throw BusinessException.conflict(CODE_CONCURRENT_CONFLICT, "商品状态并发变化，请稍后重试");
    }
}
