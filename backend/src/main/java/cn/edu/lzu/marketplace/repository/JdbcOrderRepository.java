package cn.edu.lzu.marketplace.repository;

import cn.edu.lzu.marketplace.model.Order;
import cn.edu.lzu.marketplace.model.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * 订单的 JDBC 数据访问实现。使用 JdbcTemplate 写入订单快照，按订单号或买家手机号查询。
 *
 * <h3>数据库联调依据 —— 依赖的表结构与字段（与 schema.sql 对齐）</h3>
 * <pre>
 * 表名: orders
 * | 列名           | 类型          | 约束/说明                          |
 * |----------------|--------------|-----------------------------------|
 * | id             | BIGINT       | 主键, 自增                        |
 * | order_no       | VARCHAR(48)  | 订单号, 非空, 唯一(UNIQUE)          |
 * | product_id     | BIGINT       | 商品ID, 非空                      |
 * | product_title  | VARCHAR(100) | 商品标题快照, 非空                  |
 * | quantity       | INTEGER      | 购买数量, 非空, &gt;0               |
 * | unit_price     | BIGINT       | 下单时单价(分), 非空               |
 * | total_amount   | BIGINT       | 订单总额(分), 非空                 |
 * | buyer_name     | VARCHAR(50)  | 买家姓名, 非空                     |
 * | buyer_phone    | VARCHAR(20)  | 买家手机号, 非空                   |
 * | address        | VARCHAR(200) | 收货地址, 非空                     |
 * | status         | VARCHAR(10)  | created/paid, 非空                |
 * | created_at     | TIMESTAMP    | 创建时间, 由应用写入, 非空          |
 * | paid_at        | TIMESTAMP    | 支付预留时间, 可空                 |
 * </pre>
 *
 * <p>关键约束依赖：order_no 上的 UNIQUE 约束是订单号唯一性的最终兜底；
 * created_at 由 Service 层写入，排序默认 created_at 倒序 + id 倒序作为稳定排序的并列决断。
 *
 * <p>order_no 长度兼容性（建表时注意）：临时占位号为 "P" + 32 位十六进制 UUID = 33 字符；
 * 正式编号为 "SO" + 8 位日期 + 自增序号（{@code %06d} 超出不截断，上限约 19 位），即 16~29 字符。
 * 二者均 ≤ 48，故列宽取 VARCHAR(48)。建表时不得将 order_no 收窄到 VARCHAR(32)，
 * 否则 33 字符的临时占位号会被拒绝。
 *
 * <h3>分页参数顺序（易错点）</h3>
 * <p>SQL 写的是 {@code LIMIT ? OFFSET ?}，JDBC 占位符按出现顺序绑定：先每页条数(limit)
 * 后偏移量(offset)。findPage 中占位符顺序为 buyer_phone、limit、offset，与 SQL 中
 * {@code WHERE buyer_phone = ? ... LIMIT ? OFFSET ?} 的出现顺序一致。
 */
@Repository
public class JdbcOrderRepository implements OrderRepository {

    // 查询列与领域对象映射，供订单详情和列表使用；支付预留时间允许为空。
    private static final String SELECT_COLUMNS =
            "id, order_no, product_id, product_title, quantity, unit_price, total_amount, "
                    + "buyer_name, buyer_phone, address, status, created_at, paid_at";

    private static final RowMapper<Order> ROW_MAPPER = (rs, rowNum) -> {
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
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                paidAt == null ? null : paidAt.toLocalDateTime());
    };

    // 构造器接收 Spring 配置的 JdbcTemplate，订单写入使用当前事务的连接。
    private final JdbcTemplate jdbcTemplate;

    public JdbcOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 订单落库：绑定订单快照和临时编号，返回生成的主键供业务层生成正式编号。 */
    @Override
    public Long insert(Order order) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO orders (order_no, product_id, product_title, quantity, unit_price, "
                            + "total_amount, buyer_name, buyer_phone, address, status, created_at, paid_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, order.orderNo());
            ps.setLong(2, order.productId());
            ps.setString(3, order.productTitle());
            ps.setInt(4, order.quantity());
            ps.setLong(5, order.unitPrice());
            ps.setLong(6, order.totalAmount());
            ps.setString(7, order.buyerName());
            ps.setString(8, order.buyerPhone());
            ps.setString(9, order.address());
            ps.setString(10, order.status());
            ps.setTimestamp(11, Timestamp.valueOf(order.createdAt()));
            ps.setTimestamp(12, order.paidAt() == null ? null : Timestamp.valueOf(order.paidAt()));
            return ps;
        }, keyHolder);

        // 缺失主键时无法生成正式编号，上抛异常使下单事务失败。
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("插入订单后未返回自增主键");
        }
        return key.longValue();
    }

    /** 正式编号回填：按主键更新，影响行数由业务层校验。 */
    @Override
    public int updateOrderNo(Long id, String orderNo) {
        return jdbcTemplate.update("UPDATE orders SET order_no = ? WHERE id = ?", orderNo, id);
    }

    /** 订单详情：使用唯一订单号定位订单记录。 */
    @Override
    public Optional<Order> findByOrderNo(String orderNo) {
        List<Order> rows = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM orders WHERE order_no = ?", ROW_MAPPER, orderNo);
        return rows.stream().findFirst();
    }

    /** 买家订单分页：先按手机号统计总数，再按创建时间和主键倒序查询。 */
    @Override
    public PageResult<Order> findPage(long offset, int limit, String buyerPhone) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE buyer_phone = ?", Long.class, buyerPhone);
        long totalCount = total == null ? 0L : total;

        // 占位符顺序：buyer_phone, limit, offset —— 与 WHERE ? / LIMIT ? / OFFSET ? 出现顺序一致
        List<Order> items = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM orders WHERE buyer_phone = ? "
                        + "ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, buyerPhone, limit, offset);

        return new PageResult<>(totalCount, items);
    }
}
