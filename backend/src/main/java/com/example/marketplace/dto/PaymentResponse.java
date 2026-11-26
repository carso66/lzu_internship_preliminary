package com.example.marketplace.dto;

import com.example.marketplace.domain.Payment;

import java.time.OffsetDateTime;

/**
 * 支付响应；amount 使用整数分，当前模拟支付的 status 固定为 paid。
 */
public record PaymentResponse(
        String paymentId,
        Long orderId,
        String orderNo,
        Long amount,
        String method,
        String status,
        OffsetDateTime paidAt
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getOrderId(),
                payment.getOrderNo(),
                payment.getAmount(),
                payment.getMethod(),
                "paid",
                payment.getPaidAt()
        );
    }
}
