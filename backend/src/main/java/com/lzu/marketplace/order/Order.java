package com.lzu.marketplace.order;

import java.time.OffsetDateTime;

/** 数据库中的订单，也是订单详情和列表使用的返回结构。金额单位为分。 */
public record Order(
        Long id,
        String orderNo,
        Long productId,
        String productTitle,
        Integer quantity,
        Long unitPrice,
        Long totalAmount,
        String status,
        String buyerName,
        String buyerPhone,
        String address,
        OffsetDateTime createdAt,
        OffsetDateTime paidAt
) {
}
