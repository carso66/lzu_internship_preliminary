package com.example.marketplace.dto;

import com.example.marketplace.domain.Order;

import java.time.OffsetDateTime;

/**
 * 订单响应；金额字段使用整数分，商品标题和单价是下单时的成交快照。
 */
public record OrderResponse(
        Long id,
        String orderNo,
        Long productId,
        String productTitle,
        Integer quantity,
        Long unitPrice,
        Long totalAmount,
        String status,
        String buyerPhone,
        String address,
        OffsetDateTime createdAt,
        OffsetDateTime paidAt
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getProductId(),
                order.getProductTitle(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getBuyerPhone(),
                order.getAddress(),
                order.getCreatedAt(),
                order.getPaidAt()
        );
    }
}
