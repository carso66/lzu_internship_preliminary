package com.lzu.marketplace.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/payments（官方 §6 L2-9）验收测试。
 *
 * <p>订单用 JDBC 直接插入（下单链路已由 OrderCreateTest 覆盖），
 * 支付测试只关心支付自身的行为。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private long productId;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");

        jdbc.update("INSERT INTO products (title, price, stock, category, status, seller_name) " +
                    "VALUES ('支付测试商品', 1990, 5, '图书', 'on', '校园数码铺')");
        productId = jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);
    }

    // ================= 正常支付 =================

    @Test
    @DisplayName("正常支付 → 200，返回 paymentId/orderNo/amount/status/paidAt")
    void paySuccess() throws Exception {
        String orderNo = insertOrder("SO-PAY-1", 2, "created");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(orderNo, "alipay")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.paymentId").isString())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.amount").value(3980))   // 1990 * 2
                .andExpect(jsonPath("$.data.status").value("paid"))
                .andExpect(jsonPath("$.data.paidAt").exists());

        // 订单状态被更新为 paid，paid_at 已写
        Map<String, Object> order = jdbc.queryForMap(
                "SELECT status, paid_at FROM orders WHERE order_no = ?", orderNo);
        assertEquals("paid", order.get("STATUS"));
        assertNotNull(order.get("PAID_AT"));

        // 支付记录落库，金额来自订单总额
        Map<String, Object> payment = jdbc.queryForMap(
                "SELECT amount, method, status FROM payments WHERE order_no = ?", orderNo);
        assertEquals(3980L, ((Number) payment.get("AMOUNT")).longValue());
        assertEquals("alipay", payment.get("METHOD"));
        assertEquals("paid", payment.get("STATUS"));
    }

    @Test
    @DisplayName("wechat 支付方式同样合法")
    void payWithWechat() throws Exception {
        String orderNo = insertOrder("SO-PAY-2", 1, "created");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(orderNo, "wechat")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(1990));
    }

    @Test
    @DisplayName("支付金额只取订单服务端金额：客户端传 amount 被忽略")
    void clientAmountIgnored() throws Exception {
        String orderNo = insertOrder("SO-PAY-3", 3, "created");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderNo", orderNo);
        body.put("method", "alipay");
        body.put("amount", 1);   // 试图篡改支付金额

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(5970));  // 1990 * 3，非客户端传的 1

        Long storedAmount = jdbc.queryForObject(
                "SELECT amount FROM payments WHERE order_no = ?", Long.class, orderNo);
        assertEquals(5970L, storedAmount);
    }

    @Test
    @DisplayName("支付不扣减商品库存")
    void paymentDoesNotDeductStock() throws Exception {
        String orderNo = insertOrder("SO-PAY-4", 2, "created");
        int stockBefore = stockOf(productId);

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(orderNo, "alipay")))
                .andExpect(status().isOk());

        assertEquals(stockBefore, stockOf(productId),
                "支付后库存必须与支付前一致");
    }

    // ================= 参数校验 =================

    @Test
    @DisplayName("method 非法（cash）→ 400 / 40001")
    void invalidMethod() throws Exception {
        String orderNo = insertOrder("SO-PAY-5", 1, "created");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(orderNo, "cash")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("method")));
    }

    @Test
    @DisplayName("method 大写 ALIPAY → 400（严格匹配）")
    void methodCaseSensitive() throws Exception {
        String orderNo = insertOrder("SO-PAY-6", 1, "created");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(orderNo, "ALIPAY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("method 缺失 / null / 空串 → 400 / 40001")
    void methodMissingOrBlank() throws Exception {
        String orderNo = insertOrder("SO-PAY-7", 1, "created");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\",\"method\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\",\"method\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("orderNo 缺失 → 400 / 40001")
    void orderNoMissing() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"alipay\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    // ================= 订单不存在 / 重复支付 =================

    @Test
    @DisplayName("订单不存在 → 404 / 40401")
    void orderNotFound() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("SO-NOT-EXIST", "alipay")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("订单不存在")));
    }

    @Test
    @DisplayName("重复支付 → 409 / 40903，不生成第二条支付记录")
    void duplicatePayment() throws Exception {
        String orderNo = insertOrder("SO-PAY-8", 1, "created");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(orderNo, "alipay")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(orderNo, "wechat")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("订单已支付")));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_no = ?", Integer.class, orderNo),
                "重复支付不能生成第二条支付记录");
    }

    // ================= helpers =================

    private String insertOrder(String orderNo, int quantity, String status) {
        jdbc.update("INSERT INTO orders (order_no, product_id, product_title, quantity, unit_price, " +
                    "total_amount, buyer_name, buyer_phone, address, status) " +
                    "VALUES (?, ?, '支付测试商品', ?, 1990, ?, '张三', '13800138000', " +
                    "'兰州市城关区天水南路222号', ?)",
                orderNo, productId, quantity, 1990L * quantity, status);
        return orderNo;
    }

    private int stockOf(long productId) {
        return jdbc.queryForObject(
                "SELECT stock FROM products WHERE id = ?", Integer.class, productId);
    }

    private String payBody(String orderNo, String method) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderNo", orderNo);
        body.put("method", method);
        return objectMapper.writeValueAsString(body);
    }
}
