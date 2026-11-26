package com.lzu.marketplace.order;

import com.lzu.marketplace.common.BusinessException;
import com.lzu.marketplace.common.ErrorCode;
import com.lzu.marketplace.common.PageResult;
import com.lzu.marketplace.order.dto.CreateOrderRequest;
import com.lzu.marketplace.order.dto.OrderQuery;
import com.lzu.marketplace.order.dto.OrderResponse;
import com.lzu.marketplace.product.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 下单业务（官方 §5 L1-6）。
 *
 * <p><b>事务边界</b>：{@link #create} 上的 {@code @Transactional} 是唯一的事务边界。
 * 锁商品 → 校验 → 扣库存 → 算价 → 插订单全部在这一个事务里，
 * 任一环节抛异常整体回滚，已扣的库存随之恢复。
 *
 * <p><b>为什么用行锁而不是条件 UPDATE</b>：先 {@code SELECT ... FOR UPDATE} 锁住商品行，
 * 使「读库存 → 判断 → 扣减」对并发请求串行化。锁在事务提交前一直持有，
 * 所以校验用的库存值和实际扣减的值一定是同一份。
 *
 * <p><b>不要在类内自调用其他 @Transactional 方法</b>：Spring 的代理在跨 bean 调用时生效，
 * {@code this.xxx()} 会绕过代理、不开新事务。这里所有步骤都直接写在 {@link #create} 内。
 */
@Service
public class OrderService {

    private final OrderRepository repository;
    private final OrderNoGenerator orderNoGenerator;

    public OrderService(OrderRepository repository, OrderNoGenerator orderNoGenerator) {
        this.repository = repository;
        this.orderNoGenerator = orderNoGenerator;
    }

    /**
     * 创建订单。
     *
     * <p>失败语义（题目 §3 码表）：
     * <ul>
     *   <li>商品不存在 → 404 + 40401</li>
     *   <li>商品已下架 → 409 + 40902</li>
     *   <li>库存不足 → 409 + 40901</li>
     * </ul>
     *
     * <p>金额全程 {@code long} 存「分」，乘法用 {@link Math#multiplyExact}：
     * 溢出抛 {@code ArithmeticException}，被兜底处理器转成 50000，
     * 而不是悄悄得到一个负数金额。
     */
    @Transactional
    public OrderResponse create(CreateOrderRequest req) {
        int quantity = req.quantity();

        // 1. 锁住商品行，锁持有到事务结束
        Product product = repository.findByIdForUpdate(req.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品不存在"));

        // 2. 业务校验
        if (Product.STATUS_OFF.equals(product.status())) {
            throw new BusinessException(ErrorCode.PRODUCT_OFF_SHELF, "商品已下架");
        }
        if (product.stock() < quantity) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT, "库存不足");
        }

        // 3. 扣库存，必须检查影响行数（题目 L1-6 第 8 条）
        int affected = repository.deductStock(product.id(), quantity);
        if (affected != 1) {
            // 锁内已校验过库存，走到这里说明有未预期的并发写入或 id 变化。
            // 不静默放过：宁可 409 也不能让库存和订单不一致。
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT, "库存不足");
        }

        // 4. 金额只取数据库里的价格，不接受客户端传值（题目 L1-6 第 5 条）
        long unitPrice = product.price();
        long totalAmount = Math.multiplyExact(unitPrice, (long) quantity);

        // 5. 插订单（title / unitPrice 为快照）
        Order order = repository.insert(
                orderNoGenerator.next(),
                product.id(),
                product.title(),
                quantity,
                unitPrice,
                totalAmount,
                req.buyerName().trim(),
                req.buyerPhone().trim(),
                req.address().trim());

        return OrderResponse.from(order);
    }

    /**
     * 按订单号查详情（官方 §5 L1-7）。不存在 → 404 + 40401。
     */
    public OrderResponse getByOrderNo(String orderNo) {
        return repository.findByOrderNo(orderNo)
                .map(OrderResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "订单不存在"));
    }

    /**
     * 按买家手机号分页查询订单（官方 §5 L1-8）。
     *
     * <p>手机号必填的校验在 {@link OrderQuery#of} 里完成；空结果返回 {@code items: []}。
     */
    public PageResult<OrderResponse> listByBuyerPhone(OrderQuery query) {
        PageResult<Order> page = repository.searchByBuyerPhone(query);
        List<OrderResponse> items = page.items().stream()
                .map(OrderResponse::from)
                .toList();
        return PageResult.of(page.total(), page.page(), page.pageSize(), items);
    }
}
