package com.lzu.market.order;

import com.lzu.market.common.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderDao {

    private static final String COLS = "id, order_no, product_id, product_title, quantity, unit_price, "
            + "total_amount, status, buyer_name, buyer_phone, address, created_at, paid_at";

    private static final RowMapper<Order> MAPPER = (ResultSet rs, int rowNum) -> new Order(
            rs.getLong("id"),
            rs.getString("order_no"),
            rs.getLong("product_id"),
            rs.getString("product_title"),
            rs.getInt("quantity"),
            rs.getInt("unit_price"),
            rs.getLong("total_amount"),
            rs.getString("status"),
            rs.getString("buyer_name"),
            rs.getString("buyer_phone"),
            rs.getString("address"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("paid_at") == null ? null : rs.getTimestamp("paid_at").toLocalDateTime());

    private final JdbcTemplate jdbc;

    public OrderDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 写入订单，返回自增 id */
    public long insert(String orderNo, long productId, String productTitle, int quantity,
                       int unitPrice, long totalAmount,
                       String buyerName, String buyerPhone, String address) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO orders (order_no, product_id, product_title, quantity, unit_price, "
                            + "total_amount, status, buyer_name, buyer_phone, address) "
                            + "VALUES (?, ?, ?, ?, ?, ?, 'created', ?, ?, ?)",
                    new String[]{"id"});
            ps.setString(1, orderNo);
            ps.setLong(2, productId);
            ps.setString(3, productTitle);
            ps.setInt(4, quantity);
            ps.setInt(5, unitPrice);
            ps.setLong(6, totalAmount);
            ps.setString(7, buyerName);
            ps.setString(8, buyerPhone);
            ps.setString(9, address);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public Optional<Order> findById(long id) {
        List<Order> list = jdbc.query(
                "SELECT " + COLS + " FROM orders WHERE id = ?", MAPPER, id);
        return list.stream().findFirst();
    }

    public Optional<Order> findByNo(String orderNo) {
        List<Order> list = jdbc.query(
                "SELECT " + COLS + " FROM orders WHERE order_no = ?", MAPPER, orderNo);
        return list.stream().findFirst();
    }

    /** 订单列表：buyer_phone 等值 + created_at 倒序，total 与 items 共用同一个 where 与 args */
    public PageResult<Order> findPageByPhone(String buyerPhone, int page, int pageSize) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE buyer_phone = ?", Long.class, buyerPhone);

        List<Order> items = jdbc.query(
                "SELECT " + COLS + " FROM orders WHERE buyer_phone = ? "
                        + "ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                MAPPER, buyerPhone, pageSize, (long) (page - 1) * pageSize);

        return new PageResult<>(total == null ? 0 : total, page, pageSize, items);
    }

    /**
     * 状态机式改单：只有还没支付的订单会被改成 paid。
     * 返回 0 说明这一单已经被别的事务改过了，调用方据此判定重复支付。
     */
    public int markPaid(long id, Timestamp paidAt) {
        return jdbc.update(
                "UPDATE orders SET status = 'paid', paid_at = ? WHERE id = ? AND status <> 'paid'",
                paidAt, id);
    }
}
