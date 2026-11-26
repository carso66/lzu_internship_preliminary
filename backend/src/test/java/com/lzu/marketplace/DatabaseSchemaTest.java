package com.lzu.marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 使用单独的测试数据库；直接执行 SQL，验证表结构和约束，不依赖尚未实现的下单接口。
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:marketplace-schema-test;DB_CLOSE_DELAY=-1"
})
// 每个测试结束后回滚数据，避免不同用例相互影响。
@Transactional
class DatabaseSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void applicationStartupCreatesBothTables() {
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM products", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class));
    }

    @Test
    void orderKeepsSnapshotAndLargeIntegerAmounts() {
        // 故意使用超过 int 上限的金额，检查 BIGINT 能否完整保存整数分。
        long productId = insertProduct(3_000_000_000L, 2, "on");
        insertOrder("SCHEMA-SNAPSHOT", productId, 2);

        // 修改商品后，订单内已保存的标题和成交单价应保持原值。
        jdbc.update("UPDATE products SET title = ?, price = ? WHERE id = ?",
                "Updated title", 100L, productId);

        var order = jdbc.queryForMap("SELECT * FROM orders WHERE order_no = ?", "SCHEMA-SNAPSHOT");
        assertEquals("Schema test product", order.get("PRODUCT_TITLE"));
        assertEquals(3_000_000_000L, ((Number) order.get("UNIT_PRICE")).longValue());
        assertEquals(6_000_000_000L, ((Number) order.get("TOTAL_AMOUNT")).longValue());
        assertEquals("created", order.get("STATUS"));
        assertNotNull(order.get("CREATED_AT"));
    }

    @Test
    void databaseRejectsInvalidProductValues() {
        assertThrows(DataIntegrityViolationException.class, () -> insertProduct(1990L, -1, "on"));
        assertThrows(DataIntegrityViolationException.class, () -> insertProduct(0L, 1, "on"));
        assertThrows(DataIntegrityViolationException.class, () -> insertProduct(1990L, 1, "invalid"));
    }

    @Test
    void databaseRejectsInvalidQuantityAndDuplicateOrderNumber() {
        long productId = insertProduct(3_000_000_000L, 2, "on");
        assertThrows(DataIntegrityViolationException.class, () -> insertOrder("SCHEMA-ZERO", productId, 0));

        insertOrder("SCHEMA-UNIQUE", productId, 1);
        assertThrows(DataIntegrityViolationException.class, () -> insertOrder("SCHEMA-UNIQUE", productId, 1));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class));
    }

    private long insertProduct(long price, int stock, String status) {
        // H2 的 FINAL TABLE 可以在插入后直接取得数据库生成的主键。
        return jdbc.queryForObject("""
                SELECT id FROM FINAL TABLE (
                    INSERT INTO products (title, price, stock, category, seller_name, status)
                    VALUES (?, ?, ?, ?, ?, ?)
                )
                """, Long.class, "Schema test product", price, stock, "Test", "Test seller", status);
    }

    private void insertOrder(String orderNo, long productId, int quantity) {
        jdbc.update("""
                INSERT INTO orders (order_no, product_id, product_title, quantity,
                    unit_price, total_amount, buyer_name, buyer_phone, address)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, orderNo, productId, "Schema test product", quantity,
                3_000_000_000L, 3_000_000_000L * quantity, "Test buyer", "00000000000", "Test address");
    }
}
