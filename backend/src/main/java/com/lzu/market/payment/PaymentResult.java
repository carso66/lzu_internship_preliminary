package com.lzu.market.payment;

import java.time.LocalDateTime;

/**
 * 支付结果视图。paymentId 即 payments.payment_no。
 */
public record PaymentResult(
        String paymentId,
        String orderNo,
        long amount,
        String status,
        LocalDateTime paidAt) {
}
