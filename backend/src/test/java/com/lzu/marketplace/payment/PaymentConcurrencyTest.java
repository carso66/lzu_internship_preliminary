package com.lzu.marketplace.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过真实 HTTP 并发请求验证支付幂等：同一订单同时支付 10 次，
 * 只能有一次成功并写入一条支付记录，其余请求都返回 409 / 40903。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:payment-concurrency-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
)
@ActiveProfiles("test")
class PaymentConcurrencyTest {

    private static final int REQUESTS = 10;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper mapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");
    }

    @Test
    void onlyOnePaymentSucceedsWhenTenRequestsPaySameOrder() throws Exception {
        String orderNo = createOrder();
        String requestBody = mapper.writeValueAsString(Map.of(
                "orderNo", orderNo,
                "method", "alipay"
        ));

        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch ready = new CountDownLatch(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Attempt>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < REQUESTS; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("并发支付测试启动超时");
                    }
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/api/payments"))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                            .build();
                    HttpResponse<String> response = httpClient.send(
                            request, HttpResponse.BodyHandlers.ofString());
                    return new Attempt(response.statusCode(), mapper.readTree(response.body()));
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Attempt> attempts = new ArrayList<>();
            for (Future<Attempt> future : futures) {
                attempts.add(future.get(30, TimeUnit.SECONDS));
            }
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

            List<Attempt> successes = attempts.stream()
                    .filter(attempt -> attempt.httpStatus() == 200)
                    .toList();
            assertThat(successes).hasSize(1);
            JsonNode winner = successes.get(0).body().path("data");
            assertThat(successes.get(0).body().path("code").asInt()).isZero();
            assertThat(winner.path("paymentId").asText()).matches("^PAY\\d{12,}$");
            assertThat(winner.path("orderNo").asText()).isEqualTo(orderNo);
            assertThat(winner.path("amount").asLong()).isEqualTo(1000L);
            assertThat(winner.path("status").asText()).isEqualTo("paid");

            List<Attempt> failures = attempts.stream()
                    .filter(attempt -> attempt.httpStatus() != 200)
                    .toList();
            assertThat(failures).hasSize(REQUESTS - 1);
            assertThat(failures).allSatisfy(attempt -> {
                assertThat(attempt.httpStatus()).isEqualTo(409);
                assertThat(attempt.body().path("code").asInt()).isEqualTo(40903);
                assertThat(attempt.body().path("message").asText()).isEqualTo("订单已支付，不可重复支付");
                assertThat(attempt.body().path("data").isNull()).isTrue();
            });

            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE order_no = ?", Long.class, orderNo))
                    .isEqualTo(1L);
            assertThat(jdbc.queryForObject(
                    "SELECT payment_no FROM payments WHERE order_no = ?", String.class, orderNo))
                    .isEqualTo(winner.path("paymentId").asText());
            Map<String, Object> order = jdbc.queryForMap(
                    "SELECT status, paid_at FROM orders WHERE order_no = ?", orderNo);
            assertThat(order.get("status")).isEqualTo("paid");
            assertThat(order.get("paid_at")).isNotNull();
            assertThat(jdbc.queryForObject(
                    "SELECT amount FROM payments WHERE order_no = ?", Long.class, orderNo))
                    .isEqualTo(1000L);
            OffsetDateTime paidAt = OffsetDateTime.parse(winner.path("paidAt").asText());
            assertThat(order.get("paid_at")).isEqualTo(paidAt);
            assertThat(jdbc.queryForObject(
                    "SELECT paid_at FROM payments WHERE order_no = ?", OffsetDateTime.class, orderNo))
                    .isEqualTo(paidAt);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private String createOrder() {
        jdbc.update("""
                        INSERT INTO products (title, price, stock, category, seller_name, status)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                "并发支付测试商品", 1000, 1, "文具", "测试店铺", "on");
        Long productId = jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);
        assertThat(productId).isNotNull();
        jdbc.update("""
                        INSERT INTO orders (order_no, product_id, product_title, quantity, unit_price,
                                            total_amount, buyer_name, buyer_phone, address)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "SO_PAYMENT_CONCURRENCY_TEST", productId, "并发支付测试商品", 1, 1000,
                1000, "并发买家", "13800138000", "测试地址");
        return "SO_PAYMENT_CONCURRENCY_TEST";
    }

    private record Attempt(int httpStatus, JsonNode body) {
    }
}
