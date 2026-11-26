package com.zyh.marketplace;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 回归：健康检查、统一响应结构、SQL 注入与 LIKE 特殊字符、错误响应不泄露内部信息。
 */
class CommonApiRegressionTest extends ApiTestSupport {

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @BeforeEach
    void setUp() {
        truncateProducts();
        insertProductDirectly("普通商品", 1990L, 1, "文具", "on", "店铺", "2026-01-01 10:00:00");
    }

    @Test
    @DisplayName("回归 /api/health 返回 {code:0,message:ok,data:null}")
    void healthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("回归 未知路径 → 404 + 40401 且仍是统一结构")
    void unknownPathUnifiedBody() throws Exception {
        mockMvc.perform(get("/api/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("回归 PATCH 到不存在的子资源 → 404 + 40401（统一结构）")
    void patchUnknownSubResourceIs404() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/products/1/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("回归 不支持的方法 → 405 + 40004（统一结构）")
    void methodNotAllowedUnifiedBody() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(40004));
    }

    @Test
    @DisplayName("回归 成功响应都包含且仅包含 code/message/data 三个顶层字段")
    void successResponsesHaveThreeTopLevelFields() throws Exception {
        for (String uri : new String[]{"/api/health", "/api/products", "/api/products/1"}) {
            String body = mockMvc.perform(get(uri)).andReturn().getResponse().getContentAsString();
            java.util.Map<String, Object> root = JsonPath.<java.util.Map<String, Object>>parse(body).read("$");
            assertThat(root)
                    .as(uri)
                    .containsKeys("code", "message", "data")
                    .hasSize(3);
        }
    }

    @Test
    @DisplayName("回归 SQL 注入尝试：payload 只被当作普通字符串，不破坏表、不绕过筛选")
    void sqlInjectionAttemptIsInert() throws Exception {
        String[] payloads = {"' OR '1'='1", "'; DROP TABLE products;--", "1 OR 1=1", "%' OR '1'='1"};
        for (String payload : payloads) {
            listProductsWithParams("keyword", payload).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(0));
            listProductsWithParams("category", payload).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(0));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class)).isEqualTo(1);

        String injectedTitle = "x'); DROP TABLE products;--";
        postProduct("{\"title\":\"" + injectedTitle + "\",\"price\":1,\"stock\":1,"
                + "\"category\":\"c\",\"sellerName\":\"s\"}")
                .andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class)).isEqualTo(2);
        listProductsWithParams("keyword", injectedTitle)
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value(injectedTitle));
    }

    @Test
    @DisplayName("回归 LIKE 特殊字符：keyword 中的 % _ \\ 只按字面量匹配")
    void likeSpecialCharsAreEscaped() throws Exception {
        insertProductDirectly("五折50%促销", 100L, 1, "文具", "on", "店铺", "2026-01-01 11:00:00");
        insertProductDirectly("型号A_1", 100L, 1, "文具", "on", "店铺", "2026-01-01 12:00:00");
        insertProductDirectly("反斜杠A\\B", 100L, 1, "文具", "on", "店铺", "2026-01-01 13:00:00");

        listProductsWithParams("keyword", "%")
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("五折50%促销"));
        listProductsWithParams("keyword", "_")
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("型号A_1"));
        listProductsWithParams("keyword", "\\")
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("反斜杠A\\B"));
        listProductsWithParams("keyword", "50%").andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("回归 中文 keyword 正常编码时可命中")
    void chineseKeywordWorks() throws Exception {
        listProductsWithParams("keyword", "普通").andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("回归 错误响应不泄露堆栈 / SQL / 异常类名")
    void errorResponsesDoNotLeakInternals() throws Exception {
        String[] bodies = {
                mockMvc.perform(get("/api/products/abc")).andReturn().getResponse().getContentAsString(),
                mockMvc.perform(get("/api/products/999999")).andReturn().getResponse().getContentAsString(),
                postProduct("not json").andReturn().getResponse().getContentAsString(),
                postProduct("{\"title\":\"t\",\"price\":19.9,\"stock\":1,\"category\":\"c\",\"sellerName\":\"s\"}")
                        .andReturn().getResponse().getContentAsString()
        };
        for (String body : bodies) {
            assertThat(body)
                    .doesNotContain("Exception")
                    .doesNotContain("org.springframework")
                    .doesNotContain("jdbc")
                    .doesNotContain("SELECT")
                    .doesNotContain("at com.zyh");
        }
    }
}