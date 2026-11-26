package com.lzu.marketplace.payment;

import java.time.LocalDateTime;

public record Payment(String paymentId, String orderNo, long amount, String status, LocalDateTime paidAt) {
}
