package com.lzu.marketplace.order;

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
 * 并发测试通过真实 HTTP 请求验收，而不是直接调用 service：
 * 这样能同时验证 MVC 参数绑定、统一响应和实际 HTTP 状态码。
 * 测试类不使用 @Transactional：每个 HTTP 请求在服务端线程中独立开启事务，
 * 所有请求结束后再读取已提交的最终库存和订单数。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:order-concurrency-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
)
@ActiveProfiles("test")
class OrderConcurrencyTest {

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
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");
    }

    @Test
    void onlyOneHttpRequestSucceedsWhenTenRequestsCompeteForOneItem() throws Exception {
        jdbc.update("""
                        INSERT INTO products (title, price, stock, category, seller_name, status)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                "并发测试商品", 1000, 1, "文具", "测试店铺", "on");
        Long productId = jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);
        assertThat(productId).isNotNull();

        String requestBody = mapper.writeValueAsString(Map.of(
                "productId", productId,
                "quantity", 1,
                "buyerName", "并发买家",
                "buyerPhone", "13700137000",
                "address", "测试地址"
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
                        throw new IllegalStateException("并发测试启动超时");
                    }
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/api/orders"))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                            .build();
                    HttpResponse<String> response = httpClient.send(
                            request, HttpResponse.BodyHandlers.ofString());
                    JsonNode body = mapper.readTree(response.body());
                    return new Attempt(response.statusCode(), body);
                }));
            }

            // 等所有客户端线程就绪后再同时放行，确保 10 个 HTTP 请求真正并发。
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Attempt> attempts = new ArrayList<>();
            for (Future<Attempt> future : futures) {
                attempts.add(future.get(30, TimeUnit.SECONDS));
            }
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

            // 所有 HTTP 响应都已返回后，再检查响应内容和最终数据。
            List<Attempt> successes = attempts.stream()
                    .filter(attempt -> attempt.httpStatus() == 201)
                    .toList();
            assertThat(successes).hasSize(1);
            Attempt winner = successes.get(0);
            JsonNode winnerData = winner.body().path("data");
            assertThat(winner.body().path("code").asInt()).isZero();
            assertThat(winner.body().path("message").asText()).isEqualTo("ok");
            assertThat(winnerData.path("orderNo").asText()).matches("^SO\\d{12,}$");
            assertThat(winnerData.path("quantity").asInt()).isEqualTo(1);
            assertThat(winnerData.path("unitPrice").asLong()).isEqualTo(1000L);
            assertThat(winnerData.path("totalAmount").asLong()).isEqualTo(1000L);
            assertThat(winnerData.path("status").asText()).isEqualTo("created");

            List<Attempt> failures = attempts.stream()
                    .filter(attempt -> attempt.httpStatus() != 201)
                    .toList();
            assertThat(failures).hasSize(REQUESTS - 1);
            assertThat(failures).allSatisfy(attempt -> {
                assertThat(attempt.httpStatus()).isEqualTo(409);
                assertThat(attempt.body().path("code").asInt()).isEqualTo(40901);
                assertThat(attempt.body().path("message").asText()).isEqualTo("库存不足");
                assertThat(attempt.body().path("data").isNull()).isTrue();
            });

            assertThat(jdbc.queryForObject(
                    "SELECT stock FROM products WHERE id = ?", Integer.class, productId))
                    .isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM orders WHERE product_id = ?", Long.class, productId))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT total_amount FROM orders WHERE product_id = ?", Long.class, productId))
                    .isEqualTo(1000L);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private record Attempt(int httpStatus, JsonNode body) {
    }
}
