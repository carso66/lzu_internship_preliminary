package com.lzu.marketplace.order;

import com.lzu.marketplace.common.ApiCode;
import com.lzu.marketplace.common.BusinessException;
import com.lzu.marketplace.product.Product;
import com.lzu.marketplace.product.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderNumberGenerator orderNumberGenerator;

    public OrderService(ProductRepository productRepository,
                        OrderRepository orderRepository,
                        OrderNumberGenerator orderNumberGenerator) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderNumberGenerator = orderNumberGenerator;
    }

    /**
     * 下单在一个事务内完成：锁定商品行、校验状态和库存、扣库存、插入订单。
     * 锁定商品行后，状态和库存的检查与扣减之间不会被其他事务插入修改，
     * 因此不需要在扣减失败后再做容易产生竞态歧义的原因推断。
     */
    @Transactional
    public Order create(CreateOrderRequest request) {
        Product product = productRepository.findByIdForUpdate(request.productId())
                .orElseThrow(() -> notFound("商品不存在"));

        if (!"on".equals(product.status())) {
            throw conflict(ApiCode.PRODUCT_OFF_SHELF, "商品已下架");
        }
        if (product.stock() < request.quantity()) {
            throw conflict(ApiCode.OUT_OF_STOCK, "库存不足");
        }

        long totalAmount;
        try {
            totalAmount = Math.multiplyExact(product.price(), request.quantity().longValue());
        } catch (ArithmeticException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, ApiCode.BAD_REQUEST,
                    "quantity: 购买数量过大，金额超出范围");
        }

        int changed = productRepository.decreaseStock(product.id(), request.quantity());
        if (changed != 1) {
            // 正常路径上商品行已锁定，扣减应当成功。这里仍重新读取并分类，
            // 避免把并发导致的状态/库存变化误报成 500；若最新状态仍可下单，
            // 说明是真正的内部不变量异常，让事务回滚。
            Product latest = productRepository.findByIdForUpdate(product.id())
                    .orElseThrow(() -> notFound("商品不存在"));
            if (!"on".equals(latest.status())) {
                throw conflict(ApiCode.PRODUCT_OFF_SHELF, "商品已下架");
            }
            if (latest.stock() < request.quantity()) {
                throw conflict(ApiCode.OUT_OF_STOCK, "库存不足");
            }
            throw new IllegalStateException("扣减库存失败");
        }

        String orderNo = orderNumberGenerator.next();
        long id = orderRepository.insert(new OrderDraft(
                orderNo,
                product.id(),
                product.title(),
                request.quantity(),
                product.price(),
                totalAmount,
                request.buyerName(),
                request.buyerPhone(),
                request.address()
        ));
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("插入订单后无法读取订单"));
    }

    @Transactional(readOnly = true)
    public Order getByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.length() > 64) {
            throw notFound("订单不存在");
        }
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> notFound("订单不存在"));
    }

    @Transactional(readOnly = true)
    public OrderPage list(OrderListQuery query) {
        return new OrderPage(
                orderRepository.countByBuyerPhone(query.getBuyerPhone()),
                query.getPage(),
                query.getPageSize(),
                orderRepository.findPageByBuyerPhone(
                        query.getBuyerPhone(), query.getPageSize(), query.offset())
        );
    }

    private BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, ApiCode.NOT_FOUND, message);
    }

    private BusinessException conflict(int code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }
}
