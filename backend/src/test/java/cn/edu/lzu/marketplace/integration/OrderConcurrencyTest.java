package cn.edu.lzu.marketplace.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L2-10 并发不超卖测试。
 *
 * <p>通过 {@code RANDOM_PORT} + 真实 {@link TestRestTemplate} 发起<b>真实 HTTP 并发</b>请求，
 * 而非顺序发送或直接调用 Service。每轮独立准备一个「库存为 1、状态 on」的商品，
 * 用 {@link CountDownLatch} 让 10 个请求尽可能同时发出（每个请求买 1 件），
 * 并逐轮断言：
 * <ul>
 *   <li>恰好 1 个请求返回 201 / code 0；</li>
 *   <li>其余 9 个返回 409 / code 40901；</li>
 *   <li>超时、500 或其它错误一律记为「其它」，不得计入库存不足；</li>
 *   <li>最终库存为 0，该商品恰好新增 1 条订单。</li>
 * </ul>
 * <p>重复三轮，检查结果稳定性。可用 {@code mvn -Dtest=OrderConcurrencyTest test} 单独重跑。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:itest;DB_CLOSE_DELAY=-1")
class OrderConcurrencyTest {

    private static final int THREADS = 10;
    private static final int ROUNDS = 3;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanDatabase() {
        TestDb.reset(jdbcTemplate);
    }

    @Test
    void concurrentOrdersAgainstSingleStockDoNotOversell() throws Exception {
        for (int round = 1; round <= ROUNDS; round++) {
            long productId = TestDb.insertProduct(jdbcTemplate, "并发商品R" + round, 1990L, 1, "on");
            runRoundAndAssert(productId, round);
        }
    }

    private void runRoundAndAssert(long productId, int round) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        body.put("quantity", 1);
        body.put("buyerName", "并发买家");
        body.put("buyerPhone", "13800138000");
        body.put("address", "兰州市城关区天水南路222号");

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch fire = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    fire.await(); // 统一等待发令，尽量同时发出
                    return restTemplate.postForEntity("/api/orders", body, String.class);
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS), "第" + round + "轮：线程未在超时内就绪");
            fire.countDown(); // 同时释放 10 个请求

            int success = 0;
            int conflict = 0;
            int other = 0;
            for (Future<ResponseEntity<String>> future : futures) {
                try {
                    ResponseEntity<String> resp = future.get(10, TimeUnit.SECONDS);
                    int status = resp.getStatusCode().value();
                    int code = objectMapper.readTree(resp.getBody()).path("code").asInt();
                    if (status == 201 && code == 0) {
                        success++;
                    } else if (status == 409 && code == 40901) {
                        conflict++;
                    } else {
                        other++; // 超时 / 500 / 其它错误一律不得计入库存不足
                    }
                } catch (Exception e) {
                    other++;
                }
            }

            assertEquals(1, success, "第" + round + "轮：恰好应有 1 个请求成功");
            assertEquals(9, conflict, "第" + round + "轮：应有 9 个请求返回库存不足");
            assertEquals(0, other, "第" + round + "轮：不得出现超时/500/其它错误");
            assertEquals(0, TestDb.stockOf(jdbcTemplate, productId), "第" + round + "轮：最终库存应为 0");
            assertEquals(1L, TestDb.countOrdersFor(jdbcTemplate, productId), "第" + round + "轮：应恰好新增 1 条订单");
        } finally {
            pool.shutdownNow();
        }
    }
}
