package com.lzu.marketplace.order.dto;

import com.lzu.marketplace.order.Order;

import java.time.LocalDateTime;

/**
 * 下单成功响应体（官方 §5 L1-6 示例）。
 *
 * <p>示例中的字段全部返回；{@code buyerName} 是题目未列出的附加字段，
 * 按 §3「data 中题目未明确要求的字段由考生决定」补充。
 */
public record OrderResponse(
        Long id,
        String orderNo,
        Long productId,
        String productTitle,
        int quantity,
        long unitPrice,
        long totalAmount,
        String status,
        String buyerName,
        String buyerPhone,
        String address,
        LocalDateTime createdAt) {

    public static OrderResponse from(Order o) {
        return new OrderResponse(
                o.id(),
                o.orderNo(),
                o.productId(),
                o.productTitle(),
                o.quantity(),
                o.unitPrice(),
                o.totalAmount(),
                o.status(),
                o.buyerName(),
                o.buyerPhone(),
                o.address(),
                o.createdAt());
    }
}
