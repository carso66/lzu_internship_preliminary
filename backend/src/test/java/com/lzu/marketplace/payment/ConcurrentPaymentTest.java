package com.lzu.marketplace.payment;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 并发重复支付验收测试（官方 §6 L2-9 幂等要求）。
 *
 * <p>10 个并发请求支付同一订单，验证：
 * 恰好 1 个 200、9 个 409 + 40903、payments 表恰好 1 条记录。
 *
 * <p>与 {@link ConcurrentOrderTest} 相同的真实并发手段：RANDOM_PORT 真实 Tomcat +
 * JDK HttpClient + 两级 CountDownLatch 同时放行，不重试、不吞异常、不串行。
 *
 * <p>{@link RepeatedTest} 多轮验证可重复性。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ConcurrentPaymentTest {

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

        jdbc.update("INSERT INTO products (title, price, stock, category, status, seller_name) " +
                    "VALUES ('并发支付商品', 1990, 1, '图书', 'on', '校园数码铺')");
        long productId = jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);
        jdbc.update("INSERT INTO orders (order_no, product_id, product_title, quantity, unit_price, " +
                    "total_amount, buyer_name, buyer_phone, address, status) " +
                    "VALUES ('SO-CONC-PAY', ?, '并发支付商品', 1, 1990, 1990, '张三', " +
                    "'13800138000', '兰州市城关区天水南路222号', 'created')", productId);
    }

    @RepeatedTest(value = 5, name = "并发重复支付 第 {currentRepetition}/{totalRepetitions} 轮")
    @DisplayName("同一订单 10 个并发支付：恰好 1 个 200、9 个 409/40903，仅 1 条支付记录")
    void concurrentPaymentOnlyOneSucceeds() throws Exception {
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
                        return pay(idx);
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

            long success = results.stream().filter(r -> r.httpStatus == 200).count();
            long alreadyPaid = results.stream()
                    .filter(r -> r.httpStatus == 409 && r.businessCode == 40903)
                    .count();
            long other = results.size() - success - alreadyPaid;

            assertEquals(1, success, "应恰好 1 个支付成功（200），实际：" + results);
            assertEquals(9, alreadyPaid, "应恰好 9 个 409 + 40903，实际：" + results);
            assertEquals(0, other, "不应出现 200 与 409/40903 之外的响应，实际：" + results);

            // 数据库兜底：恰好 1 条支付记录，且订单为 paid
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE order_no = 'SO-CONC-PAY'", Integer.class));
            assertEquals("paid", jdbc.queryForObject(
                    "SELECT status FROM orders WHERE order_no = 'SO-CONC-PAY'", String.class));
        } finally {
            pool.shutdownNow();
        }
    }

    private Result pay(int idx) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "orderNo", "SO-CONC-PAY",
                "method", idx % 2 == 0 ? "alipay" : "wechat"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/payments"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        int code = -1;
        try {
            JsonNode root = objectMapper.readTree(resp.body());
            code = root.path("code").asInt(-1);
        } catch (Exception ignore) {
            // 非 JSON 响应保留 code=-1，交由断言层判定为 other
        }
        return new Result(resp.statusCode(), code, resp.body());
    }

    private record Result(int httpStatus, int businessCode, String body) {
        @Override
        public String toString() {
            String snippet = body.length() > 200 ? body.substring(0, 200) + "…" : body;
            return "{" + httpStatus + "/" + businessCode + " " + snippet + "}";
        }
    }
}
