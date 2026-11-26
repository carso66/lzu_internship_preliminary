package cn.edu.lzu.marketplace.integration;

import cn.edu.lzu.marketplace.model.Order;
import cn.edu.lzu.marketplace.repository.JdbcOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * 验证真实下单事务在订单插入抛异常时能回滚 H2 中的库存扣减。
 *
 * <p>通过 HTTP 进入 Spring 事务代理管理的订单服务，商品仓储实际执行条件更新。
 * 注入点仅为订单仓储的 {@code insert}，订单号回填影响 0 行时抛异常的行为由服务层单元测试覆盖。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:itest;DB_CLOSE_DELAY=-1")
class OrderRollbackIntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TestRestTemplate restTemplate;

    /** 包装真实订单仓储，以便只对订单插入方法注入故障。 */
    @MockitoSpyBean
    JdbcOrderRepository orderRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanDatabase() {
        TestDb.reset(jdbcTemplate);
    }

    @Test
    void stockDeductionRollsBackWhenOrderInsertFails() throws Exception {
        long productId = TestDb.insertProduct(jdbcTemplate, "回滚验证商品", 4990L, 3, "on");

        // ① 注入故障：库存扣减成功后，调用订单插入时抛出运行时异常。
        doThrow(new RuntimeException("injected order insert failure"))
                .when(orderRepository).insert(any(Order.class));

        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        body.put("quantity", 2);
        body.put("buyerName", "张三");
        body.put("buyerPhone", "13800138000");
        body.put("address", "兰州市城关区天水南路222号");

        // ② 发起真实 HTTP 请求，检查未预期异常映射为 HTTP 500 和业务码 50000。
        ResponseEntity<String> resp = restTemplate.postForEntity("/api/orders", body, String.class);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        assertEquals(50000, objectMapper.readTree(resp.getBody()).path("code").asInt());

        // ③ 确认执行已到达订单插入步骤，此前的库存条件更新已成功。
        verify(orderRepository).insert(any(Order.class));

        // ④ 请求结束后直接查库，确认库存恢复且无残留订单。
        assertEquals(3, TestDb.stockOf(jdbcTemplate, productId), "回滚后库存应恢复为初始值");
        assertEquals(0L, TestDb.countOrdersFor(jdbcTemplate, productId), "回滚后不得残留订单");
    }
}
