package com.lzu.marketplace.order;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class OrderRepository {

    private static final String COLUMNS =
            "id, order_no, product_id, product_title, quantity, unit_price, total_amount, "
                    + "buyer_name, buyer_phone, address, status, created_at, paid_at";

    private static final RowMapper<Order> ROW_MAPPER = (rs, rowNum) -> {
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class)
                .withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime paidAt = rs.getObject("paid_at", OffsetDateTime.class);
        if (paidAt != null) {
            paidAt = paidAt.withOffsetSameInstant(ZoneOffset.UTC);
        }
        return new Order(
                rs.getLong("id"),
                rs.getString("order_no"),
                rs.getLong("product_id"),
                rs.getString("product_title"),
                rs.getInt("quantity"),
                rs.getLong("unit_price"),
                rs.getLong("total_amount"),
                rs.getString("status"),
                rs.getString("buyer_name"),
                rs.getString("buyer_phone"),
                rs.getString("address"),
                createdAt,
                paidAt
        );
    };

    private final NamedParameterJdbcTemplate jdbc;

    public OrderRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(OrderDraft draft) {
        String sql = """
                INSERT INTO orders (order_no, product_id, product_title, quantity, unit_price,
                                    total_amount, buyer_name, buyer_phone, address)
                VALUES (:orderNo, :productId, :productTitle, :quantity, :unitPrice,
                        :totalAmount, :buyerName, :buyerPhone, :address)
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("orderNo", draft.orderNo())
                .addValue("productId", draft.productId())
                .addValue("productTitle", draft.productTitle())
                .addValue("quantity", draft.quantity())
                .addValue("unitPrice", draft.unitPrice())
                .addValue("totalAmount", draft.totalAmount())
                .addValue("buyerName", draft.buyerName())
                .addValue("buyerPhone", draft.buyerPhone())
                .addValue("address", draft.address());
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql, parameters, keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("插入订单后未取得主键");
        }
        return key.longValue();
    }

    public Optional<Order> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM orders WHERE id = :id",
                        Map.of("id", id), ROW_MAPPER)
                .stream().findFirst();
    }

    public Optional<Order> findByOrderNo(String orderNo) {
        return jdbc.query("SELECT " + COLUMNS + " FROM orders WHERE order_no = :orderNo",
                        Map.of("orderNo", orderNo), ROW_MAPPER)
                .stream().findFirst();
    }

    /** 支付事务专用：锁定订单行，保证状态检查与支付更新不会被并发请求交叉执行。 */
    public Optional<Order> findByOrderNoForUpdate(String orderNo) {
        return jdbc.query("SELECT " + COLUMNS + " FROM orders WHERE order_no = :orderNo FOR UPDATE",
                        Map.of("orderNo", orderNo), ROW_MAPPER)
                .stream().findFirst();
    }

    /**
     * 仅当订单仍处于 created 时更新为 paid，返回受影响行数。
     * 即使调用方遗漏行锁，条件更新和 payments.order_no 唯一约束也能防止重复成功支付。
     */
    public int markPaid(String orderNo, OffsetDateTime paidAt) {
        return jdbc.update("""
                        UPDATE orders
                        SET status = 'paid', paid_at = :paidAt
                        WHERE order_no = :orderNo AND status = 'created'
                        """,
                new MapSqlParameterSource()
                        .addValue("orderNo", orderNo)
                        .addValue("paidAt", paidAt));
    }

    public long countByBuyerPhone(String buyerPhone) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE buyer_phone = :buyerPhone",
                Map.of("buyerPhone", buyerPhone), Long.class);
        return total == null ? 0 : total;
    }

    public List<Order> findPageByBuyerPhone(String buyerPhone, int limit, long offset) {
        return jdbc.query("SELECT " + COLUMNS + " FROM orders"
                        + " WHERE buyer_phone = :buyerPhone"
                        + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset",
                new MapSqlParameterSource()
                        .addValue("buyerPhone", buyerPhone)
                        .addValue("limit", limit)
                        .addValue("offset", offset),
                ROW_MAPPER);
    }
}
