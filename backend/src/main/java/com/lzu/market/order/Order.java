package com.lzu.market.order;

import java.time.LocalDateTime;

/**
 * 订单视图。product_title / unit_price 是下单那一刻的快照，商品后来改价改名都不影响已成的订单。
 */
public record Order(
        long id,
        String orderNo,
        long productId,
        String productTitle,
        int quantity,
        int unitPrice,
        long totalAmount,
        String status,
        String buyerName,
        String buyerPhone,
        String address,
        LocalDateTime createdAt,
        LocalDateTime paidAt) {
}
