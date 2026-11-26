package com.lzu.marketplace.payment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {
    private final JdbcTemplate jdbc;
    public PaymentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(Payment payment, String method) {
        jdbc.update("INSERT INTO payments(payment_id, order_no, method, amount, status, paid_at) VALUES (?, ?, ?, ?, ?, ?)",
                payment.paymentId(), payment.orderNo(), method, payment.amount(), payment.status(), payment.paidAt());
    }
}
