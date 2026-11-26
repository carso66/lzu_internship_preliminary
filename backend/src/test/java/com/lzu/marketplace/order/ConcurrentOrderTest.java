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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 并发不超卖验收测试（官方 §6 L2-10）。
 *
 * <p><b>为什么用真实 HTTP 而不是 MockMvc</b>：MockMvc 默认在调用线程内执行，
 * 用它做并发需要额外的异步派发设置，且容易掩盖事务/连接池的真实行为。
 * 这里用 {@code RANDOM_PORT} 起真实 Tomcat，每个任务通过 JDK {@link HttpClient}
 * 发独立 HTTP 请求，走完整的「Tomcat 线程 → Controller → @Transactional 代理
 * → JdbcTemplate → H2」链路，锁竞争和事务提交是真实的。
 *
 * <p><b>并发如何同时发起</b>：两级 {@link CountDownLatch}——
 * 所有任务先 {@code ready.countDown()}，再统一等 {@code startGate}；
 * 主线程确认全部就绪后 {@code startGate.countDown()} 一次放行。
 *
 * <p><b>不做任何伪装</b>：每个任务只发一次请求、不重试、不捕获业务异常；
 * {@link Future#get()} 会传播执行异常，若某任务意外失败，测试立即失败而非静默吞掉。
 *
 * <p><b>多轮稳定性</b>：{@link RepeatedTest} 每轮重新执行 {@code @BeforeEach} 清理数据、
 * 重建库存为 1 的商品，共跑 5 轮验证可重复性。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ConcurrentOrderTest {

    private static final int TASKS = 10;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    /** 单例 HttpClient：线程安全，10 个任务共享。 */
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");
    }

    @RepeatedTest(value = 5, name = "并发不超卖 第 {currentRepetition}/{totalRepetitions} 轮")
    @DisplayName("库存 1 的商品，10 个并发下单：恰好 1 个 201、9 个 409/40901，库存 0、订单 1")
    void tenConcurrentOrdersOnlyOneSucceeds() throws Exception {
        // 1. 每轮创建独立商品，库存恰为 1
        long productId = insertProductStockOne();

        // 2. 10 个独立并发任务，CountDownLatch 同步同时发起
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
                        startGate.await();          // 全部就绪后统一放行
                        return placeOrder(productId, idx);
                    } finally {
                        done.countDown();
                    }
                }));
            }

            ready.await();                          // 等 10 个任务全部就绪
            startGate.countDown();                  // 同时放行
            done.await();                           // 等全部完成

            // 3. 收集结果；get() 不吞异常，任务内部异常会让测试失败
            List<Result> results = new ArrayList<>();
            for (Future<Result> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }

            // 4. 断言响应分布
            long success = results.stream().filter(r -> r.httpStatus == 201).count();
            long conflict = results.stream()
                    .filter(r -> r.httpStatus == 409 && r.businessCode == 40901)
                    .count();
            long other = results.size() - success - conflict;

            assertEquals(1, success, "应恰好 1 个请求成功（201），实际响应：" + results);
            assertEquals(9, conflict, "应恰好 9 个请求 409 + 40901，实际响应：" + results);
            assertEquals(0, other, "不应出现 201 与 409/40901 之外的响应，实际：" + results);

            // 5. 断言数据库最终状态
            assertEquals(0, stockOf(productId), "最终库存必须为 0");
            assertEquals(1, orderCountOf(productId), "本轮必须恰好新增 1 条订单");
        } finally {
            pool.shutdownNow();
        }
    }

    /** 走真实 HTTP 链路下单，返回 HTTP 状态 + 业务码 + 原始响应体。 */
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
        try {
            JsonNode root = objectMapper.readTree(resp.body());
            code = root.path("code").asInt(-1);
        } catch (Exception ignore) {
            // 响应体不是 JSON 时保留 code = -1，交给断言层判定为 other
        }
        return new Result(resp.statusCode(), code, resp.body());
    }

    private long insertProductStockOne() {
        jdbc.update("INSERT INTO products (title, price, stock, category, status, seller_name) " +
                    "VALUES ('并发测试商品', 1000, 1, '测试', 'on', '店铺')");
        return jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);
    }

    private int stockOf(long productId) {
        return jdbc.queryForObject(
                "SELECT stock FROM products WHERE id = ?", Integer.class, productId);
    }

    private int orderCountOf(long productId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE product_id = ?", Integer.class, productId);
    }

    private record Result(int httpStatus, int businessCode, String body) {
        @Override
        public String toString() {
            // 截断 body，避免断言失败时打印过长
            String snippet = body.length() > 200 ? body.substring(0, 200) + "…" : body;
            return "{" + httpStatus + "/" + businessCode + " " + snippet + "}";
        }
    }
}
