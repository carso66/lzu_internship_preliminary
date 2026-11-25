package com.example.marketplace.controller;

import com.example.marketplace.service.PaymentNumberGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:payment-id-collision-test;DB_CLOSE_DELAY=-1"
)
class PaymentIdCollisionApiTest {

    private static final String COLLIDING_PAYMENT_ID = "PAY_COLLISION";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private PaymentNumberGenerator paymentNumberGenerator;

    @BeforeEach
    void setUp() {
        when(paymentNumberGenerator.generate()).thenReturn(COLLIDING_PAYMENT_ID);
    }

    @Test
    void doesNotReportPaymentIdCollisionAsAlreadyPaid() throws Exception {
        long productId = createProduct();
        String occupiedOrderNo = createOrder(productId);
        String targetOrderNo = createOrder(productId);

        Long occupiedOrderId = jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE order_no = ?", Long.class, occupiedOrderNo);
        jdbcTemplate.update("""
                INSERT INTO payments (payment_id, order_id, method, amount, paid_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, COLLIDING_PAYMENT_ID, occupiedOrderId, "alipay", 4990L);

        ResponseEntity<String> response = postPayment(targetOrderNo);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("code").asInt()).isEqualTo(50000);
        assertThat(body.path("message").asText()).isEqualTo("internal server error");

        Long targetOrderId = jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE order_no = ?", Long.class, targetOrderNo);
        String targetOrderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, targetOrderId);
        assertThat(targetOrderStatus).isEqualTo("created");
        Long targetPaymentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id = ?", Long.class, targetOrderId);
        assertThat(targetPaymentCount).isZero();
    }

    private long createProduct() throws Exception {
        ResponseEntity<String> response = postJson("/api/products", """
                {
                  "title": "Payment collision test product",
                  "price": 4990,
                  "stock": 10,
                  "category": "Tests",
                  "description": "Used to verify payment id collisions",
                  "sellerName": "Campus shop"
                }
                """);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody()).path("data").path("id").asLong();
    }

    private String createOrder(long productId) throws Exception {
        ResponseEntity<String> response = postJson("/api/orders", """
                {
                  "productId": %d,
                  "quantity": 1,
                  "buyerName": "Buyer",
                  "buyerPhone": "13800138000",
                  "address": "Lanzhou"
                }
                """.formatted(productId));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody()).path("data").path("orderNo").asText();
    }

    private ResponseEntity<String> postPayment(String orderNo) {
        return postJson("/api/payments", """
                {
                  "orderNo": "%s",
                  "method": "wechat"
                }
                """.formatted(orderNo));
    }

    private ResponseEntity<String> postJson(String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }
}
