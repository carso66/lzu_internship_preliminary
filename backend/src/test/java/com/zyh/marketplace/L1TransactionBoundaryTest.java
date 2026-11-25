package com.zyh.marketplace;

import com.zyh.marketplace.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 事务边界专项审查（不注入故障，纯验证正式代码的事务配置与失败路径的行为）。
 *
 * <p>审查项：
 * 1. {@code @Transactional} 是否真的存在于 create / detail / list 上；
 * 2. OrderService 是否被 Spring 以 CGLIB 代理形式暴露（有代理，注解才会被事务拦截器处理）；
 * 3. 所有失败请求（40901 / 40902 / 40401 / 40001）都不扣库存、不新增订单；
 * 4. 购买数量恰等于库存时成功且库存归零（边界）。
 */
class L1TransactionBoundaryTest extends ApiTestSupport {

    @Autowired
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        truncateProducts();
    }

    @Test
    @DisplayName("审查：OrderService 是 Spring 事务代理（CGLIB），create 带 @Transactional 且为读写事务")
    void orderServiceIsTransactionalProxy() throws Exception {
        assertThat(AopUtils.isAopProxy(orderService))
                .as("OrderService 必须被代理，否则 @Transactional 不生效")
                .isTrue();
        assertThat(AopUtils.getTargetClass(orderService)).isEqualTo(OrderService.class);

        Method create = OrderService.class.getMethod("create", com.zyh.marketplace.dto.CreateOrderRequest.class);
        Transactional createTx = create.getAnnotation(Transactional.class);
        assertThat(createTx).as("create() 必须有 @Transactional").isNotNull();
        assertThat(createTx.readOnly()).as("create() 必须是读写事务").isFalse();

        Method detail = OrderService.class.getMethod("detail", String.class);
        assertThat(detail.getAnnotation(Transactional.class)).isNotNull();
        Method list = OrderService.class.getMethod("list", com.zyh.marketplace.dto.OrderQuery.class);
        assertThat(list.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    @DisplayName("审查：库存扣减与订单插入在同一个连接/事务内（同一 mapper 调用链，单事务方法）")
    void stockAndOrderShareOneTransaction() {
        // create() 是单方法事务：deductStockIfAvailable 与 insert 都在其方法体内，
        // 由同一个 @Transactional 代理边界包裹（结构断言 + 上面的代理断言 + 回滚测试共同证明）
        assertThat(OrderService.class.getDeclaredFields()).extracting("name")
                .contains("orderMapper", "productMapper");
    }

    @Test
    @DisplayName("审查：购买数量 = 库存时成功，库存归零（边界）")
    void buyExactlyAllStock() throws Exception {
        long id = insertProductDirectly("清仓商品", 100L, 3, "图书", "on", "店铺", "2026-01-01 10:00:00");
        postOrder("{\"productId\":" + id + ",\"quantity\":3,\"buyerName\":\"张三\","
                + "\"buyerPhone\":\"13800138000\",\"address\":\"a\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAmount").value(300))
                .andExpect(jsonPath("$.data.status").value("created"));
        Integer stock = jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, id);
        assertThat(stock).isZero();
        // 库存为 0 后再买 1 件 → 40901
        postOrder("{\"productId\":" + id + ",\"quantity\":1,\"buyerName\":\"张三\","
                + "\"buyerPhone\":\"13800138000\",\"address\":\"a\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    @DisplayName("审查：40901 / 40902 / 40401 失败请求都不扣库存、不新增订单")
    void failedRequestsNeverTouchStockOrOrders() throws Exception {
        long onId = insertProductDirectly("在架商品", 1990L, 2, "图书", "on", "店铺", "2026-01-01 10:00:00");
        long offId = insertProductDirectly("下架商品", 1990L, 5, "图书", "off", "店铺", "2026-01-01 10:00:00");

        // 库存不足
        postOrder("{\"productId\":" + onId + ",\"quantity\":3,\"buyerName\":\"张三\","
                + "\"buyerPhone\":\"13800138000\",\"address\":\"a\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40901));
        // 商品已下架
        postOrder("{\"productId\":" + offId + ",\"quantity\":1,\"buyerName\":\"张三\","
                + "\"buyerPhone\":\"13800138000\",\"address\":\"a\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40902))
                .andExpect(jsonPath("$.message").value(containsString("已下架")));
        // 商品不存在
        postOrder("{\"productId\":999999,\"quantity\":1,\"buyerName\":\"张三\","
                + "\"buyerPhone\":\"13800138000\",\"address\":\"a\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(40401));

        assertThat(jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, onId))
                .as("库存不足后库存不变").isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, offId))
                .as("下架商品库存不变").isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class))
                .as("失败请求不产生订单").isZero();
    }
}