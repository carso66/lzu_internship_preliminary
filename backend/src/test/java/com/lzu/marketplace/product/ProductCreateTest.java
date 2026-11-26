package com.lzu.marketplace.product;

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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/products（官方 §4 L0-2）验收测试。
 *
 * <p>每个用例前清空 products，避免自增 id 与数据互相影响。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductCreateTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    /** L2-11：本接口需要 Bearer token，每个用例前真实登录一次。 */
    private String token;

    private Map<String, Object> validBody;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");

        token = AuthTestSupport.login(mockMvc, objectMapper);

        validBody = new LinkedHashMap<>();
        validBody.put("title", "测试商品");
        validBody.put("price", 1990);
        validBody.put("stock", 10);
        validBody.put("category", "文具");
        validBody.put("description", "备注");
        validBody.put("sellerName", "校园数码铺");
    }

    // ================= 成功 =================

    @Test
    @DisplayName("发布成功 → 201，返回完整商品，status 默认 on")
    void createSuccess() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.title").value("测试商品"))
                .andExpect(jsonPath("$.data.price").value(1990))
                .andExpect(jsonPath("$.data.stock").value(10))
                .andExpect(jsonPath("$.data.category").value("文具"))
                .andExpect(jsonPath("$.data.description").value("备注"))
                .andExpect(jsonPath("$.data.sellerName").value("校园数码铺"))
                .andExpect(jsonPath("$.data.status").value("on"))
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    @DisplayName("发布成功确实落库，且字段一致")
    void createPersists() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody)))
                .andExpect(status().isCreated());

        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM products", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, count);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT title, price, stock, category, description, status, seller_name FROM products");
        org.junit.jupiter.api.Assertions.assertEquals("测试商品", row.get("TITLE"));
        org.junit.jupiter.api.Assertions.assertEquals(1990L, ((Number) row.get("PRICE")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals(10, ((Number) row.get("STOCK")).intValue());
        org.junit.jupiter.api.Assertions.assertEquals("on", row.get("STATUS"));
        org.junit.jupiter.api.Assertions.assertEquals("校园数码铺", row.get("SELLER_NAME"));
    }

    @Test
    @DisplayName("description 可省略，返回 null，不报错")
    void descriptionOptional() throws Exception {
        validBody.remove("description");
        mockMvc.perform(post("/api/products")
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.description").doesNotExist());
    }

    @Test
    @DisplayName("stock 可以为 0（下架前预售为 0 是合法状态）")
    void zeroStockAccepted() throws Exception {
        validBody.put("stock", 0);
        mockMvc.perform(post("/api/products")
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.stock").value(0));
    }

    @Test
    @DisplayName("客户端传 status 被忽略，服务端强制 on")
    void clientStatusIgnored() throws Exception {
        validBody.put("status", "off");
        mockMvc.perform(post("/api/products")
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("on"));
    }

    @Test
    @DisplayName("标题前后空白被去除")
    void titleTrimmed() throws Exception {
        validBody.put("title", "  测试商品  ");
        mockMvc.perform(post("/api/products")
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("测试商品"));
    }

    // ================= 缺少必填字段 =================

    @Test
    @DisplayName("缺少 title → 400 / 40001，message 含 title")
    void missingTitle() throws Exception {
        validBody.remove("title");
        expectBadRequest("title");
    }

    @Test
    @DisplayName("缺少 price → 400 / 40001")
    void missingPrice() throws Exception {
        validBody.remove("price");
        expectBadRequest("price");
    }

    @Test
    @DisplayName("缺少 stock → 400 / 40001")
    void missingStock() throws Exception {
        validBody.remove("stock");
        expectBadRequest("stock");
    }

    @Test
    @DisplayName("缺少 category → 400 / 40001")
    void missingCategory() throws Exception {
        validBody.remove("category");
        expectBadRequest("category");
    }

    @Test
    @DisplayName("缺少 sellerName → 400 / 40001")
    void missingSellerName() throws Exception {
        validBody.remove("sellerName");
        expectBadRequest("sellerName");
    }

    @Test
    @DisplayName("title 为空白字符串 → 400 / 40001")
    void blankTitle() throws Exception {
        validBody.put("title", "   ");
        expectBadRequest("title");
    }

    // ================= 非法价格 =================

    @Test
    @DisplayName("price = 0 → 400 / 40001")
    void zeroPrice() throws Exception {
        validBody.put("price", 0);
        expectBadRequest("price");
    }

    @Test
    @DisplayName("price 为负数 → 400 / 40001")
    void negativePrice() throws Exception {
        validBody.put("price", -1);
        expectBadRequest("price");
    }

    @Test
    @DisplayName("price 超出上限 → 400 / 40001")
    void priceOverMax() throws Exception {
        validBody.put("price", 1_000_000_000L);
        expectBadRequest("price");
    }

    @Test
    @DisplayName("price 为字符串 \"1990\" → 400 / 40001（严格类型）")
    void priceAsString() throws Exception {
        validBody.put("price", "1990");
        expectBadRequest(null);
    }

    @Test
    @DisplayName("price 为小数 19.9 → 400 / 40001（严格类型）")
    void priceAsDecimal() throws Exception {
        validBody.put("price", 19.9);
        expectBadRequest(null);
    }

    @Test
    @DisplayName("price 为 null → 400 / 40001")
    void priceNull() throws Exception {
        validBody.put("price", null);
        expectBadRequest("price");
    }

    // ================= 非法库存 =================

    @Test
    @DisplayName("stock 为负数 → 400 / 40001")
    void negativeStock() throws Exception {
        validBody.put("stock", -1);
        expectBadRequest("stock");
    }

    @Test
    @DisplayName("stock 超出上限 → 400 / 40001")
    void stockOverMax() throws Exception {
        validBody.put("stock", 10000);
        expectBadRequest("stock");
    }

    @Test
    @DisplayName("stock 为字符串 \"10\" → 400 / 40001（严格类型）")
    void stockAsString() throws Exception {
        validBody.put("stock", "10");
        expectBadRequest(null);
    }

    @Test
    @DisplayName("stock 为小数 1.5 → 400 / 40001（严格类型）")
    void stockAsDecimal() throws Exception {
        validBody.put("stock", 1.5);
        expectBadRequest(null);
    }

    // ================= 非法标题 / 分类 / 卖家名 =================

    @Test
    @DisplayName("title 超过 50 字符 → 400 / 40001")
    void titleTooLong() throws Exception {
        validBody.put("title", "x".repeat(51));
        expectBadRequest("title");
    }

    @Test
    @DisplayName("category 超过 32 字符 → 400 / 40001")
    void categoryTooLong() throws Exception {
        validBody.put("category", "x".repeat(33));
        expectBadRequest("category");
    }

    @Test
    @DisplayName("sellerName 超过 64 字符 → 400 / 40001")
    void sellerNameTooLong() throws Exception {
        validBody.put("sellerName", "x".repeat(65));
        expectBadRequest("sellerName");
    }

    @Test
    @DisplayName("description 超过 200 字符 → 400 / 40001")
    void descriptionTooLong() throws Exception {
        validBody.put("description", "x".repeat(201));
        expectBadRequest("description");
    }

    @Test
    @DisplayName("category 任意字符串均接受（题目未要求枚举）")
    void anyCategoryAccepted() throws Exception {
        validBody.put("category", "自定义分类");
        mockMvc.perform(post("/api/products")
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.category").value("自定义分类"));
    }

    // ================= 请求体整体非法 =================

    @Test
    @DisplayName("空请求体 → 400，统一结构")
    void emptyBody() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("非法 JSON → 400 / 40002")
    void malformedJson() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    @DisplayName("SQL 注入式标题被当作普通字符串存储，不破坏表")
    void sqlInjectionTitleStoredLiterally() throws Exception {
        String evil = "'; DROP TABLE products; --";
        validBody.put("title", evil);
        mockMvc.perform(post("/api/products")
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value(evil));

        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM products", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, count, "products 表应仍然存在且有 1 条记录");
    }

    // ================= helpers =================

    private void expectBadRequest(String fieldInMessage) throws Exception {
        var result = mockMvc.perform(post("/api/products")
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data").doesNotExist());

        if (fieldInMessage != null) {
            result.andExpect(jsonPath("$.message").value(containsString(fieldInMessage)));
        }
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }
}
