package com.lzu.marketplace.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发下单的订单号唯一性验收（官方 §5 L1-6 第 6 条：{@code order_no} 必须唯一）。
 *
 * <p><b>与 {@link ConcurrentOrderTest} 的分工</b>：那个测的是库存为 1 时「恰好 1 单成功」，
 * 只会产生 1 条订单，因而<b>无法验证订单号唯一性</b>——没有第二个单号可以去重。
 * 本类让库存充足（50），10 个并发请求全部成功，才真正把「并发下各自生成单号」这条路径跑到，
 * 再断言 10 个单号互不相同、库里恰好 10 条。
 *
 * <p>用真实 HTTP（{@code RANDOM_PORT} 真实 Tomcat + JDK HttpClient），
 * 与生产链路一致；断言的是数据库最终状态，不是方法调用。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ConcurrentOrderNoUniquenessTest {

    private static final int TASKS = 10;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");
    }

    @RepeatedTest(value = 3, name = "并发下单订单号唯一 第 {currentRepetition}/{totalRepetitions} 轮")
    @DisplayName("库存充足时 10 个并发下单全部成功，10 个 orderNo 互不相同，库存 50-10=40")
    void concurrentOrdersProduceDistinctOrderNumbers() throws Exception {
        long productId = insertProduct(50);

        CountDownLatch ready = new CountDownLatch(TASKS);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(TASKS);
        ExecutorService pool = Executors.newFixedThreadPool(TASKS);

        try {
            List<Future<Result>> futures = new ArrayList<>();
            for (int i = 0; i < TASKS; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    try {
                        ready.countDown();
                        startGate.await();
                        return placeOrder(productId, idx);
                    } finally {
                        done.countDown();
                    }
                }));
            }

            ready.await();
            startGate.countDown();
            done.await();

            List<Result> results = new ArrayList<>();
            for (Future<Result> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }

            // 全部成功
            for (Result r : results) {
                assertEquals(201, r.httpStatus, "并发且库存充足时每个请求都应成功，实际：" + r);
                assertEquals(0, r.businessCode, "成功响应业务码应为 0，实际：" + r);
                assertFalse(r.orderNo == null || r.orderNo.isBlank(), "成功响应必须含 orderNo：" + r);
            }

            // 订单号两两不同
            Set<String> orderNos = new HashSet<>();
            for (Result r : results) {
                assertTrue(orderNos.add(r.orderNo), "出现了重复订单号：" + r.orderNo);
            }
            assertEquals(TASKS, orderNos.size());
            assertEquals(TASKS, jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT order_no) FROM orders", Integer.class));

            // 数据库最终状态
            assertEquals(TASKS, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM orders WHERE product_id = ?", Integer.class, productId));
            assertEquals(40, stockOf(productId), "50 - 10 = 40");
        } finally {
            pool.shutdownNow();
        }
    }

    private Result placeOrder(long productId, int idx) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "productId", productId,
                "quantity", 1,
                "buyerName", "buyer" + idx,
                "buyerPhone", "138" + String.format("%08d", idx),
                "address", "addr" + idx));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/orders"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        int code = -1;
        String orderNo = null;
        try {
            JsonNode root = objectMapper.readTree(resp.body());
            code = root.path("code").asInt(-1);
            orderNo = root.path("data").path("orderNo").asText(null);
        } catch (Exception ignore) {
            // 非 JSON 响应交给断言层判定
        }
        return new Result(resp.statusCode(), code, orderNo, resp.body());
    }

    private long insertProduct(int stock) {
        jdbc.update("INSERT INTO products (title, price, stock, category, status, seller_name) " +
                    "VALUES ('并发下单商品', 1000, ?, '图书', 'on', '校园数码铺')", stock);
        return jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);
    }

    private int stockOf(long productId) {
        return jdbc.queryForObject(
                "SELECT stock FROM products WHERE id = ?", Integer.class, productId);
    }

    private record Result(int httpStatus, int businessCode, String orderNo, String body) {
        @Override
        public String toString() {
            String snippet = body.length() > 200 ? body.substring(0, 200) + "…" : body;
            return "{" + httpStatus + "/" + businessCode + " " + snippet + "}";
        }
    }
}
