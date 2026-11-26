package com.lzu.marketplace.order;

import com.lzu.marketplace.product.Product;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {
    private static final RowMapper<Order> MAPPER = (rs, row) -> new Order(
            rs.getLong("id"), rs.getString("order_no"), rs.getLong("product_id"), rs.getString("product_title"),
            rs.getInt("quantity"), rs.getLong("unit_price"), rs.getLong("total_amount"), rs.getString("buyer_name"),
            rs.getString("buyer_phone"), rs.getString("address"), rs.getString("status"),
            rs.getObject("created_at", LocalDateTime.class), rs.getObject("paid_at", LocalDateTime.class));
    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert insert;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        insert = new SimpleJdbcInsert(jdbc).withTableName("orders").usingGeneratedKeyColumns("id")
                .usingColumns("order_no", "product_id", "product_title", "quantity", "unit_price", "total_amount",
                        "buyer_name", "buyer_phone", "address", "status");
    }

    public Order insert(String number, Product product, CreateOrderRequest request, long total) {
        insert.executeAndReturnKey(new MapSqlParameterSource()
                .addValue("order_no", number).addValue("product_id", product.id())
                .addValue("product_title", product.title()).addValue("quantity", request.quantity())
                .addValue("unit_price", product.price()).addValue("total_amount", total)
                .addValue("buyer_name", request.buyerName()).addValue("buyer_phone", request.buyerPhone())
                .addValue("address", request.address()).addValue("status", "created"));
        return find(number, false).orElseThrow();
    }

    public Optional<Order> find(String number, boolean lock) {
        return jdbc.query("SELECT * FROM orders WHERE order_no = ?" + (lock ? " FOR UPDATE" : ""),
                MAPPER, number).stream().findFirst();
    }

    public OrderPage list(OrderQuery query) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE buyer_phone = ?", Long.class, query.buyerPhone());
        long offset = ((long) query.page() - 1) * query.pageSize();
        var items = jdbc.query("SELECT * FROM orders WHERE buyer_phone = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                MAPPER, query.buyerPhone(), query.pageSize(), offset);
        return new OrderPage(total, query.page(), query.pageSize(), items);
    }

    public void markPaid(String number, LocalDateTime time) {
        jdbc.update("UPDATE orders SET status = 'paid', paid_at = ? WHERE order_no = ?", time, number);
    }
}
