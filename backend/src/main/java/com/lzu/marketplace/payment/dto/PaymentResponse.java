package com.lzu.marketplace.payment.dto;

import com.lzu.marketplace.payment.Payment;

import java.time.LocalDateTime;

/**
 * 支付成功响应体（官方 §6 L2-9 示例）。
 *
 * <p>示例只列了 {@code paymentId / orderNo / amount / status / paidAt} 五个字段，
 * 这里严格照示例返回，不额外返回 {@code method} 或 {@code createdAt}。
 */
public record PaymentResponse(
        String paymentId,
        String orderNo,
        long amount,
        String status,
        LocalDateTime paidAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.paymentId(),
                p.orderNo(),
                p.amount(),
                p.status(),
                p.paidAt());
    }
}
