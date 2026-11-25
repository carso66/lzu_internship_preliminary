package com.lzu.marketplace.payment;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Repository
public class PaymentRepository {

    private static final String COLUMNS =
            "payment_no, order_no, amount, method, status, paid_at";

    private static final RowMapper<Payment> ROW_MAPPER = (rs, rowNum) -> new Payment(
            rs.getString("payment_no"),
            rs.getString("order_no"),
            rs.getLong("amount"),
            rs.getString("method"),
            rs.getString("status"),
            rs.getObject("paid_at", OffsetDateTime.class).withOffsetSameInstant(ZoneOffset.UTC)
    );

    private final NamedParameterJdbcTemplate jdbc;

    public PaymentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(PaymentDraft draft) {
        jdbc.update("""
                        INSERT INTO payments (payment_no, order_no, amount, method, paid_at)
                        VALUES (:paymentNo, :orderNo, :amount, :method, :paidAt)
                        """,
                new MapSqlParameterSource()
                        .addValue("paymentNo", draft.paymentNo())
                        .addValue("orderNo", draft.orderNo())
                        .addValue("amount", draft.amount())
                        .addValue("method", draft.method())
                        .addValue("paidAt", draft.paidAt()));
    }

    public Optional<Payment> findByPaymentNo(String paymentNo) {
        return jdbc.query("SELECT " + COLUMNS + " FROM payments WHERE payment_no = :paymentNo",
                        new MapSqlParameterSource().addValue("paymentNo", paymentNo), ROW_MAPPER)
                .stream().findFirst();
    }
}
