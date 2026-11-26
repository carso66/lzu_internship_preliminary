package com.lzu.marketplace.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:payment-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void paysOrderWritesPaymentAndMarksOrderPaid() throws Exception {
        String orderNo = createOrder(4990, 2);

        JsonNode paid = mapper.readTree(performPay(validBody(orderNo, "alipay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.paymentId").value(matchesPattern("^PAY\\d{12,}$")))
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.amount").value(9980))
                .andExpect(jsonPath("$.data.method").value("alipay"))
                .andExpect(jsonPath("$.data.status").value("paid"))
                .andReturn().getResponse().getContentAsString()).get("data");

        OffsetDateTime responsePaidAt = OffsetDateTime.parse(paid.get("paidAt").asText());
        assertThat(responsePaidAt.getOffset().getTotalSeconds()).isZero();

        Map<String, Object> order = jdbc.queryForMap(
                "SELECT status, paid_at FROM orders WHERE order_no = ?", orderNo);
        assertThat(order.get("status")).isEqualTo("paid");
        assertThat(order.get("paid_at")).isNotNull();

        List<Map<String, Object>> payments = jdbc.queryForList(
                "SELECT payment_no, order_no, amount, method, status, paid_at"
                        + " FROM payments WHERE order_no = ?", orderNo);
        assertThat(payments).hasSize(1);
        Map<String, Object> payment = payments.get(0);
        assertThat(payment.get("payment_no")).isEqualTo(paid.get("paymentId").asText());
        assertThat(payment.get("amount")).isEqualTo(9980L);
        assertThat(payment.get("method")).isEqualTo("alipay");
        assertThat(payment.get("status")).isEqualTo("paid");
        assertThat(payment.get("paid_at")).isNotNull();
        assertThat(payment.get("paid_at")).isEqualTo(order.get("paid_at"));
        assertThat(responsePaidAt).isEqualTo((OffsetDateTime) payment.get("paid_at"));

        mvc.perform(get("/api/orders/{orderNo}", orderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("paid"))
                .andExpect(jsonPath("$.data.paidAt").isNotEmpty());
    }

    @Test
    void usesOrderSnapshotAfterProductPriceChangesWithoutDeductingStockAgain() throws Exception {
        String orderNo = createOrder(4990, 2);
        Long productId = jdbc.queryForObject(
                "SELECT product_id FROM orders WHERE order_no = ?", Long.class, orderNo);
        jdbc.update("UPDATE products SET price = 1, status = 'off' WHERE id = ?", productId);

        performPay(validBody(orderNo, "alipay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(9980));

        assertThat(jdbc.queryForObject(
                "SELECT amount FROM payments WHERE order_no = ?", Long.class, orderNo))
                .isEqualTo(9980L);
        assertThat(jdbc.queryForObject(
                "SELECT stock FROM products WHERE id = ?", Integer.class, productId))
                .isEqualTo(98);
    }

    @Test
    void supportsWechatPayment() throws Exception {
        String orderNo = createOrder(1000, 1);

        performPay(validBody(orderNo, "wechat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("wechat"))
                .andExpect(jsonPath("$.data.amount").value(1000))
                .andExpect(jsonPath("$.data.status").value("paid"));
    }

    @Test
    void trimsOrderNoAndMethod() throws Exception {
        String orderNo = createOrder(1000, 1);

        performPay(validBody("  " + orderNo + "  ", "  wechat  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.method").value("wechat"));
    }

    @Test
    void ignoresClientSuppliedAmountStatusAndPaymentId() throws Exception {
        String orderNo = createOrder(4990, 2);
        ObjectNode body = validBody(orderNo, "alipay");
        body.put("paymentId", "HACKED");
        body.put("amount", 1);
        body.put("status", "created");

        performPay(body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId").value(matchesPattern("^PAY\\d{12,}$")))
                .andExpect(jsonPath("$.data.amount").value(9980))
                .andExpect(jsonPath("$.data.status").value("paid"));

        assertThat(jdbc.queryForObject(
                "SELECT amount FROM payments WHERE order_no = ?", Long.class, orderNo))
                .isEqualTo(9980L);
    }

    @Test
    void rejectsRepeatedPaymentWithoutCreatingAnotherRecord() throws Exception {
        String orderNo = createOrder(4990, 2);
        String firstPaymentId = mapper.readTree(performPay(validBody(orderNo, "alipay"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("paymentId").asText();

        performPay(validBody(orderNo, "wechat"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903))
                .andExpect(jsonPath("$.message").value("订单已支付，不可重复支付"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertThat(paymentCount(orderNo)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT payment_no FROM payments WHERE order_no = ?", String.class, orderNo))
                .isEqualTo(firstPaymentId);
        assertThat(jdbc.queryForObject(
                "SELECT method FROM payments WHERE order_no = ?", String.class, orderNo))
                .isEqualTo("alipay");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM orders WHERE order_no = ?", String.class, orderNo))
                .isEqualTo("paid");
    }

    @Test
    void returnsNotFoundForUnknownOrder() throws Exception {
        performPay(validBody("SO99999999999999", "alipay"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value("订单不存在"))
                .andExpect(jsonPath("$.data").value(nullValue()));
        assertThat(totalPaymentCount()).isZero();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL", "EMPTY", "BLANK", "alipayy", "ALIPAY", "cash", "123"
    })
    void rejectsInvalidMethod(String value) throws Exception {
        String orderNo = createOrder(1000, 1);
        ObjectNode body = validBody(orderNo, "alipay");
        switch (value) {
            case "NULL" -> body.putNull("method");
            case "EMPTY" -> body.put("method", "");
            case "BLANK" -> body.put("method", "   ");
            default -> body.put("method", value);
        }

        expectBadRequest(performPay(body))
                .andExpect(jsonPath("$.message", containsString("method")));
        assertThat(totalPaymentCount()).isZero();
        assertThat(orderStatus(orderNo)).isEqualTo("created");
    }

    @Test
    void rejectsMissingMethod() throws Exception {
        String orderNo = createOrder(1000, 1);
        ObjectNode body = validBody(orderNo, "alipay");
        body.remove("method");

        expectBadRequest(performPay(body))
                .andExpect(jsonPath("$.message", containsString("method")));
        assertThat(totalPaymentCount()).isZero();
        assertThat(orderStatus(orderNo)).isEqualTo("created");
    }

    @Test
    void rejectsNonStringMethod() throws Exception {
        String orderNo = createOrder(1000, 1);
        ObjectNode body = validBody(orderNo, "alipay");
        body.put("method", 1);

        expectBadRequest(performPay(body))
                .andExpect(jsonPath("$.message", containsString("method")));
        assertThat(totalPaymentCount()).isZero();
        assertThat(orderStatus(orderNo)).isEqualTo("created");
    }

    @Test
    void rejectsMissingOrBlankOrderNo() throws Exception {
        String orderNo = createOrder(1000, 1);

        ObjectNode missing = validBody(orderNo, "alipay");
        missing.remove("orderNo");
        expectBadRequest(performPay(missing))
                .andExpect(jsonPath("$.message", containsString("orderNo")));

        ObjectNode blank = validBody(orderNo, "alipay");
        blank.put("orderNo", "   ");
        expectBadRequest(performPay(blank))
                .andExpect(jsonPath("$.message", containsString("orderNo")));

        ObjectNode overlong = validBody(orderNo, "alipay");
        overlong.put("orderNo", "X".repeat(65));
        expectBadRequest(performPay(overlong))
                .andExpect(jsonPath("$.message", containsString("orderNo")));

        assertThat(totalPaymentCount()).isZero();
        assertThat(orderStatus(orderNo)).isEqualTo("created");
    }

    @Test
    void rejectsNonStringOrderNo() throws Exception {
        ObjectNode body = validBody("SO99999999999999", "alipay");
        body.put("orderNo", 123);

        expectBadRequest(performPay(body))
                .andExpect(jsonPath("$.message", containsString("orderNo")));
        assertThat(totalPaymentCount()).isZero();
    }

    private ResultActions performPay(ObjectNode body) throws Exception {
        return mvc.perform(post("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    private ObjectNode validBody(String orderNo, String method) {
        ObjectNode body = mapper.createObjectNode();
        body.put("orderNo", orderNo);
        body.put("method", method);
        return body;
    }

    private String createOrder(long price, int quantity) throws Exception {
        jdbc.update("""
                        INSERT INTO products (title, price, stock, category, seller_name, status)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                "支付测试商品", price, 100, "文具", "测试店铺", "on");
        Long productId = jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);
        assertThat(productId).isNotNull();

        ObjectNode body = mapper.createObjectNode();
        body.put("productId", productId);
        body.put("quantity", quantity);
        body.put("buyerName", "张三");
        body.put("buyerPhone", "13800138000");
        body.put("address", "兰州市城关区天水南路222号");
        JsonNode created = mapper.readTree(mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("data");
        return created.get("orderNo").asText();
    }

    private long paymentCount(String orderNo) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_no = ?", Long.class, orderNo);
        return count == null ? 0 : count;
    }

    private long totalPaymentCount() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM payments", Long.class);
        return count == null ? 0 : count;
    }

    private String orderStatus(String orderNo) {
        return jdbc.queryForObject(
                "SELECT status FROM orders WHERE order_no = ?", String.class, orderNo);
    }

    private ResultActions expectBadRequest(ResultActions actions) throws Exception {
        return actions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
