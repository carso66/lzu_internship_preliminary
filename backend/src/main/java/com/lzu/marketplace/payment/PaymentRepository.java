package com.lzu.marketplace.payment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;

/**
 * payments 表持久层。
 *
 * <p>只负责插入与按 order_no 查询。防重复支付靠两层：
 * 应用层「锁订单行 + 判状态」，数据库层 {@code uk_payments_order_no} 唯一约束兜底。
 */
@Repository
public class PaymentRepository {

    private static final String PAYMENT_COLUMNS =
            "id, payment_id, order_no, amount, method, status, paid_at, created_at";

    private static final RowMapper<Payment> ROW_MAPPER = (rs, rowNum) -> new Payment(
            rs.getLong("id"),
            rs.getString("payment_id"),
            rs.getString("order_no"),
            rs.getLong("amount"),
            rs.getString("method"),
            rs.getString("status"),
            rs.getObject("paid_at", LocalDateTime.class),
            rs.getObject("created_at", LocalDateTime.class));

    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 插入一条支付记录并回填 id。
     */
    public Payment insert(String paymentId, String orderNo, long amount, String method,
                          LocalDateTime paidAt) {
        String sql = "INSERT INTO payments (payment_id, order_no, amount, method, status, paid_at) " +
                     "VALUES (?, ?, ?, ?, 'paid', ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, paymentId);
            ps.setString(2, orderNo);
            ps.setLong(3, amount);
            ps.setString(4, method);
            ps.setObject(5, paidAt);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("插入支付记录后未取得自增主键");
        }
        return findById(key.longValue())
                .orElseThrow(() -> new IllegalStateException("插入支付记录后无法回读 id=" + key));
    }

    public java.util.Optional<Payment> findById(long id) {
        return jdbc.query(
                        "SELECT " + PAYMENT_COLUMNS + " FROM payments WHERE id = ?",
                        ROW_MAPPER, id)
                .stream()
                .findFirst();
    }

    public java.util.Optional<Payment> findByOrderNo(String orderNo) {
        return jdbc.query(
                        "SELECT " + PAYMENT_COLUMNS + " FROM payments WHERE order_no = ?",
                        ROW_MAPPER, orderNo)
                .stream()
                .findFirst();
    }
}
