package cn.edu.lzu.marketplace.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单业务集成测试（L2-12 覆盖项 + 真实 H2 落库核对）。
 *
 * <p>与单元测试不同：这里不 Mock Service / Repository，而是通过 {@code RANDOM_PORT}
 * 启动真实 Tomcat，用 {@link TestRestTemplate} 走完整 HTTP → Controller → Service
 * → Repository 链路，落库到独立 H2 内存库 {@code jdbc:h2:mem:itest}（与其它测试隔离）。
 * 断言直接查询数据库，验证库存、订单条数、金额与快照是否真实落库。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:itest;DB_CLOSE_DELAY=-1")
class OrderIntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanDatabase() {
        TestDb.reset(jdbcTemplate);
    }

    private ResponseEntity<String> postOrder(long productId, int quantity) {
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        body.put("quantity", quantity);
        body.put("buyerName", "张三");
        body.put("buyerPhone", "13800138000");
        body.put("address", "兰州市城关区天水南路222号");
        return restTemplate.postForEntity("/api/orders", body, String.class);
    }

    @Test
    void normalOrderDeductsStockAndSnapshotsProduct() throws Exception {
        long productId = TestDb.insertProduct(jdbcTemplate, "考研数学复习全书", 4990L, 3, "on");

        ResponseEntity<String> resp = postOrder(productId, 2);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        JsonNode root = objectMapper.readTree(resp.getBody());
        assertEquals(0, root.path("code").asInt());
        JsonNode data = root.path("data");
        assertEquals(2, data.path("quantity").asInt());
        assertEquals(4990L, data.path("unitPrice").asLong(), "单价应来自服务端商品表");
        assertEquals(9980L, data.path("totalAmount").asLong(), "总额 = 单价 × 数量");
        assertEquals("考研数学复习全书", data.path("productTitle").asText(), "商品标题应快照到订单");
        assertEquals("created", data.path("status").asText());
        assertTrue(data.path("orderNo").asText().matches("SO\\d{8}\\d{6}"), "订单号格式不符: " + data.path("orderNo").asText());

        // 真实落库核对：库存 3 → 1，且该商品新增 1 条订单
        assertEquals(1, TestDb.stockOf(jdbcTemplate, productId), "库存应减少 2");
        assertEquals(1L, TestDb.countOrdersFor(jdbcTemplate, productId), "应恰好新增 1 条订单");
    }

    @Test
    void insufficientStockReturns409AndLeavesDataUnchanged() throws Exception {
        long productId = TestDb.insertProduct(jdbcTemplate, "库存告急商品", 100L, 1, "on");

        ResponseEntity<String> resp = postOrder(productId, 5);

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertEquals(40901, objectMapper.readTree(resp.getBody()).path("code").asInt());
        assertEquals(1, TestDb.stockOf(jdbcTemplate, productId), "库存不足时库存不得变化");
        assertEquals(0L, TestDb.countOrdersFor(jdbcTemplate, productId), "库存不足时不得产生新订单");
    }

    @Test
    void zeroQuantityReturns400AndLeavesDataUnchanged() throws Exception {
        long productId = TestDb.insertProduct(jdbcTemplate, "零数量商品", 100L, 1, "on");

        ResponseEntity<String> resp = postOrder(productId, 0);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals(40001, objectMapper.readTree(resp.getBody()).path("code").asInt());
        assertEquals(1, TestDb.stockOf(jdbcTemplate, productId), "非法数量时库存不得变化");
        assertEquals(0L, TestDb.countOrdersFor(jdbcTemplate, productId), "非法数量时不得产生新订单");
    }
}
