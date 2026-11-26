package com.lzu.marketplace.order;

import com.lzu.marketplace.common.PageResult;
import com.lzu.marketplace.order.dto.OrderQuery;
import com.lzu.marketplace.product.Product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 下单与订单查询的持久层。对 products 只做下单需要的事：加锁读取、条件扣减。
 */
@Repository
public class OrderRepository {

    private static final String ORDER_COLUMNS =
            "id, order_no, product_id, product_title, quantity, unit_price, total_amount, " +
            "buyer_name, buyer_phone, address, status, created_at, paid_at";

    private static final RowMapper<Order> ORDER_ROW_MAPPER = (rs, rowNum) -> new Order(
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
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("paid_at", LocalDateTime.class));

    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 在同一事务内对商品行加排他锁读取（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>锁持有到事务提交/回滚。之后的校验、扣减、订单插入都发生在锁内，
     * 并发下单会在此处排队，杜绝「两个请求都读到 stock=1 然后都扣成 0」。
     *
     * <p>用 {@code query() + findFirst()} 而不是 {@code queryForObject}：
     * 后者在 0 行时抛 EmptyResultDataAccessException，让「商品不存在」变成异常路径。
     *
     * @return 商品不存在时为 empty
     */
    public Optional<Product> findByIdForUpdate(long productId) {
        return jdbc.query(
                        "SELECT id, title, price, stock, category, description, status, " +
                        "seller_name, created_at FROM products WHERE id = ? FOR UPDATE",
                        (rs, rowNum) -> new Product(
                                rs.getLong("id"),
                                rs.getString("title"),
                                rs.getLong("price"),
                                rs.getInt("stock"),
                                rs.getString("category"),
                                rs.getString("description"),
                                rs.getString("status"),
                                rs.getString("seller_name"),
                                rs.getObject("created_at", LocalDateTime.class)),
                        productId)
                .stream()
                .findFirst();
    }

    /**
     * 扣减库存，返回受影响行数。
     *
     * <p>WHERE 带上 {@code stock >= ?} 作为第二道防线：锁内读到的库存与扣减之间
     * 理论上不会有其他写入，但条件扣减让这条 UPDATE 本身也不会把库存扣成负数——
     * 调用方必须检查返回值（题目 L1-6 第 8 条）。
     *
     * @return 0 表示库存不足，未扣减
     */
    public int deductStock(long productId, int quantity) {
        return jdbc.update(
                "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?",
                quantity, productId, quantity);
    }

    /**
     * 插入订单并回填 id / created_at。
     */
    public Order insert(String orderNo, long productId, String productTitle, int quantity,
                        long unitPrice, long totalAmount, String buyerName, String buyerPhone,
                        String address) {
        String sql = "INSERT INTO orders (order_no, product_id, product_title, quantity, unit_price, " +
                     "total_amount, buyer_name, buyer_phone, address) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
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

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("插入订单后未取得自增主键");
        }
        return findById(key.longValue())
                .orElseThrow(() -> new IllegalStateException("插入订单后无法回读记录 id=" + key));
    }

    public Optional<Order> findById(long id) {
        return jdbc.query(
                        "SELECT " + ORDER_COLUMNS + " FROM orders WHERE id = ?",
                        ORDER_ROW_MAPPER, id)
                .stream()
                .findFirst();
    }

    /**
     * 按订单号查详情（官方 §5 L1-7）。
     *
     * <p>order_no 在库里有唯一约束（{@code uk_orders_order_no}），命中行至多一条。
     */
    public Optional<Order> findByOrderNo(String orderNo) {
        return jdbc.query(
                        "SELECT " + ORDER_COLUMNS + " FROM orders WHERE order_no = ?",
                        ORDER_ROW_MAPPER, orderNo)
                .stream()
                .findFirst();
    }

    /**
     * 在同一事务内对订单行加排他锁读取（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>供支付使用：两个并发支付同一订单时，后到者在锁上排队，
     * 先到者提交后、后到者读到 {@code status='paid'} 返回 40903，
     * 从根上杜绝「同一订单两条支付记录」。锁持有到事务结束。
     *
     * @return 订单不存在时为 empty
     */
    public Optional<Order> findByOrderNoForUpdate(String orderNo) {
        return jdbc.query(
                        "SELECT " + ORDER_COLUMNS + " FROM orders WHERE order_no = ? FOR UPDATE",
                        ORDER_ROW_MAPPER, orderNo)
                .stream()
                .findFirst();
    }

    /**
     * 把订单标记为已支付，同时写入支付时间。返回受影响行数。
     *
     * <p>WHERE 带 {@code status = 'created'} 作为第二道防线：锁内已确认过状态，
     * 但条件更新保证「paid → 再更新」不会发生。返回 0 行表示状态已不是 created，
     * 调用方按「订单已支付」处理。
     *
     * <p>必须同时写 {@code paid_at}：orders 表有 CHECK 约束
     * {@code (status='paid') = (paid_at IS NOT NULL)}，只改 status 会违反约束。
     */
    public int markPaid(String orderNo, LocalDateTime paidAt) {
        return jdbc.update(
                "UPDATE orders SET status = 'paid', paid_at = ? " +
                "WHERE order_no = ? AND status = 'created'",
                paidAt, orderNo);
    }

    /**
     * 按买家手机号分页查询订单，创建时间倒序（官方 §5 L1-8）。
     *
     * <p><b>排序稳定性</b>：{@code ORDER BY created_at DESC, id DESC}。
     * created_at 是 TIMESTAMP，同一毫秒内批量插入的订单会并列；
     * 只按 created_at 排序时，并列行的相对次序由数据库决定且可能随执行计划变化，
     * 翻页就会重复或漏掉记录。追加 id DESC 给出总序中的确定性位置。
     * 这与商品列表采用的是同一条约定。
     *
     * <p><b>过滤范围</b>：{@code WHERE buyer_phone = ?} 精确匹配，
     * 手机号经参数绑定而非字符串拼接；只有该买家的订单会被 COUNT 和分页查询看到，
     * 不存在「先取全表再在内存里过滤」导致越界返回的路径。
     *
     * <p>COUNT 与数据查询共用同一个 WHERE 子句和参数，保证 total 与 items 一致。
     */
    public PageResult<Order> searchByBuyerPhone(OrderQuery q) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE buyer_phone = ?",
                Long.class,
                q.buyerPhone());

        List<Order> items = jdbc.query(
                "SELECT " + ORDER_COLUMNS + " FROM orders WHERE buyer_phone = ?"
                        + " ORDER BY created_at DESC, id DESC"
                        + " LIMIT ? OFFSET ?",
                ORDER_ROW_MAPPER,
                q.buyerPhone(), q.pageSize(), q.offset());

        return PageResult.of(total == null ? 0L : total, q.page(), q.pageSize(), items);
    }
}
