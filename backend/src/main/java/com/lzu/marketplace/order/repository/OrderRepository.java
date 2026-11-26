package com.lzu.marketplace.order.repository;

import com.lzu.marketplace.common.PageResult;
import com.lzu.marketplace.order.model.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 订单数据访问：只负责 SQL 和行映射，所有查询条件都用参数绑定，不拼接用户输入。
 */
@Repository
public class OrderRepository {

    /** 订单号前缀：SO + 日期 + 序号。 */
    private static final String ORDER_NO_PREFIX = "SO";
    private static final DateTimeFormatter ORDER_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 序号至少补齐 8 位；超过 8 位按实际值完整输出，不截断。 */
    private static final int ORDER_NO_SEQ_WIDTH = 8;

    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String SELECT_SQL =
            "SELECT id, order_no, product_id, product_title, quantity, unit_price, total_amount, "
                    + "buyer_name, buyer_phone, address, status, created_at, paid_at FROM orders";

    private static final String INSERT_SQL =
            "INSERT INTO orders (order_no, product_id, product_title, quantity, unit_price, total_amount, "
                    + "buyer_name, buyer_phone, address) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final RowMapper<Order> ROW_MAPPER = (rs, rowNum) -> new Order(
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
            rs.getString("status"),
            toLocalDateTime(rs.getTimestamp("created_at")),
            toLocalDateTime(rs.getTimestamp("paid_at"))
    );

    /** 数据库里的 paid_at 允许为 NULL，这里统一转成可为 null 的 LocalDateTime。 */
    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return (timestamp == null) ? null : timestamp.toLocalDateTime();
    }

    /**
     * 取下一个订单号：SO + yyyyMMdd + 至少 8 位零填充序号。
     *
     * <p>序号来自数据库序列 order_no_seq，天然唯一且并发安全；
     * 事务回滚会消耗掉序号，允许出现跳号（不影响唯一性）。</p>
     *
     * <p>长度：2（SO）+ 8（日期）+ 序号位数。序列是 BIGINT，最多 19 位，
     * 最长 29 个字符，不会超过 order_no VARCHAR(32)。</p>
     */
    public String nextOrderNo() {
        Long seq = jdbcTemplate.queryForObject("SELECT NEXT VALUE FOR order_no_seq", Long.class);
        long value = (seq == null) ? 0L : seq;
        return ORDER_NO_PREFIX
                + LocalDate.now().format(ORDER_NO_DATE)
                + String.format("%0" + ORDER_NO_SEQ_WIDTH + "d", value);
    }

    /** 插入并回读完整行（拿到自增 id、默认 status 和 created_at）。 */
    public Order insert(String orderNo, long productId, String productTitle, int quantity,
                        long unitPrice, long totalAmount, String buyerName, String buyerPhone, String address) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            // 只返回 ID 一列，避免 H2 把带 DEFAULT 的 created_at 也当成生成列，导致 getKey() 抛异常
            PreparedStatement ps = connection.prepareStatement(INSERT_SQL, new String[]{"ID"});
            ps.setString(1, orderNo);
            ps.setLong(2, productId);
            ps.setString(3, productTitle);
            ps.setInt(4, quantity);
            ps.setLong(5, unitPrice);
            ps.setLong(6, totalAmount);
            ps.setString(7, buyerName);
            ps.setString(8, buyerPhone);
            ps.setString(9, address);
            return ps;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findById(id);
    }

    public Order findById(long id) {
        List<Order> list = jdbcTemplate.query(SELECT_SQL + " WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 按订单号精确查询，不存在返回 null。 */
    public Order findByOrderNo(String orderNo) {
        List<Order> list = jdbcTemplate.query(SELECT_SQL + " WHERE order_no = ?", ROW_MAPPER, orderNo);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 按手机号分页查询：按创建时间倒序，id 倒序作为并列时的稳定排序，
     * 保证翻页时不会因为同一条记录位置漂移而重复或漏掉。
     */
    public PageResult<Order> findPageByPhone(String buyerPhone, int page, int pageSize, long offset) {
        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE buyer_phone = ?", Long.class, buyerPhone);

        List<Order> items = jdbcTemplate.query(
                SELECT_SQL + " WHERE buyer_phone = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                ROW_MAPPER,
                buyerPhone, pageSize, offset);

        return new PageResult<>(total, page, pageSize, items);
    }
}
