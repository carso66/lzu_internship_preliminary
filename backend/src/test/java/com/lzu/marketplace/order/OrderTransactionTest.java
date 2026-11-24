package com.lzu.marketplace.order;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * 事务回滚测试不能加 @Transactional：
 * 否则测试自身开启的事务会包住 service 事务，异常后库存还停留在未回滚的同一事务里，
 * 测试看不到真实提交/回滚结果。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:order-rollback-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class OrderTransactionTest {

    @Autowired
    private OrderService service;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private OrderRepository orderRepository;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");
    }

    @Test
    void rollsBackStockWhenOrderInsertFails() {
        jdbc.update("""
                        INSERT INTO products (title, price, stock, category, seller_name, status)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                "回滚测试商品", 1000, 5, "文具", "测试店铺", "on");
        Long productId = jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);
        assertThat(productId).isNotNull();

        doThrow(new DataIntegrityViolationException("forced order insert failure"))
                .when(orderRepository).insert(any(OrderDraft.class));

        CreateOrderRequest request = new CreateOrderRequest(
                productId, 2, "张三", "13800138000", "兰州市城关区天水南路222号");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject(
                "SELECT stock FROM products WHERE id = ?", Integer.class, productId))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE product_id = ?", Long.class, productId))
                .isZero();
    }
}
