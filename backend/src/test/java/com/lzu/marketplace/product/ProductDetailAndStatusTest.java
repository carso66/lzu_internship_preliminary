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

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/products/{id}（L0-4）与 PATCH /api/products/{id}/status（L0-5）验收测试。
 *
 * <p>不硬编码自增 id：用 {@link #createProduct()} 从 201 响应里读实际 id。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductDetailAndStatusTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    /** L2-11：本类的发布/上下架均需要 token，用例前登录一次。 */
    private String token;

    @BeforeEach
    void clean() throws Exception {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");
        token = AuthTestSupport.login(mockMvc, objectMapper);
    }

    // ================= L0-4 详情 =================

    @Test
    @DisplayName("详情：存在 → 200，返回完整商品字段")
    void detailFound() throws Exception {
        long id = createProduct("测试商品", 1990, 10, "文具");

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.title").value("测试商品"))
                .andExpect(jsonPath("$.data.price").value(1990))
                .andExpect(jsonPath("$.data.stock").value(10))
                .andExpect(jsonPath("$.data.category").value("文具"))
                .andExpect(jsonPath("$.data.status").value("on"))
                .andExpect(jsonPath("$.data.sellerName").value("校园数码铺"))
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    @DisplayName("详情：不存在 → 404 / 40401")
    void detailNotFound() throws Exception {
        mockMvc.perform(get("/api/products/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value(containsString("商品不存在")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("详情：id 非数字 → 400 / 40001（不是 500）")
    void detailNonNumericId() throws Exception {
        mockMvc.perform(get("/api/products/{id}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("详情：id 超出 long 范围 → 400，不泄漏内部异常")
    void detailOverflowId() throws Exception {
        mockMvc.perform(get("/api/products/{id}", "99999999999999999999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("详情：负数 id → 404 / 40401（语法合法但不存在）")
    void detailNegativeId() throws Exception {
        mockMvc.perform(get("/api/products/{id}", -1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    // ================= L0-5 下架 =================

    @Test
    @DisplayName("下架：on → off，200，data 只含 id 与 status")
    void offShelf() throws Exception {
        long id = createProduct("测试商品", 1990, 10, "文具");

        mockMvc.perform(patch("/api/products/{id}/status", id)
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("off")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("off"))
                // 题目 L0-5 示例只给 id + status，不应返回完整商品
                .andExpect(jsonPath("$.data.title").doesNotExist())
                .andExpect(jsonPath("$.data.price").doesNotExist());
    }

    @Test
    @DisplayName("下架后详情与列表都反映新状态")
    void offShelfPersists() throws Exception {
        long id = createProduct("测试商品", 1990, 10, "文具");

        mockMvc.perform(patch("/api/products/{id}/status", id)
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("off")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(jsonPath("$.data.status").value("off"));

        mockMvc.perform(get("/api/products").param("status", "off"))
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/products").param("status", "on"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // ================= L0-5 上架 =================

    @Test
    @DisplayName("上架：先下架再上架，off → on")
    void onShelf() throws Exception {
        long id = createProduct("测试商品", 1990, 10, "文具");
        setStatus(id, "off");

        mockMvc.perform(patch("/api/products/{id}/status", id)
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("on")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("on"));
    }

    // ================= 重复设置相同状态 =================

    @Test
    @DisplayName("重复设为 off：幂等，仍返回 200 与 off")
    void repeatSameStatusOff() throws Exception {
        long id = createProduct("测试商品", 1990, 10, "文具");

        setStatus(id, "off");
        mockMvc.perform(patch("/api/products/{id}/status", id)
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("off")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("off"));
    }

    @Test
    @DisplayName("重复设为 on（新商品本来就是 on）：幂等，200")
    void repeatSameStatusOn() throws Exception {
        long id = createProduct("测试商品", 1990, 10, "文具");

        mockMvc.perform(patch("/api/products/{id}/status", id)
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("on")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("on"));
    }

    // ================= 非法枚举 =================

    @Test
    @DisplayName("status=unknown → 400 / 40001")
    void invalidStatusValue() throws Exception {
        long id = createProduct("测试商品", 1990, 10, "文具");

        mockMvc.perform(patch("/api/products/{id}/status", id)
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("unknown")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("status")));
    }

    @Test
    @DisplayName("status 大小写敏感：ON 不合法")
    void statusCaseSensitive() throws Exception {
        long id = createProduct("测试商品", 1990, 10, "文具");

        mockMvc.perform(patch("/api/products/{id}/status", id)
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ON")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("status 为空串 → 400 / 40001")
    void statusBlank() throws Exception {
        long id = createProduct("测试商品", 1990, 10, "文具");

        mockMvc.perform(patch("/api/products/{id}/status", id)
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("status 为 null → 400 / 40001")
    void statusNull() throws Exception {
        long id = createProduct("测试商品", 1990, 10, "文具");

        mockMvc.perform(patch("/api/products/{id}/status", id)
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("缺少 status 字段 → 400 / 40001")
    void statusMissing() throws Exception {
        long id = createProduct("测试商品", 1990, 10, "文具");

        mockMvc.perform(patch("/api/products/{id}/status", id)
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("status 为数字 1 → 400 / 40001（在 @Pattern 处被拒，不是类型错误）")
    void statusNotString() throws Exception {
        long id = createProduct("测试商品", 1990, 10, "文具");

        // 说明：ALLOW_COERCION_OF_SCALARS=false 只拦截「非字符串 → 数字」，
        // 不拦截「数字 → String」，所以 1 会先被转成 "1"，再由 @Pattern("on|off") 拒绝。
        // 结果仍是 400 + 40001，符合题目「status 只能是 on/off」的要求。
        mockMvc.perform(patch("/api/products/{id}/status", id)
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("非法状态时商品状态不变")
    void invalidStatusDoesNotMutate() throws Exception {
        long id = createProduct("测试商品", 1990, 10, "文具");
        setStatus(id, "off");

        mockMvc.perform(patch("/api/products/{id}/status", id)
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("bad")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(jsonPath("$.data.status").value("off"));
    }

    // ================= 参数类型 / 商品不存在 =================

    @Test
    @DisplayName("上下架：id 非数字 → 400 / 40001")
    void statusNonNumericId() throws Exception {
        mockMvc.perform(patch("/api/products/{id}/status", "abc")
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("off")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("上下架：商品不存在 → 404 / 40401")
    void statusProductNotFound() throws Exception {
        mockMvc.perform(patch("/api/products/{id}/status", 999999)
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("off")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value(containsString("商品不存在")));
    }

    @Test
    @DisplayName("上下架：非法 JSON → 400 / 40002")
    void statusMalformedJson() throws Exception {
        long id = createProduct("测试商品", 1990, 10, "文具");

        mockMvc.perform(patch("/api/products/{id}/status", id)
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    @DisplayName("未新增删除 / 改价接口：DELETE 与 PUT 均为 405")
    void noDeleteOrPriceEndpoints() throws Exception {
        long id = createProduct("测试商品", 1990, 10, "文具");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/products/{id}", id))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/products/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":100}"))
                .andExpect(status().isMethodNotAllowed());
    }

    // ================= helpers =================

    private long createProduct(String title, long price, int stock, String category) throws Exception {
        Map<String, Object> body = Map.of(
                "title", title,
                "price", price,
                "stock", stock,
                "category", category,
                "sellerName", "校园数码铺");
        String response = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/products")
                                .header("Authorization", AuthTestSupport.bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private void setStatus(long id, String status) throws Exception {
        mockMvc.perform(patch("/api/products/{id}/status", id)
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(status)))
                .andExpect(status().isOk());
    }

    private String body(String status) throws Exception {
        return objectMapper.writeValueAsString(Map.of("status", status));
    }
}
