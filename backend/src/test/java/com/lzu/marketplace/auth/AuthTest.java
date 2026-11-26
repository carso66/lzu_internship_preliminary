package com.lzu.marketplace.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L2-11 最简鉴权验收（官方 §6）。
 *
 * <p>覆盖四类事实：
 * <ol>
 *   <li>登录接口本身：成功 / 密码错 / 请求体非法；</li>
 *   <li>被保护接口（发布、上下架）缺头或 token 无效 → 401 + 统一结构 + 正确业务码；</li>
 *   <li>被保护接口带有效 token → 行为与未加鉴权前一致（201 / 200）；</li>
 *   <li>公开接口（商品列表、详情、订单、支付、登录）无 token 正常。</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");
    }

    // ================= 登录接口 =================

    @Test
    @DisplayName("登录成功 → 200，data.token 为 64 位随机十六进制串")
    void loginSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin", "password", "market123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.token")
                        .value(matchesPattern("[0-9a-f]{64}")));
    }

    @Test
    @DisplayName("两次登录得到两个不同 token，均可用")
    void loginTwiceDistinctTokens() throws Exception {
        String t1 = AuthTestSupport.login(mockMvc, objectMapper);
        String t2 = AuthTestSupport.login(mockMvc, objectMapper);
        org.junit.jupiter.api.Assertions.assertNotEquals(t1, t2, "随机 token 不应重复");

        publishWithToken(t1).andExpect(status().isCreated());
        publishWithToken(t2).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("密码错误 → 400 / 40001，文案与账号不存在一致（防用户名枚举）")
    void wrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin", "password", "wrong"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("账号或密码不正确"));

        // 账号不存在：同码同文案
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "nobody", "password", "whatever"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("账号或密码不正确"));
    }

    @Test
    @DisplayName("登录请求体缺字段 / 非法 JSON → 400 / 40001、40002")
    void loginValidation() throws Exception {
        String[][] cases = {
                {"{\"username\":\"admin\"}", "40001"},
                {"{\"password\":\"market123\"}", "40001"},
                {"{\"username\":\"\",\"password\":\"\"}", "40001"},
                {"{bad", "40002"},
        };
        for (String[] c : cases) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(c[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(Integer.parseInt(c[1])));
        }
    }

    // ================= 被保护接口：拒绝路径 =================

    @Test
    @DisplayName("发布商品不带 Authorization → 401 / 40101，统一结构")
    void publishMissingToken() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProductJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").doesNotExist());

        // 确实没有落库
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM products", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(0, count);
    }

    @Test
    @DisplayName("发布商品带未签发 token / 非Bearer格式 → 401 / 40102")
    void publishInvalidToken() throws Exception {
        // 完整伪造 token
        String resp1 = mockMvc.perform(post("/api/products")
                        .header(AuthInterceptor.HEADER, "Bearer " + "0".repeat(64))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProductJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40102))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(
                resp1.contains("\"code\":40102"), "伪造 token 应业务码 40102: " + resp1);

        // scheme 错误
        mockMvc.perform(post("/api/products")
                        .header(AuthInterceptor.HEADER, "Basic YWRtaW46eA==")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProductJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40102));

        // Bearer 后空串
        mockMvc.perform(post("/api/products")
                        .header(AuthInterceptor.HEADER, "Bearer ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProductJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40102));
    }

    @Test
    @DisplayName("上下架：缺 token → 401 / 40101；坏 token → 401 / 40102")
    void statusChangeRequiresToken() throws Exception {
        long id = insertProduct();

        mockMvc.perform(patch("/api/products/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"off\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        mockMvc.perform(patch("/api/products/{id}/status", id)
                        .header(AuthInterceptor.HEADER, "Bearer not-a-real-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"off\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40102));

        // 状态没被改动
        String status = jdbc.queryForObject(
                "SELECT status FROM products WHERE id = ?", String.class, id);
        org.junit.jupiter.api.Assertions.assertEquals("on", status);
    }

    // ================= 被保护接口：带 token 可用 =================

    @Test
    @DisplayName("带有效 token 发布 → 201；再用同一 token 上下架 → 200")
    void protectedEndpointsWorkWithToken() throws Exception {
        String token = AuthTestSupport.login(mockMvc, objectMapper);

        long id = publishWithTokenReturnId(token);
        org.junit.jupiter.api.Assertions.assertTrue(id > 0);

        mockMvc.perform(patch("/api/products/{id}/status", id)
                        .header(AuthInterceptor.HEADER, AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"off\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("off"));
    }

    // ================= 公开接口不受影响 =================

    @Test
    @DisplayName("无 token：商品列表/详情/订单/支付/登录全部照常工作")
    void publicEndpointsUnaffected() throws Exception {
        long id = insertProduct();

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));

        // 下单（买家接口，不要求 token）
        String createResp = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(orderBody(id))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderNo = objectMapper.readTree(createResp).path("data").path("orderNo").asText();

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("orderNo", orderNo, "method", "alipay"))))
                .andExpect(status().isOk());
    }

    // ================= helpers =================

    private org.springframework.test.web.servlet.ResultActions publishWithToken(String token)
            throws Exception {
        return mockMvc.perform(post("/api/products")
                        .header(AuthInterceptor.HEADER, AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProductJson()));
    }

    private long publishWithTokenReturnId(String token) throws Exception {
        String resp = publishWithToken(token)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asLong();
    }

    private long insertProduct() {
        jdbc.update("INSERT INTO products (title, price, stock, category, status, seller_name) " +
                    "VALUES ('鉴权测试商品', 1000, 5, '测试', 'on', '店')");
        return jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);
    }

    private String validProductJson() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "鉴权测试商品");
        body.put("price", 1990);
        body.put("stock", 10);
        body.put("category", "文具");
        body.put("sellerName", "校园数码铺");
        return json(body);
    }

    private Map<String, Object> orderBody(long productId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", productId);
        body.put("quantity", 1);
        body.put("buyerName", "李四");
        body.put("buyerPhone", "13900139000");
        body.put("address", "兰州市城关区");
        return body;
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
