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
 * 真实事务回滚验证（对应 L1-6「扣库存和插入订单必须在同一事务」）。
 *
 * <p><b>故障注入方式与有效性：</b>用 {@link MockitoSpyBean} 包装真实的
 * {@link JdbcOrderRepository} Bean，仅把 {@code insert} 方法桩成抛 {@link RuntimeException}，
 * 其余方法（含走真实数据库的库存扣减）全部透传给真实实现。因此：
 * <ol>
 *   <li>库存扣减（{@code ProductRepository.deductStockIfAvailable}）由<b>未被 Mock 的
 *       真实 ProductRepository</b> 执行，真正对 H2 做了 {@code UPDATE ... stock = stock - ?}；</li>
 *   <li>故障在「扣减成功后、插入订单时」这一精确时点触发（由 {@code verify(orderRepository).insert(...)}
 *       证明流程已到达插入步骤，即扣减必然已经发生）；</li>
 *   <li>调用的是经 {@code @Transactional} AOP 代理包装的<b>真实 OrderServiceImpl</b>（通过 HTTP 进入），
 *       异常上抛触发事务回滚，而非靠测试方法自身的事务回滚掩盖问题。</li>
 * </ol>
 * <p>断言事务结束后库存恢复、无残留订单，即证明「扣减 + 插单」确在同一事务内原子提交。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:itest;DB_CLOSE_DELAY=-1")
class OrderRollbackIntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TestRestTemplate restTemplate;

    /** 仅替换 OrderRepository 为 spy，ProductRepository 保持真实，从而真实扣库。 */
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

        // 故障注入：让订单插入（扣减成功后的下一步）抛运行时异常
        doThrow(new RuntimeException("injected order insert failure"))
                .when(orderRepository).insert(any(Order.class));

        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        body.put("quantity", 2);
        body.put("buyerName", "张三");
        body.put("buyerPhone", "13800138000");
        body.put("address", "兰州市城关区天水南路222号");

        ResponseEntity<String> resp = restTemplate.postForEntity("/api/orders", body, String.class);

        // 未预期异常统一转为 500/50000，且不泄露堆栈
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        assertEquals(50000, objectMapper.readTree(resp.getBody()).path("code").asInt());

        // 证明故障发生在扣库存之后：订单插入确实被调用（即流程已走过扣减）
        verify(orderRepository).insert(any(Order.class));

        // 事务回滚后：库存恢复为初始值，且没有残留订单
        assertEquals(3, TestDb.stockOf(jdbcTemplate, productId), "回滚后库存应恢复为初始值");
        assertEquals(0L, TestDb.countOrdersFor(jdbcTemplate, productId), "回滚后不得残留订单");
    }
}
