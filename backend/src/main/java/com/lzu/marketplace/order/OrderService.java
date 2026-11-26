package com.lzu.marketplace.order;

import com.lzu.marketplace.common.BusinessException;
import com.lzu.marketplace.product.ProductRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final ProductRepository products;
    private final OrderRepository orders;

    public OrderService(ProductRepository products, OrderRepository orders) {
        this.products = products;
        this.orders = orders;
    }

    @Transactional
    public Order create(CreateOrderRequest request) {
        // 行锁在事务提交前保持；下单、上下架共享同一商品行的数据库锁。
        var product = products.find(request.productId(), true)
                .orElseThrow(() -> BusinessException.notFound("商品不存在"));
        if (!"on".equals(product.status())) throw new BusinessException(409, 40902, "商品已下架");
        if (product.stock() < request.quantity()) throw new BusinessException(409, 40901, "库存不足");
        long total;
        try {
            total = Math.multiplyExact(product.price(), (long) request.quantity());
        } catch (ArithmeticException ex) {
            throw BusinessException.badRequest("quantity与价格相乘后金额超出范围");
        }
        if (products.deductStock(product.id(), request.quantity()) != 1)
            throw new BusinessException(409, 40901, "库存不足");
        // 插入失败时异常向外抛出，由 Spring 回滚刚才的库存扣减。
        return orders.insert("SO" + UUID.randomUUID().toString().replace("-", ""), product, request, total);
    }

    @Transactional(readOnly = true)
    public Order detail(String orderNo) {
        return orders.find(orderNo, false).orElseThrow(() -> BusinessException.notFound("订单不存在"));
    }

    @Transactional(readOnly = true)
    public OrderPage list(OrderQuery query) { return orders.list(query); }
}
