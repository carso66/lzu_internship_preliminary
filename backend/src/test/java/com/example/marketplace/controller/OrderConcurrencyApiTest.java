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
        properties = "spring.datasource.url=jdbc:h2:mem:order-concurrency-test;DB_CLOSE_DELAY=-1"
)
class OrderConcurrencyApiTest {

    private static final int ATTEMPTS = 10;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allowsOnlyOneOrderWhenTenRequestsCompeteForOneItem() throws Exception {
        long productId = createProduct(1);
        String payload = orderPayload(productId, 1);
        CountDownLatch ready = new CountDownLatch(ATTEMPTS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(ATTEMPTS);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < ATTEMPTS; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("concurrent requests were not released");
                    }
                    return post("/api/orders", payload);
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ResponseEntity<String>> responses = new ArrayList<>();
            for (Future<ResponseEntity<String>> future : futures) {
                responses.add(future.get(20, TimeUnit.SECONDS));
            }

            assertThat(responses)
                    .filteredOn(response -> response.getStatusCode() == HttpStatus.CREATED)
                    .hasSize(1);

            List<ResponseEntity<String>> conflicts = responses.stream()
                    .filter(response -> response.getStatusCode() == HttpStatus.CONFLICT)
                    .toList();
            assertThat(conflicts).hasSize(ATTEMPTS - 1);
            for (ResponseEntity<String> conflict : conflicts) {
                assertThat(objectMapper.readTree(conflict.getBody()).path("code").asInt())
                        .isEqualTo(40901);
            }

            ResponseEntity<String> productResponse = restTemplate.getForEntity(
                    "/api/products/{id}", String.class, productId);
            assertThat(productResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode product = objectMapper.readTree(productResponse.getBody()).path("data");
            assertThat(product.path("stock").asInt()).isZero();

            Long orderCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM orders WHERE product_id = ?", Long.class, productId);
            assertThat(orderCount).isEqualTo(1L);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private long createProduct(int stock) throws Exception {
        ResponseEntity<String> response = post("/api/products", """
                {
                  "title": "并发测试商品",
                  "price": 4990,
                  "stock": %d,
                  "category": "Books",
                  "description": "库存为 1 的并发测试商品",
                  "sellerName": "Campus shop"
                }
                """.formatted(stock));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody()).path("data").path("id").asLong();
    }

    private ResponseEntity<String> post(String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private String orderPayload(long productId, int quantity) {
        return """
                {
                  "productId": %d,
                  "quantity": %d,
                  "buyerName": "张三",
                  "buyerPhone": "13800138000",
                  "address": "兰州市城关区天水南路222号"
                }
                """.formatted(productId, quantity);
    }
}
