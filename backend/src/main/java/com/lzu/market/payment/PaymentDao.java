package com.lzu.market.payment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Repository
public class PaymentDao {

    private final JdbcTemplate jdbc;

    public PaymentDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 写支付流水。payments.order_id 上有唯一约束，
     * 同一单并发插入第二条会抛 DuplicateKeyException，这是幂等的最后一道闸。
     */
    public void insert(String paymentNo, long orderId, String method, long amount, LocalDateTime paidAt) {
        jdbc.update(
                "INSERT INTO payments (payment_no, order_id, method, amount, status, paid_at) "
                        + "VALUES (?, ?, ?, ?, 'paid', ?)",
                paymentNo, orderId, method, amount, Timestamp.valueOf(paidAt));
    }
}
