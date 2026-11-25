package com.zyh.marketplace;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L2-10 并发不超卖专项验证（官方配置：库存 1 的已上架商品，同时发起 10 个有效下单请求，每个买 1 件）。
 *
 * <p><b>实现方式</b>：
 * <ul>
 *   <li>{@code webEnvironment = RANDOM_PORT}：启动真实内嵌 Tomcat（随机端口），用 java.net.http.HttpClient
 *       发真实 HTTP 请求 —— 不是 MockMvc，不是串行伪装；</li>
 *   <li>数据源为隔离的内存测试库（application-test.yaml），与 ./data/marketplace 完全无关；</li>
 *   <li>每轮（@RepeatedTest，共 10 轮）先用真实 HTTP 创建全新的库存 1、状态 on 的商品，避免历史数据干扰；</li>
 *   <li>10 个任务提交到固定 10 线程池，全部 await 同一个 {@link CountDownLatch} 起跑门
 *       （countDown 一次全部放行），十个请求几乎同时发出；</li>
 *   <li>逐项记录：每个请求的 HTTP 状态 + 业务码 + 起跑偏差（ms）+ 单请求耗时（ms），
 *       等全部 Future 完成后**逐轮硬断言**官方要求（不靠汇总掩盖单轮失败）。</li>
 * </ul>
 *
 * <p><b>预期</b>：带库存条件的原子 UPDATE + @Transactional 下，恰好 1 单成功（201/code 0），
 * 其余 9 单 409/40901，库存归 0，本轮恰好新增 1 条订单，且成功订单号与库一致。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class L210ConcurrentOrderTest extends ApiTestSupport {

    /** 官方配置：10 个并发请求 */
    private static final int CONCURRENCY = 10;
    /** 官方配置：库存为 1 */
    private static final int INITIAL_STOCK = 1;
    /** 单价（分），凑整方便核对 totalAmount */
    private static final long UNIT_PRICE = 12345L;

    /** 10 轮实验的逐轮记录（供 @AfterAll 汇总表使用；并发下用同步 List） */
    private static final List<RoundResult> RESULTS = Collections.synchronizedList(new ArrayList<>());

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${local.server.port}")
    private int port;

    /** 单个请求的结果 */
    record Outcome(int httpStatus, String body, String orderNo, String error, long startOffsetMs, long durationMs) {
    }

    /** 单轮实验的记录 */
    record RoundResult(int round, int initialStock, int requests, int created201, int conflict409,
                       int otherErrors, int finalStock, int newOrders, String orderNoMatch, boolean pass,
                       long totalElapsedMs, long maxStartOffsetMs, long avgDurationMs) {
    }

    private int stockOf(long id) {
        Integer stock = jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, id);
        return stock == null ? -1 : stock;
    }

    private static String safeCode(String body) {
        try {
            // 注意：不能写成 String.valueOf(JsonPath.read(body, "$.code")) ——
            // 编译器会把 <T> T read(...) 的 T 按更具体的 String.valueOf(char[]) 推断成 char[]，
            // 运行时 Integer → char[] 强转抛 ClassCastException。拆成两步按 Object 取值。
            Object code = JsonPath.read(body, "$.code");
            return String.valueOf(code);
        } catch (Exception e) {
            return "<无法解析code:" + e.getClass().getSimpleName() + ":" + e.getMessage() + ">";
        }
    }

    @RepeatedTest(value = 10, name = "第 {currentRepetition}/{totalRepetitions} 轮")
    @DisplayName("L2-10 并发不超卖：10 并发买库存 1 的商品，恰好 1 单成功 9 单 40901")
    void tenConcurrentBuyersOfLastItem(RepetitionInfo repetition) throws Exception {
        int round = repetition.getCurrentRepetition();
        truncateProducts();

        // 1. 本轮全新商品：库存 1、已上架（走真实 HTTP，得到真实自增 id）
        String createBody = "{\"title\":\"L2-10并发商品第" + round + "轮\",\"price\":" + UNIT_PRICE
                + ",\"stock\":" + INITIAL_STOCK + ",\"category\":\"并发\",\"sellerName\":\"L2-10\"}";
        HttpResponse<String> create = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/products"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(createBody, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(create.statusCode()).as("第 %d 轮：创建商品应 201", round).isEqualTo(201);
        long productId = ((Number) JsonPath.read(create.body(), "$.data.id")).longValue();
        assertThat(stockOf(productId)).as("第 %d 轮：初始库存应为 1", round).isEqualTo(INITIAL_STOCK);

        // 2. 10 个任务在同一个起跑门后同时下单（禁止串行伪装并发）
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();
        long releaseAt = System.nanoTime();
        for (int i = 0; i < CONCURRENCY; i++) {
            String phone = "139" + String.format("%08d", round * 100 + i);
            String orderJson = "{\"productId\":" + productId + ",\"quantity\":1,\"buyerName\":\"并发买家" + i
                    + "\",\"buyerPhone\":\"" + phone + "\",\"address\":\"兰州市城关区天水南路222号\"}";
            futures.add(pool.submit(() -> {
                startGate.await();
                long startNano = System.nanoTime();
                long startOffsetMs = (startNano - releaseAt) / 1_000_000;
                try {
                    HttpResponse<String> resp = http.send(
                            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/orders"))
                                    .header("Content-Type", "application/json")
                                    .timeout(Duration.ofSeconds(15))
                                    .POST(HttpRequest.BodyPublishers.ofString(orderJson, StandardCharsets.UTF_8))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    long durationMs = (System.nanoTime() - startNano) / 1_000_000;
                    String orderNo = null;
                    try {
                        Object candidate = JsonPath.read(resp.body(), "$.data.orderNo");
                        orderNo = candidate == null ? null : candidate.toString();
                    } catch (Exception ignore) {
                        // 409 响应没有 data.orderNo，属预期
                    }
                    return new Outcome(resp.statusCode(), resp.body(), orderNo, null, startOffsetMs, durationMs);
                } catch (Exception e) {
                    long durationMs = (System.nanoTime() - startNano) / 1_000_000;
                    return new Outcome(-1, "", null, e.getClass().getSimpleName() + ": " + e.getMessage(),
                            startOffsetMs, durationMs);
                }
            }));
        }

        long wallStart = System.nanoTime();
        startGate.countDown(); // 10 个线程同时放行
        List<Outcome> outcomes = new ArrayList<>();
        try {
            for (Future<Outcome> f : futures) {
                outcomes.add(f.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        long totalElapsedMs = (System.nanoTime() - wallStart) / 1_000_000;
        long maxStartOffsetMs = outcomes.stream().mapToLong(Outcome::startOffsetMs).max().orElse(-1);
        long avgDurationMs = (long) outcomes.stream().mapToLong(Outcome::durationMs).average().orElse(-1);
        analyzeAndAssert(round, productId, outcomes, totalElapsedMs, maxStartOffsetMs, avgDurationMs);
    }

    /** 逐轮统计 + 硬断言 + 记录（失败证据先打印再断言，@AfterAll 里可看到已完成的轮次） */
    private void analyzeAndAssert(int round, long productId, List<Outcome> outcomes,
                                  long totalElapsedMs, long maxStartOffsetMs, long avgDurationMs) {
        int createdCount = 0;
        int conflictCount = 0;
        List<Outcome> others = new ArrayList<>();
        Outcome winner = null;
        for (Outcome o : outcomes) {
            if (o.httpStatus() == 201 && "0".equals(safeCode(o.body()))) {
                createdCount++;
                winner = o;
            } else if (o.httpStatus() == 409 && "40901".equals(safeCode(o.body()))) {
                conflictCount++;
            } else {
                others.add(o);
            }
        }

        int finalStock = stockOf(productId);
        Integer newOrders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE product_id = ?", Integer.class, productId);
        String dbOrderNo = null;
        Long dbTotalAmount = null;
        if (newOrders != null && newOrders == 1) {
            dbOrderNo = jdbcTemplate.queryForObject(
                    "SELECT order_no FROM orders WHERE product_id = ?", String.class, productId);
            dbTotalAmount = jdbcTemplate.queryForObject(
                    "SELECT total_amount FROM orders WHERE product_id = ?", Long.class, productId);
        }
        String orderNoMatch = winner != null && winner.orderNo() != null && winner.orderNo().equals(dbOrderNo)
                ? "一致" : "不一致";

        boolean pass = outcomes.size() == CONCURRENCY
                && createdCount == 1 && conflictCount == CONCURRENCY - 1 && others.isEmpty()
                && finalStock == 0 && newOrders != null && newOrders == 1
                && "一致".equals(orderNoMatch) && dbTotalAmount != null && dbTotalAmount == UNIT_PRICE;

        RESULTS.add(new RoundResult(round, INITIAL_STOCK, CONCURRENCY, createdCount, conflictCount,
                others.size(), finalStock, newOrders == null ? -1 : newOrders, orderNoMatch, pass,
                totalElapsedMs, maxStartOffsetMs, avgDurationMs));

        System.out.printf("[L2-10 第%2d轮] 并发=10 201=%d 40901=%d 其他=%d 最终库存=%d 新增订单=%s 订单号=%s "
                        + "轮耗时=%dms 最大起跑偏差=%dms 平均单请求=%dms %s%n",
                round, createdCount, conflictCount, others.size(), finalStock, newOrders, orderNoMatch,
                totalElapsedMs, maxStartOffsetMs, avgDurationMs, pass ? "PASS" : "FAIL");

        if (!pass) {
            // 失败证据：逐个请求的真实状态/业务码/响应前段
            for (Outcome o : outcomes) {
                System.out.printf("    [调试 第%d轮] status=%d code=%s orderNo=%s err=%s body=%.220s%n",
                        round, o.httpStatus(), safeCode(o.body()), o.orderNo(), o.error(), o.body());
            }
        }

        // 逐轮硬断言：任何一轮不满足即失败，失败证据已打印并记入 RESULTS（@AfterAll 可见）
        assertThat(outcomes).as("第 %d 轮：10 个请求必须全部完成，无超时/异常", round).hasSize(CONCURRENCY);
        assertThat(createdCount).as("第 %d 轮：恰好 1 个请求 201+code 0", round).isEqualTo(1);
        assertThat(conflictCount).as("第 %d 轮：恰好 9 个请求 409+40901", round).isEqualTo(CONCURRENCY - 1);
        assertThat(others).as("第 %d 轮：不允许其他 HTTP 状态/业务码/异常 %s", round, others).isEmpty();
        assertThat(maxStartOffsetMs).as("第 %d 轮：10 个请求应同时从起跑门释放", round).isLessThan(2000);
        assertThat(finalStock).as("第 %d 轮：最终库存应为 0", round).isZero();
        assertThat(newOrders).as("第 %d 轮：本轮恰好新增 1 条订单", round).isEqualTo(1);
        assertThat(dbOrderNo).as("第 %d 轮：成功订单号应与数据库一致", round).isEqualTo(winner.orderNo());
        assertThat(dbTotalAmount).as("第 %d 轮：订单金额应为单价 × 1 = %d", round, UNIT_PRICE)
                .isEqualTo(UNIT_PRICE);
    }

    @AfterAll
    static void printSummary() {
        System.out.println();
        System.out.println("==== L2-10 并发不超卖 · 10 轮独立实验汇总 ====");
        System.out.println("轮 | 初始库存 | 并发数 | 201 | 40901 | 其他 | 最终库存 | 新增订单 | 订单号核对 | 轮耗时ms | 起跑偏差ms | 平均单请求ms | 结果");
        for (RoundResult r : RESULTS) {
            System.out.printf("%2d | %d | %d | %d | %d | %d | %d | %d | %s | %d | %d | %d | %s%n",
                    r.round(), r.initialStock(), r.requests(), r.created201(), r.conflict409(),
                    r.otherErrors(), r.finalStock(), r.newOrders(), r.orderNoMatch(),
                    r.totalElapsedMs(), r.maxStartOffsetMs(), r.avgDurationMs(), r.pass() ? "PASS" : "FAIL");
        }
        long passed = RESULTS.stream().filter(RoundResult::pass).count();
        long created = RESULTS.stream().mapToLong(RoundResult::created201).sum();
        long conflict = RESULTS.stream().mapToLong(RoundResult::conflict409).sum();
        System.out.printf("共 %d 轮：PASS=%d FAIL=%d；201 总数=%d / 40901 总数=%d / 两者总和=%d / 其他总计=%d%n",
                RESULTS.size(), passed, RESULTS.size() - passed, created, conflict, created + conflict,
                RESULTS.stream().mapToLong(RoundResult::otherErrors).sum());
    }
}
