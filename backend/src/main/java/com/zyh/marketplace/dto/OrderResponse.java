package com.zyh.marketplace.dto;

/**
 * 订单响应 DTO（camelCase），时间字段输出为 yyyy-MM-dd HH:mm:ss。
 * 金额 unitPrice / totalAmount 为整数「分」。
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
        String buyerName,
        String buyerPhone,
        String address,
        String paidAt,
        String createdAt
) {
}
