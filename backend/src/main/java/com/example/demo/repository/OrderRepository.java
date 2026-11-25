package com.example.demo.repository;

import com.example.demo.common.PageResult;
import com.example.demo.model.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 统一的订单行映射。
     */
    private static final RowMapper<Order> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        Timestamp paidAt = rs.getTimestamp("paid_at");

        return new Order(
                rs.getLong("id"),
                rs.getString("order_no"),
                rs.getLong("product_id"),
                rs.getString("product_title"),
                rs.getInt("quantity"),
                rs.getLong("unit_price"),
                rs.getLong("total_amount"),
                rs.getString("buyer_name"),
                rs.getString("buyer_phone"),
                rs.getString("address"),
                rs.getString("remark"),
                rs.getString("status"),
                createdAt == null ? null : createdAt.toLocalDateTime(),
                updatedAt == null ? null : updatedAt.toLocalDateTime(),
                paidAt == null ? null : paidAt.toLocalDateTime()
        );
    };

    /**
     * 插入订单，返回数据库生成的订单 ID。
     *
     * 状态固定为 created，created_at 和 updated_at 使用数据库默认值。
     */
    public long insert(Order order) {
        String sql = """
                INSERT INTO orders (
                    order_no,
                    product_id,
                    product_title,
                    quantity,
                    unit_price,
                    total_amount,
                    buyer_name,
                    buyer_phone,
                    address,
                    remark,
                    status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'created')
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement =
                    connection.prepareStatement(
                            sql,
                            new String[]{"id"}
                    );

            statement.setString(1, order.orderNo());
            statement.setLong(2, order.productId());
            statement.setString(3, order.productTitle());
            statement.setInt(4, order.quantity());
            statement.setLong(5, order.unitPrice());
            statement.setLong(6, order.totalAmount());
            statement.setString(7, order.buyerName());
            statement.setString(8, order.buyerPhone());
            statement.setString(9, order.address());
            statement.setString(
                    10,
                    order.remark() == null ? "" : order.remark()
            );

            return statement;
        }, keyHolder);

        Number id = keyHolder.getKey();

        if (id == null) {
            throw new IllegalStateException("新增订单后未取得订单 ID");
        }

        return id.longValue();
    }

    /**
     * 按订单号查询订单。
     */
    public Optional<Order> findByOrderNo(String orderNo) {
        String sql = """
                SELECT id, order_no, product_id, product_title, quantity,
                       unit_price, total_amount, buyer_name, buyer_phone,
                       address, remark, status, created_at, updated_at, paid_at
                FROM orders
                WHERE order_no = ?
                """;

        return jdbcTemplate.query(
                        sql,
                        ROW_MAPPER,
                        orderNo
                )
                .stream()
                .findFirst();
    }

    /**
     * 按订单 ID 查询订单。
     */
    public Optional<Order> findById(long id) {
        String sql = """
                SELECT id, order_no, product_id, product_title, quantity,
                       unit_price, total_amount, buyer_name, buyer_phone,
                       address, remark, status, created_at, updated_at, paid_at
                FROM orders
                WHERE id = ?
                """;

        return jdbcTemplate.query(
                        sql,
                        ROW_MAPPER,
                        id
                )
                .stream()
                .findFirst();
    }

    /**
     * 按买家手机号分页查询订单，按创建时间倒序。
     *
     * total 和 items 使用完全相同的筛选条件。
     */
    public PageResult<Order> findPageByBuyerPhone(
            String buyerPhone,
            int page,
            int pageSize
    ) {
        String where = " WHERE buyer_phone = ? ";

        String countSql =
                "SELECT COUNT(*) FROM orders" + where;

        Long total = jdbcTemplate.queryForObject(
                countSql,
                Long.class,
                buyerPhone
        );

        long offset = ((long) page - 1) * pageSize;

        String listSql = """
                SELECT id, order_no, product_id, product_title, quantity,
                       unit_price, total_amount, buyer_name, buyer_phone,
                       address, remark, status, created_at, updated_at, paid_at
                FROM orders
                """ + where + """
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """;

        List<Order> items = jdbcTemplate.query(
                listSql,
                ROW_MAPPER,
                buyerPhone,
                pageSize,
                offset
        );

        return new PageResult<>(
                total == null ? 0L : total,
                page,
                pageSize,
                items
        );
    }
}
