package com.example.marketplace.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:payment-idempotency-test;DB_CLOSE_DELAY=-1"
)
class PaymentApiTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsPaymentAndMarksOrderPaid() throws Exception {
        long productId = createProduct(4990, 5);
        String orderNo = createOrder(productId, 1);

        ResponseEntity<String> response = postPayment(orderNo, "alipay");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("message").asText()).isEqualTo("ok");
        JsonNode payment = body.path("data");
        assertThat(payment.path("paymentId").asText()).matches("PAY\\d{8}[0-9A-F]{12}");
        assertThat(payment.path("orderNo").asText()).isEqualTo(orderNo);
        assertThat(payment.path("amount").asLong()).isEqualTo(4990L);
        assertThat(payment.path("status").asText()).isEqualTo("paid");
        assertThat(payment.path("paidAt").asText()).isNotBlank();

        ResponseEntity<String> orderResponse = restTemplate.getForEntity(
                "/api/orders/{orderNo}", String.class, orderNo);
        assertThat(orderResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = objectMapper.readTree(orderResponse.getBody()).path("data");
        assertThat(order.path("status").asText()).isEqualTo("paid");
        assertThat(order.path("paidAt").asText()).isEqualTo(payment.path("paidAt").asText());
    }

    @Test
    void rejectsSecondPaymentForSameOrder() throws Exception {
        long productId = createProduct(1590, 5);
        String orderNo = createOrder(productId, 1);
        ResponseEntity<String> firstPayment = postPayment(orderNo, "alipay");
        assertThat(firstPayment.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> repeatedPayment = postPayment(orderNo, "wechat");

        assertThat(repeatedPayment.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode body = objectMapper.readTree(repeatedPayment.getBody());
        assertThat(body.path("code").asInt()).isEqualTo(40903);
        assertThat(body.path("message").asText()).isEqualTo("订单已支付，请勿重复支付");
        assertThat(body.path("data").isNull()).isTrue();
        Long paymentCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM payments p
                JOIN orders o ON o.id = p.order_id
                WHERE o.order_no = ?
                """, Long.class, orderNo);
        assertThat(paymentCount).isEqualTo(1L);

        ResponseEntity<String> orderResponse = restTemplate.getForEntity(
                "/api/orders/{orderNo}", String.class, orderNo);
        assertThat(objectMapper.readTree(orderResponse.getBody()).path("data").path("status").asText())
                .isEqualTo("paid");
    }
    @Test
    void returnsPaymentById() throws Exception {
        long productId = createProduct(2990, 5);
        String orderNo = createOrder(productId, 1);
        ResponseEntity<String> creation = postPayment(orderNo, "wechat");
        assertThat(creation.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode createdPayment = objectMapper.readTree(creation.getBody()).path("data");
        String paymentId = createdPayment.path("paymentId").asText();

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/payments/{paymentId}", String.class, paymentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("code").asInt()).isZero();
        JsonNode payment = body.path("data");
        assertThat(payment.path("paymentId").asText()).isEqualTo(paymentId);
        assertThat(payment.path("orderNo").asText()).isEqualTo(orderNo);
        assertThat(payment.path("amount").asLong()).isEqualTo(2990L);
        assertThat(payment.path("method").asText()).isEqualTo("wechat");
        assertThat(payment.path("status").asText()).isEqualTo("paid");
        assertThat(payment.path("paidAt").asText()).isNotBlank();
    }
    @Test
    void returnsNotFoundWhenPaymentDoesNotExist() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/payments/{paymentId}", String.class, "PAY_NOT_FOUND");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("code").asInt()).isEqualTo(40401);
        assertThat(body.path("message").asText()).isEqualTo("支付记录不存在");
        assertThat(body.path("data").isNull()).isTrue();
    }
    @Test
    void rollsBackWhenUniquePaymentConstraintDetectsExistingPayment() throws Exception {
        long productId = createProduct(3990, 5);
        String orderNo = createOrder(productId, 1);
        Long orderId = jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE order_no = ?", Long.class, orderNo);
        jdbcTemplate.update("""
                INSERT INTO payments (payment_id, order_id, method, amount, paid_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, "PAY_EXISTING", orderId, "alipay", 3990L);

        ResponseEntity<String> response = postPayment(orderNo, "wechat");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("code").asInt()).isEqualTo(40903);
        assertThat(body.path("message").asText()).isEqualTo("订单已支付，请勿重复支付");

        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, orderId);
        assertThat(orderStatus).isEqualTo("created");
        Long paymentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id = ?", Long.class, orderId);
        assertThat(paymentCount).isEqualTo(1L);
    }
    @Test
    void allowsOnlyOnePaymentWhenTenRequestsCompeteForSameOrder() throws Exception {
        long productId = createProduct(2590, 5);
        String orderNo = createOrder(productId, 1);
        String payload = """
                {
                  "orderNo": "%s",
                  "method": "alipay"
                }
                """.formatted(orderNo);
        CountDownLatch ready = new CountDownLatch(10);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < 10; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("concurrent payment requests were not released");
                    }
                    return postJson("/api/payments", payload);
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ResponseEntity<String>> responses = new ArrayList<>();
            for (Future<ResponseEntity<String>> future : futures) {
                responses.add(future.get(20, TimeUnit.SECONDS));
            }

            assertThat(responses)
                    .filteredOn(response -> response.getStatusCode() == HttpStatus.OK)
                    .hasSize(1);

            List<ResponseEntity<String>> conflicts = responses.stream()
                    .filter(response -> response.getStatusCode() == HttpStatus.CONFLICT)
                    .toList();
            assertThat(conflicts).hasSize(9);
            for (ResponseEntity<String> conflict : conflicts) {
                assertThat(objectMapper.readTree(conflict.getBody()).path("code").asInt())
                        .isEqualTo(40903);
            }

            Long paymentCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM payments p
                    JOIN orders o ON o.id = p.order_id
                    WHERE o.order_no = ?
                    """, Long.class, orderNo);
            assertThat(paymentCount).isEqualTo(1L);
            String orderStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM orders WHERE order_no = ?", String.class, orderNo);
            assertThat(orderStatus).isEqualTo("paid");
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
    @Test
    void returnsNotFoundWhenPayingMissingOrder() throws Exception {
        ResponseEntity<String> response = postPayment("ORDER_NOT_FOUND", "alipay");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("code").asInt()).isEqualTo(40401);
        assertThat(body.path("message").asText()).isEqualTo("order ORDER_NOT_FOUND not found");
        assertThat(body.path("data").isNull()).isTrue();
    }
    @Test
    void rejectsUnsupportedPaymentMethod() throws Exception {
        long productId = createProduct(1990, 5);
        String orderNo = createOrder(productId, 1);

        ResponseEntity<String> response = postPayment(orderNo, "credit_card");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("code").asInt()).isEqualTo(40001);
        assertThat(body.path("message").asText()).isEqualTo("method must be alipay or wechat");
        Long paymentCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM payments p
                JOIN orders o ON o.id = p.order_id
                WHERE o.order_no = ?
                """, Long.class, orderNo);
        assertThat(paymentCount).isZero();
    }
    private String createOrder(long productId, int quantity) {
        ResponseEntity<String> response = postJson("/api/orders", """
                {
                  "productId": %d,
                  "quantity": %d,
                  "buyerName": "Buyer",
                  "buyerPhone": "13800138000",
                  "address": "Lanzhou"
                }
                """.formatted(productId, quantity));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        try {
            return objectMapper.readTree(response.getBody()).path("data").path("orderNo").asText();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private long createProduct(long price, int stock) {
        ResponseEntity<String> response = postJson("/api/products", """
                {
                  "title": "Payment test product",
                  "price": %d,
                  "stock": %d,
                  "category": "Tests",
                  "description": "Used to create payable orders",
                  "sellerName": "Campus shop"
                }
                """.formatted(price, stock));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        try {
            return objectMapper.readTree(response.getBody()).path("data").path("id").asLong();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private ResponseEntity<String> postPayment(String orderNo, String method) {
        return postJson("/api/payments", """
                {
                  "orderNo": "%s",
                  "method": "%s"
                }
                """.formatted(orderNo, method));
    }

    private ResponseEntity<String> postJson(String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }
}
