package com.lzu.marketplace.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzu.marketplace.auth.AuthTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全链路集成测试：发布 → 查询 → 下单 → 订单查询 → 支付。
 *
 * <p>单个模块的测试（ProductXxx / OrderXxx / PaymentXxx）各自用 JDBC 播种，
 * 验证的是「模块在给定数据下行为正确」。本类不同：<b>每一步的输入都来自上一步真实接口的响应</b>，
 * 验证的是「模块之间能否衔接」——例如下单后库存、支付后订单状态在<b>查询接口</b>上是否真的可见，
 * 而不只是数据库里对了。
 *
 * <p>数据全部通过接口创建（不走 JDBC 播种），每个用例前清空三张表，互不依赖、与执行顺序无关。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseFlowTest {

    private static final String PHONE = "13800138000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    /** L2-11：发布/上下架需要 token，全链路用例前真实登录一次。 */
    private String token;

    @BeforeEach
    void clean() throws Exception {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");
        token = AuthTestSupport.login(mockMvc, objectMapper);
    }

    @Test
    @DisplayName("完整链路：发布 → 列表可见 → 下单 → 订单详情 → 支付 → 订单变 paid → 重复支付 409")
    void fullPurchaseJourney() throws Exception {
        // 1. 发布商品
        long productId = publishProduct("考研数学复习全书", 4990, 10);

        // 2. 列表能查到刚发布的商品
        mockMvc.perform(get("/api/products").param("keyword", "考研"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(productId));

        // 3. 下单 2 件
        String orderNo = placeOrder(productId, 2);
        assertEquals(8, stockOf(productId), "下单后库存应为 10 - 2 = 8");

        // 4. 订单详情：字段与服务端算价正确
        mockMvc.perform(get("/api/orders/{orderNo}", orderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.status").value("created"))
                .andExpect(jsonPath("$.data.unitPrice").value(4990))
                .andExpect(jsonPath("$.data.totalAmount").value(9980));

        // 5. 按手机号查订单列表能看到这一单
        mockMvc.perform(get("/api/orders").param("buyerPhone", PHONE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].orderNo").value(orderNo));

        // 6. 支付
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("orderNo", orderNo, "method", "alipay"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.amount").value(9980))
                .andExpect(jsonPath("$.data.status").value("paid"));

        // 7. 订单详情现在应为 paid（查询接口真实反映支付结果）
        mockMvc.perform(get("/api/orders/{orderNo}", orderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("paid"));

        // 8. 支付不扣库存
        assertEquals(8, stockOf(productId), "支付不应再扣库存");

        // 9. 重复支付 → 409 / 40903
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("orderNo", orderNo, "method", "wechat"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));
    }

    @Test
    @DisplayName("商品下架不影响已生成订单：历史订单仍可查询、仍可支付")
    void offShelfDoesNotAffectExistingOrder() throws Exception {
        long productId = publishProduct("限量商品", 1000, 5);
        String orderNo = placeOrder(productId, 1);

        // 下架商品
        mockMvc.perform(patch("/api/products/{id}/status", productId)
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"off\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("off"));

        // 下架只阻止新下单
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(orderBody(productId, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902));

        // 已生成订单不受影响：可查、可支付
        mockMvc.perform(get("/api/orders/{orderNo}", orderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("created"));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("orderNo", orderNo, "method", "wechat"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("paid"));
    }

    @Test
    @DisplayName("连续下单后订单列表按创建时间倒序，最新一单在最前")
    void consecutiveOrdersListedNewestFirst() throws Exception {
        long productId = publishProduct("测试商品", 1000, 10);

        String first = placeOrder(productId, 1);
        String second = placeOrder(productId, 1);
        String third = placeOrder(productId, 1);

        mockMvc.perform(get("/api/orders").param("buyerPhone", PHONE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items", hasSize(3)))
                .andExpect(jsonPath("$.data.items[0].orderNo").value(third))
                .andExpect(jsonPath("$.data.items[1].orderNo").value(second))
                .andExpect(jsonPath("$.data.items[2].orderNo").value(first));
    }

    @Test
    @DisplayName("支付后订单列表中的该订单状态为 paid")
    void paidStatusVisibleInOrderList() throws Exception {
        long productId = publishProduct("测试商品", 1000, 5);
        String orderNo = placeOrder(productId, 1);

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("orderNo", orderNo, "method", "alipay"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders").param("buyerPhone", PHONE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.items[0].status").value("paid"));
    }

    // ================= helpers =================

    private long publishProduct(String title, long price, int stock) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("price", price);
        body.put("stock", stock);
        body.put("category", "图书");
        body.put("sellerName", "校园数码铺");

        String resp = mockMvc.perform(post("/api/products")
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private String placeOrder(long productId, int quantity) throws Exception {
        String resp = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(orderBody(productId, quantity))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(resp).path("data");
        String orderNo = data.path("orderNo").asText();
        if (orderNo.isBlank()) {
            throw new IllegalStateException("下单响应缺少 orderNo: " + resp);
        }
        return orderNo;
    }

    private Map<String, Object> orderBody(long productId, int quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", productId);
        body.put("quantity", quantity);
        body.put("buyerName", "张三");
        body.put("buyerPhone", PHONE);
        body.put("address", "兰州市城关区天水南路222号");
        return body;
    }

    private int stockOf(long productId) {
        return jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }
}
