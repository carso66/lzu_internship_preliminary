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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品发布 / 列表的补充验收（官方 §4 L0-2、L0-3）。
 *
 * <p>{@link ProductCreateTest} 已覆盖「越界即拒绝」；本类补两类缺口：
 * <ol>
 *   <li><b>边界值恰好合法</b>：title=50、price=999999999、stock=999、
 *       category=32、sellerName=64、description=200 均应为 201。
 *       只测越界不测边界，无法证明「上限」这个数字本身是对的。</li>
 *   <li><b>分页全量遍历</b>：逐页翻完，断言每页之间不重复、不遗漏，
 *       拼起来与整体排序完全一致。单页断言无法发现翻页重复/漏行。</li>
 * </ol>
 *
 * <p>另补关键词首尾空格归一化与列表成功响应的统一外层。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    /** L2-11：发布接口需要 token，用例前登录一次（本类两个直接 POST 用例用它）。 */
    private String token;

    @BeforeEach
    void clean() throws Exception {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");
        token = AuthTestSupport.login(mockMvc, objectMapper);
    }

    // ================= 边界值：恰好在上限 =================

    @Test
    @DisplayName("各字段恰好等于上限 → 201，原样落库")
    void exactUpperBoundsAccepted() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "x".repeat(50));
        body.put("price", 999_999_999L);
        body.put("stock", 999);
        body.put("category", "c".repeat(32));
        body.put("sellerName", "s".repeat(64));
        body.put("description", "d".repeat(200));

        mockMvc.perform(post("/api/products")
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.price").value(999_999_999L))
                .andExpect(jsonPath("$.data.stock").value(999));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT LENGTH(title) AS tl, LENGTH(description) AS dl FROM products");
        assertEquals(50, ((Number) row.get("TL")).intValue());
        assertEquals(200, ((Number) row.get("DL")).intValue());
    }

    @Test
    @DisplayName("price=1（下限）与 stock=0（下限）→ 201")
    void lowerBoundsAccepted() throws Exception {
        Map<String, Object> body = baseBody();
        body.put("price", 1);
        body.put("stock", 0);

        mockMvc.perform(post("/api/products")
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.price").value(1))
                .andExpect(jsonPath("$.data.stock").value(0));
    }

    // ================= 分页全量遍历 =================

    @Test
    @DisplayName("pageSize=2 逐页翻完：4 页覆盖全部 7 条，不重复不遗漏，顺序与整体一致")
    void paginationCoversAllItemsExactlyOnce() throws Exception {
        for (int i = 1; i <= 7; i++) {
            insertProduct("商品" + i, i);
        }
        // 整体顺序（created_at DESC, id DESC）：最后插入的在前
        List<String> expected = List.of(
                "商品7", "商品6", "商品5", "商品4", "商品3", "商品2", "商品1");

        List<String> collected = new ArrayList<>();
        for (int page = 1; page <= 4; page++) {
            String body = mockMvc.perform(get("/api/products")
                            .param("page", String.valueOf(page))
                            .param("pageSize", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(7))
                    .andReturn().getResponse().getContentAsString();
            var items = objectMapper.readTree(body).path("data").path("items");
            for (var item : items) {
                collected.add(item.path("title").asText());
            }
        }

        assertEquals(expected, collected, "逐页拼起来的结果必须与整体排序一致");
        assertEquals(7, collected.stream().distinct().count(), "翻页不应重复返回同一条");
    }

    @Test
    @DisplayName("pageSize=50（上限）一次取回全部")
    void pageSizeMaxReturnsAll() throws Exception {
        for (int i = 0; i < 12; i++) {
            insertProduct("商品" + i, i);
        }

        mockMvc.perform(get("/api/products").param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(50))
                .andExpect(jsonPath("$.data.items", hasSize(12)));
    }

    // ================= 关键词归一化与列表外层 =================

    @Test
    @DisplayName("keyword 首尾空格被去除后仍能匹配")
    void keywordTrimmed() throws Exception {
        insertProduct("考研数学", 1);
        insertProduct("英语词汇", 2);

        mockMvc.perform(get("/api/products").param("keyword", "  考研  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[*].title", contains("考研数学")));
    }

    @Test
    @DisplayName("列表成功响应：统一外层 code=0 / message=ok / data 四字段齐全")
    void listEnvelope() throws Exception {
        insertProduct("测试商品", 1);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.page").isNumber())
                .andExpect(jsonPath("$.data.pageSize").isNumber())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    // ================= helpers =================

    private Map<String, Object> baseBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "测试商品");
        body.put("price", 1990);
        body.put("stock", 10);
        body.put("category", "文具");
        body.put("sellerName", "校园数码铺");
        return body;
    }

    private void insertProduct(String title, int seq) {
        jdbc.update("INSERT INTO products (title, price, stock, category, status, seller_name) " +
                    "VALUES (?, 1000, 10, '图书', 'on', '校园数码铺')", title);
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }
}
