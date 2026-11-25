package cn.edu.lzu.marketplace.integration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 真实数据库集成测试共用的数据准备 / 断言工具。
 *
 * <p>与正式代码共用同一份 {@code schema.sql}（列名、类型、约束完全一致），
 * 通过 JdbcTemplate 直连 H2 做数据准备与结果核对，不引入额外测试表结构。
 */
final class TestDb {

    private TestDb() {
    }

    /** 先删订单再删商品以满足外键约束，自增序列沿用当前数据库的值。 */
    static void reset(JdbcTemplate jt) {
        jt.update("DELETE FROM orders");
        jt.update("DELETE FROM products");
    }

    /** 直插一条商品，返回自增主键，用于精确控制初始库存/价格/状态。 */
    static long insertProduct(JdbcTemplate jt, String title, long price, int stock, String status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jt.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO products (title, price, stock, category, description, status, seller_name, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, title);
            ps.setLong(2, price);
            ps.setInt(3, stock);
            ps.setString(4, "图书");
            ps.setString(5, "测试备注");
            ps.setString(6, status);
            ps.setString(7, "校园数码铺");
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    // 数据库断言辅助：直接读取库存和订单数，独立于 HTTP 响应。
    /** 读取商品当前库存。 */
    static int stockOf(JdbcTemplate jt, long productId) {
        Integer stock = jt.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
        return stock == null ? -1 : stock;
    }

    /** 统计某商品对应的订单条数。 */
    static long countOrdersFor(JdbcTemplate jt, long productId) {
        Long count = jt.queryForObject("SELECT COUNT(*) FROM orders WHERE product_id = ?", Long.class, productId);
        return count == null ? 0L : count;
    }
}
