package com.lzu.marketplace.payment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/**
 * 支付回滚测试不能加 @Transactional：需要让 service 事务真实提交或回滚，
 * 再通过独立连接确认订单和支付记录的最终状态。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:payment-rollback-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@ActiveProfiles("test")
class PaymentTransactionTest {

    @Autowired
    private PaymentService service;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private PaymentRepository paymentRepository;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");
    }

    @Test
    void rollsBackOrderUpdateWhenPaymentInsertFails() {
        String orderNo = createOrder();

        doThrow(new DataIntegrityViolationException("forced payment insert failure"))
                .when(paymentRepository).insert(any(PaymentDraft.class));

        CreatePaymentRequest request = new CreatePaymentRequest(orderNo, "alipay");
        assertThatThrownBy(() -> service.pay(request))
                .isInstanceOf(DataIntegrityViolationException.class);

        Map<String, Object> order = jdbc.queryForMap(
                "SELECT status, paid_at FROM orders WHERE order_no = ?", orderNo);
        assertThat(order.get("status")).isEqualTo("created");
        assertThat(order.get("paid_at")).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_no = ?", Long.class, orderNo))
                .isZero();

        // 故障解除后同一订单仍可正常支付，且提交的订单时间与支付记录一致。
        doCallRealMethod().when(paymentRepository).insert(any(PaymentDraft.class));
        Payment payment = service.pay(request);
        assertThat(payment.amount()).isEqualTo(1000L);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM orders WHERE order_no = ?", String.class, orderNo))
                .isEqualTo("paid");
        assertThat(jdbc.queryForObject(
                "SELECT paid_at FROM orders WHERE order_no = ?", OffsetDateTime.class, orderNo))
                .isEqualTo(payment.paidAt());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_no = ?", Long.class, orderNo))
                .isEqualTo(1L);
    }

    @Test
    void rollsBackBothWritesWhenPaymentReadFails() {
        String orderNo = createOrder();
        // INSERT 已实际执行，后续读取失败仍必须撤销订单更新及支付记录。
        doReturn(Optional.empty()).when(paymentRepository).findByPaymentNo(any(String.class));

        assertThatThrownBy(() -> service.pay(new CreatePaymentRequest(orderNo, "wechat")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("写入支付记录后无法读取支付记录");

        Map<String, Object> order = jdbc.queryForMap(
                "SELECT status, paid_at FROM orders WHERE order_no = ?", orderNo);
        assertThat(order.get("status")).isEqualTo("created");
        assertThat(order.get("paid_at")).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_no = ?", Long.class, orderNo))
                .isZero();
    }

    private String createOrder() {
        jdbc.update("""
                        INSERT INTO products (title, price, stock, category, seller_name, status)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                "支付回滚测试商品", 1000, 5, "文具", "测试店铺", "on");
        Long productId = jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);
        assertThat(productId).isNotNull();
        jdbc.update("""
                        INSERT INTO orders (order_no, product_id, product_title, quantity, unit_price,
                                            total_amount, buyer_name, buyer_phone, address)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "SO_PAYMENT_ROLLBACK_TEST", productId, "支付回滚测试商品", 1, 1000,
                1000, "张三", "13800138000", "测试地址");
        return "SO_PAYMENT_ROLLBACK_TEST";
    }
}
