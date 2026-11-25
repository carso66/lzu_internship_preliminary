package com.example.marketplace.dto;

import com.example.marketplace.domain.Payment;

import java.time.OffsetDateTime;

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
