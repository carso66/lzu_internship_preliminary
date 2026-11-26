package com.lzu.marketplace.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据库结构验收测试：验证三张表成功创建、约束按设计生效。
 *
 * <p>每个测试方法在事务中执行并自动回滚，方法之间互不污染。
 * 并发相关测试不在本类（事务回滚对多线程无效），见后续 L2-10 测试。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaConstraintTest {

    @Autowired
    private JdbcTemplate jdbc;

    // ---------- 建表 ----------

    @Test
    @DisplayName("三张表均成功创建")
    void allTablesCreated() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'PUBLIC' AND table_name IN ('PRODUCTS','ORDERS','PAYMENTS')",
                String.class);
        assertEquals(3, tables.size(), "应创建 products / orders / payments 三张表，实际: " + tables);
    }

    // ---------- products ----------

    @Test
    @DisplayName("合法商品可以插入")
    void validProductInserted() {
        long id = insertProduct("测试商品", 1990, 10, "文具", "on");
        Integer stock = jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, id);
        assertEquals(10, stock);
    }

    @Test
    @DisplayName("负库存被拒绝")
    void negativeStockRejected() {
        assertThrows(DataAccessException.class,
                () -> insertProduct("负库存商品", 1990, -1, "文具", "on"));
    }

    @Test
    @DisplayName("非法商品状态被拒绝")
    void invalidProductStatusRejected() {
        assertThrows(DataAccessException.class,
                () -> insertProduct("非法状态商品", 1990, 10, "文具", "unknown"));
    }

    @Test
    @DisplayName("非正价格被拒绝")
    void nonPositivePriceRejected() {
        assertThrows(DataAccessException.class,
                () -> insertProduct("零价格商品", 0, 10, "文具", "on"));
    }

    // ---------- orders ----------

    @Test
    @DisplayName("不存在的商品不能创建关联订单")
    void orderWithUnknownProductRejected() {
        assertThrows(DataAccessException.class, () -> insertOrder(
                "SO20261124000000000000000000000001", 999_999L, 1, 1000L, 1000L));
    }

    @Test
    @DisplayName("重复订单号被拒绝")
    void duplicateOrderNoRejected() {
        long productId = insertProduct("商品A", 1000, 5, "文具", "on");
        String orderNo = "SO20261124000000000000000000000002";
        insertOrder(orderNo, productId, 1, 1000L, 1000L);

        assertThrows(DuplicateKeyException.class,
                () -> insertOrder(orderNo, productId, 1, 1000L, 1000L));
    }

    @Test
    @DisplayName("非法订单金额被拒绝（total_amount 与 unit_price*quantity 不符）")
    void inconsistentOrderAmountRejected() {
        long productId = insertProduct("商品B", 1000, 5, "文具", "on");
        assertThrows(DataAccessException.class, () -> insertOrder(
                "SO20261124000000000000000000000003", productId, 2, 1000L, 9999L));
    }

    @Test
    @DisplayName("非法订单金额被拒绝（unit_price 非正）")
    void nonPositiveUnitPriceRejected() {
        long productId = insertProduct("商品C", 1000, 5, "文具", "on");
        assertThrows(DataAccessException.class, () -> insertOrder(
                "SO20261124000000000000000000000004", productId, 1, 0L, 0L));
    }

    // ---------- payments ----------

    @Test
    @DisplayName("同一订单不能插入两条支付记录")
    void duplicatePaymentForSameOrderRejected() {
        long productId = insertProduct("商品D", 1000, 5, "文具", "on");
        String orderNo = "SO20261124000000000000000000000005";
        insertOrder(orderNo, productId, 1, 1000L, 1000L);

        insertPayment("PAY202611240000000000000000000001", orderNo, 1000L, "alipay");

        assertThrows(DuplicateKeyException.class,
                () -> insertPayment("PAY202611240000000000000000000002", orderNo, 1000L, "wechat"));
    }

    @Test
    @DisplayName("非法支付方式被拒绝")
    void invalidPaymentMethodRejected() {
        long productId = insertProduct("商品E", 1000, 5, "文具", "on");
        String orderNo = "SO20261124000000000000000000000006";
        insertOrder(orderNo, productId, 1, 1000L, 1000L);

        assertThrows(DataAccessException.class,
                () -> insertPayment("PAY202611240000000000000000000003", orderNo, 1000L, "cash"));
    }

    // ---------- 索引 ----------

    @Test
    @DisplayName("orders(buyer_phone, created_at) 索引已创建")
    void buyerPhoneIndexExists() {
        List<String> indexes = jdbc.queryForList(
                "SELECT index_name FROM information_schema.indexes " +
                "WHERE table_name = 'ORDERS'",
                String.class);
        assertTrue(indexes.stream().anyMatch(n -> n.equalsIgnoreCase("idx_orders_buyer_phone_created")),
                "应存在 idx_orders_buyer_phone_created，实际: " + indexes);
    }

    // ---------- helpers ----------

    private long insertProduct(String title, long price, int stock, String category, String status) {
        jdbc.update("INSERT INTO products (title, price, stock, category, description, status, seller_name) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                title, price, stock, category, "desc", status, "校园数码铺");
        return jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);
    }

    private void insertOrder(String orderNo, long productId, int quantity, long unitPrice, long totalAmount) {
        jdbc.update("INSERT INTO orders (order_no, product_id, product_title, quantity, unit_price, " +
                    "total_amount, buyer_name, buyer_phone, address) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                orderNo, productId, "快照标题", quantity, unitPrice, totalAmount,
                "张三", "13800138000", "兰州市城关区天水南路222号");
    }

    private void insertPayment(String paymentId, String orderNo, long amount, String method) {
        jdbc.update("INSERT INTO payments (payment_id, order_no, amount, method, status, paid_at) " +
                    "VALUES (?, ?, ?, ?, 'paid', CURRENT_TIMESTAMP)",
                paymentId, orderNo, amount, method);
    }
}
