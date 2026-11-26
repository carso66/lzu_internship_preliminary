package com.lzu.marketplace.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 事务回滚验收测试（题目 L1-6：插入订单失败时，已扣的库存必须回滚）。
 *
 * <p><b>故障点怎么造</b>：把 {@link OrderNoGenerator} 换成测试替身，固定返回一个
 * 已存在的订单号，使 {@code INSERT INTO orders} 撞上 {@code uk_orders_order_no}
 * 唯一约束。这个 INSERT 发生在库存扣减<b>之后</b>，正好命中要验证的窗口。
 *
 * <p><b>怎么证明扣减真的发生在失败之前</b>（而不是根本没执行）：
 * 替身在 {@code next()} 被调用时（此时扣减已执行、事务尚未提交），
 * 用同一个连接读一次商品库存。
 * <ul>
 *   <li>如果读到 {@code 4}，说明扣减已生效且对当前事务可见——故障点确实在扣减之后；</li>
 *   <li>请求结束后再从外部事务读，读到 {@code 5}，说明这次扣减被回滚了。</li>
 * </ul>
 * 一进一出两个读数合起来才构成「先扣后回滚」的完整证据。
 *
 * <p>本类不使用 {@code @Transactional}：测试级事务会让被测事务加入同一个事务，
 * 永远看不到真实的提交/回滚边界。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OrderRollbackTest.FixedOrderNoConfig.class)
class OrderRollbackTest {

    private static final String EXISTING_ORDER_NO = "SO20260101000000000000000000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StubOrderNoGenerator generator;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");

        // 库存 5 的商品
        jdbc.update("INSERT INTO products (title, price, stock, category, status, seller_name) " +
                    "VALUES ('回滚测试商品', 1000, 5, '图书', 'on', '校园数码铺')");
        long productId = jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);

        // 预先占用订单号，使后续 INSERT 必然撞唯一约束
        jdbc.update("INSERT INTO orders (order_no, product_id, product_title, quantity, unit_price, " +
                    "total_amount, buyer_name, buyer_phone, address) " +
                    "VALUES (?, ?, '占位订单', 1, 1000, 1000, '占位', '13800000000', '占位地址')",
                EXISTING_ORDER_NO, productId);

        generator.reset(EXISTING_ORDER_NO);
    }

    @Test
    @DisplayName("插订单失败 → 库存扣减回滚：扣减时可见 4，请求结束后恢复 5")
    void stockDeductedThenRolledBack() throws Exception {
        long productId = jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);

        assertEquals(5, stockOf(productId), "前置条件：库存 5");

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":1," +
                                 "\"buyerName\":\"张三\",\"buyerPhone\":\"13800138000\"," +
                                 "\"address\":\"兰州市城关区天水南路222号\"}"))
                .andExpect(status().is5xxServerError());

        // 证据 1：故障发生前库存确实已被扣到 4（替身在事务内读到的值）
        assertTrue(generator.wasInvoked(), "订单号生成在扣库存之后调用，未被调用说明扣减没执行");
        assertEquals(4, generator.stockSeenInsideTransaction().intValue(),
                "插入订单时，当前事务内应已看到扣减后的库存 4");

        // 证据 2：整个请求结束后，库存从数据库真实状态看已恢复为 5
        assertEquals(5, stockOf(productId), "插入订单失败后库存必须回滚到 5");

        // 证据 3：没有残留订单，只有预先插入的那条
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
    }

    @Test
    @DisplayName("回滚后可正常下单：证明回滚没有破坏表或连接")
    void canOrderAgainAfterRollback() throws Exception {
        long productId = jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":1," +
                                 "\"buyerName\":\"张三\",\"buyerPhone\":\"13800138000\"," +
                                 "\"address\":\"兰州市城关区天水南路222号\"}"))
                .andExpect(status().is5xxServerError());

        generator.reset(null); // 恢复正常单号生成

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":2," +
                                 "\"buyerName\":\"李四\",\"buyerPhone\":\"13900139000\"," +
                                 "\"address\":\"兰州市城关区天水南路222号\"}"))
                .andExpect(status().isCreated());

        assertEquals(3, stockOf(productId), "5 - 2 = 3");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class),
                "占位订单 + 新订单");
    }

    private int stockOf(long productId) {
        return jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
    }

    // ================= 测试替身 =================

    @TestConfiguration
    static class FixedOrderNoConfig {

        @org.springframework.context.annotation.Bean
        @Primary
        StubOrderNoGenerator stubOrderNoGenerator(JdbcTemplate jdbc) {
            return new StubOrderNoGenerator(jdbc);
        }
    }

    /**
     * 固定订单号生成器。{@code fixedOrderNo == null} 时退化为真实生成逻辑。
     */
    static class StubOrderNoGenerator extends OrderNoGenerator {

        private final JdbcTemplate jdbc;
        private final AtomicReference<String> fixedOrderNo = new AtomicReference<>();
        private final AtomicBoolean invoked = new AtomicBoolean(false);
        private final AtomicReference<Integer> stockInsideTx = new AtomicReference<>();

        StubOrderNoGenerator(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        void reset(String orderNo) {
            fixedOrderNo.set(orderNo);
            invoked.set(false);
            stockInsideTx.set(null);
        }

        boolean wasInvoked() {
            return invoked.get();
        }

        Integer stockSeenInsideTransaction() {
            return stockInsideTx.get();
        }

        @Override
        public String next() {
            invoked.set(true);
            // 此刻扣库存已执行、事务未提交。JdbcTemplate 在事务中复用同一连接，
            // 因此这里读到的是本事务尚未提交的扣减结果。
            stockInsideTx.set(jdbc.queryForObject(
                    "SELECT stock FROM products ORDER BY id DESC LIMIT 1", Integer.class));
            String fixed = fixedOrderNo.get();
            return fixed != null ? fixed : super.next();
        }
    }
}
