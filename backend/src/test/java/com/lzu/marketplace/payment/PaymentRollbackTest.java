package com.lzu.marketplace.payment;

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
 * 支付事务回滚验收测试。
 *
 * <p><b>故障点怎么造</b>：把 {@link PaymentIdGenerator} 换成测试替身，固定返回一个
 * 已存在的 paymentId，使 {@code INSERT INTO payments} 撞上 {@code uk_payments_payment_id}
 * 唯一约束。这条 INSERT 在支付链路中是<b>先执行</b>的一步，之后才更新订单状态——
 * 所以故障发生后，订单状态更新还没发生，正好验证「支付记录插入失败时订单状态不改」。
 *
 * <p><b>证据</b>：替身在 {@code next()} 里用同一连接读一次订单状态（此时支付记录
 * 的 INSERT 还没提交，事务内可见），记录到的状态应是 {@code created}；请求结束后
 * 再从外部读订单状态，仍是 {@code created}，且没有新增支付记录。
 *
 * <p>与 OrderRollbackTest 不同：那里验证「扣库存回滚」，这里验证「支付记录与订单
 * 状态必须同生共死」——一旦支付记录插不进去，订单不能先被标成 paid。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PaymentRollbackTest.FixedPaymentIdConfig.class)
class PaymentRollbackTest {

    private static final String EXISTING_PAYMENT_ID = "PAY20260101000000000000000000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StubPaymentIdGenerator generator;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");

        jdbc.update("INSERT INTO products (title, price, stock, category, status, seller_name) " +
                    "VALUES ('回滚支付商品', 1000, 5, '图书', 'on', '校园数码铺')");
        long productId = jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);

        // 待支付订单（被测对象）
        jdbc.update("INSERT INTO orders (order_no, product_id, product_title, quantity, unit_price, " +
                    "total_amount, buyer_name, buyer_phone, address, status) " +
                    "VALUES ('SO-ROLLBACK-PAY', ?, '回滚支付商品', 1, 1000, 1000, '张三', " +
                    "'13800138000', '兰州市城关区天水南路222号', 'created')", productId);

        // 占位订单 + 占位支付记录：占用 paymentId，使后续 INSERT 撞唯一约束。
        // 占位支付记录必须引用一个真实订单（payments 有外键指向 orders.order_no）。
        jdbc.update("INSERT INTO orders (order_no, product_id, product_title, quantity, unit_price, " +
                    "total_amount, buyer_name, buyer_phone, address, status) " +
                    "VALUES ('SO-DUMMY-ORDER', ?, '占位订单', 1, 1000, 1000, '占位', " +
                    "'13800000000', '占位地址', 'created')", productId);
        jdbc.update("INSERT INTO payments (payment_id, order_no, amount, method, status, paid_at) " +
                    "VALUES (?, 'SO-DUMMY-ORDER', 1000, 'alipay', 'paid', CURRENT_TIMESTAMP)",
                EXISTING_PAYMENT_ID);

        generator.reset(EXISTING_PAYMENT_ID);
    }

    @Test
    @DisplayName("支付记录插入失败 → 订单状态不被更新，事务回滚")
    void paymentInsertFailureRollsBackOrderUpdate() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"SO-ROLLBACK-PAY\",\"method\":\"alipay\"}"))
                .andExpect(status().is5xxServerError());

        // 证据 1：故障发生时，事务内看到的订单状态仍是 created
        assertTrue(generator.wasInvoked(), "paymentId 生成应在支付记录插入时被调用");
        assertEquals("created", generator.orderStatusSeenInsideTransaction(),
                "插入支付记录时，订单应仍是 created（状态更新在其之后）");

        // 证据 2：请求结束后，订单状态仍为 created，未被标记 paid
        assertEquals("created", jdbc.queryForObject(
                "SELECT status FROM orders WHERE order_no = 'SO-ROLLBACK-PAY'", String.class),
                "支付记录插入失败时，订单状态必须回滚，不能残留 paid");

        // 证据 3：没有为该订单新增支付记录
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_no = 'SO-ROLLBACK-PAY'", Integer.class));
    }

    // ================= 测试替身 =================

    @TestConfiguration
    static class FixedPaymentIdConfig {

        @org.springframework.context.annotation.Bean
        @Primary
        StubPaymentIdGenerator stubPaymentIdGenerator(JdbcTemplate jdbc) {
            return new StubPaymentIdGenerator(jdbc);
        }
    }

    static class StubPaymentIdGenerator extends PaymentIdGenerator {

        private final JdbcTemplate jdbc;
        private final AtomicReference<String> fixedId = new AtomicReference<>();
        private final AtomicBoolean invoked = new AtomicBoolean(false);
        private final AtomicReference<String> orderStatusInsideTx = new AtomicReference<>();

        StubPaymentIdGenerator(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        void reset(String id) {
            fixedId.set(id);
            invoked.set(false);
            orderStatusInsideTx.set(null);
        }

        boolean wasInvoked() {
            return invoked.get();
        }

        String orderStatusSeenInsideTransaction() {
            return orderStatusInsideTx.get();
        }

        @Override
        public String next() {
            invoked.set(true);
            orderStatusInsideTx.set(jdbc.queryForObject(
                    "SELECT status FROM orders WHERE order_no = 'SO-ROLLBACK-PAY'", String.class));
            String id = fixedId.get();
            return id != null ? id : super.next();
        }
    }
}
