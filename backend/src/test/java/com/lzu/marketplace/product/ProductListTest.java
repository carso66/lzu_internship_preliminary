package com.lzu.marketplace.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/products（官方 §4 L0-3）验收测试。
 *
 * <p>固定种子：7 条商品，覆盖 2 个状态、3 个分类，便于验证组合筛选与分页。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductListTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");

        // 注意：DELETE 不会重置 H2 的自增序列，id 会跨测试方法持续增长。
        // 因此测试断言只用「相对顺序」和「筛选结果」，不硬编码具体 id 值。
        insert("降噪蓝牙耳机", 19900, "数码", "on");     // 插入第 1 条
        insert("考研数学复习全书", 4990, "图书", "on");   // 第 2 条
        insert("晨光中性笔 12 支装", 1999, "文具", "on"); // 第 3 条
        insert("二手显示器", 9999999, "数码", "off");    // 第 4 条
        insert("英语四级词汇书", 1990, "图书", "off");    // 第 5 条
        insert("校园文创贴纸", 199, "文具", "on");        // 第 6 条
        insert("考研英语真题", 3590, "图书", "on");       // 第 7 条（最新）
    }

    // ================= 基础列表 =================

    @Test
    @DisplayName("无筛选、无分页参数 → 默认 page=1 pageSize=10，返回全部 7 条")
    void defaultsApplied() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(7))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.items", hasSize(7)));
    }

    @Test
    @DisplayName("data 必须包含 total/page/pageSize/items 四个字段")
    void responseShape() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(jsonPath("$.data.total").exists())
                .andExpect(jsonPath("$.data.page").exists())
                .andExpect(jsonPath("$.data.pageSize").exists())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("排序确定：created_at DESC, id DESC → 后插入的在前")
    void deterministicOrder() throws Exception {
        // 7 条几乎同时插入，created_at 可能并列，靠 id DESC 兜底。
        // 用标题断言顺序，不依赖自增 id 的具体数值。
        mockMvc.perform(get("/api/products"))
                .andExpect(jsonPath("$.data.items[*].title", contains(
                        "考研英语真题",
                        "校园文创贴纸",
                        "英语四级词汇书",
                        "二手显示器",
                        "晨光中性笔 12 支装",
                        "考研数学复习全书",
                        "降噪蓝牙耳机")));
    }

    // ================= 单项筛选 =================

    @Test
    @DisplayName("keyword 模糊匹配标题")
    void keywordFilter() throws Exception {
        mockMvc.perform(get("/api/products").param("keyword", "考研"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[*].title",
                        contains("考研英语真题", "考研数学复习全书")));
    }

    @Test
    @DisplayName("keyword 为部分词也匹配（模=模糊）")
    void keywordPartial() throws Exception {
        mockMvc.perform(get("/api/products").param("keyword", "书"))
                .andExpect(jsonPath("$.data.total").value(2)); // 复习全书、词汇书
    }

    @Test
    @DisplayName("keyword 大小写不敏感")
    void keywordCaseInsensitive() throws Exception {
        jdbc.update("INSERT INTO products (title, price, stock, category, status, seller_name) " +
                    "VALUES ('USB Cable', 1000, 5, '数码', 'on', 's')");
        mockMvc.perform(get("/api/products").param("keyword", "usb"))
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/products").param("keyword", "USB"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("keyword 中的 % 按字面量处理，不会匹配所有商品")
    void keywordPercentEscaped() throws Exception {
        mockMvc.perform(get("/api/products").param("keyword", "%"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("keyword 中的 _ 按字面量处理")
    void keywordUnderscoreEscaped() throws Exception {
        jdbc.update("INSERT INTO products (title, price, stock, category, status, seller_name) " +
                    "VALUES ('a_b', 1000, 5, '数码', 'on', 's')");
        mockMvc.perform(get("/api/products").param("keyword", "a_b"))
                .andExpect(jsonPath("$.data.total").value(1));
        // 若未转义，'a_b' 会匹配 'axb'；这里插入 axb 应不被匹配
        jdbc.update("INSERT INTO products (title, price, stock, category, status, seller_name) " +
                    "VALUES ('axb', 1000, 5, '数码', 'on', 's')");
        mockMvc.perform(get("/api/products").param("keyword", "a_b"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("status=on 只返回上架商品")
    void statusOn() throws Exception {
        mockMvc.perform(get("/api/products").param("status", "on"))
                .andExpect(jsonPath("$.data.total").value(5));
    }

    @Test
    @DisplayName("status=off 只返回下架商品")
    void statusOff() throws Exception {
        mockMvc.perform(get("/api/products").param("status", "off"))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    @DisplayName("status 省略 → 全部状态")
    void statusOmitted() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(jsonPath("$.data.total").value(7));
    }

    @Test
    @DisplayName("category 精确匹配")
    void categoryFilter() throws Exception {
        mockMvc.perform(get("/api/products").param("category", "图书"))
                .andExpect(jsonPath("$.data.total").value(3));
    }

    @Test
    @DisplayName("category 是精确匹配，不做模糊")
    void categoryExactNotFuzzy() throws Exception {
        mockMvc.perform(get("/api/products").param("category", "图"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // ================= 组合筛选 =================

    @Test
    @DisplayName("keyword + status 叠加")
    void keywordAndStatus() throws Exception {
        mockMvc.perform(get("/api/products").param("keyword", "考研").param("status", "on"))
                .andExpect(jsonPath("$.data.total").value(2));
        mockMvc.perform(get("/api/products").param("keyword", "考研").param("status", "off"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("status + category 叠加")
    void statusAndCategory() throws Exception {
        mockMvc.perform(get("/api/products").param("status", "off").param("category", "图书"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("英语四级词汇书"));
    }

    @Test
    @DisplayName("三个条件同时叠加")
    void allThreeFilters() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("keyword", "考研")
                        .param("status", "on")
                        .param("category", "图书"))
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/products")
                        .param("keyword", "考研")
                        .param("status", "on")
                        .param("category", "数码"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // ================= 分页 =================

    @Test
    @DisplayName("pageSize=3 第 1 页：最新的 3 条，total 仍是 7")
    void firstPage() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "1").param("pageSize", "3"))
                .andExpect(jsonPath("$.data.total").value(7))
                .andExpect(jsonPath("$.data.items", hasSize(3)))
                .andExpect(jsonPath("$.data.items[*].title", contains(
                        "考研英语真题", "校园文创贴纸", "英语四级词汇书")));
    }

    @Test
    @DisplayName("pageSize=3 第 2 页：接下来的 3 条")
    void secondPage() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "2").param("pageSize", "3"))
                .andExpect(jsonPath("$.data.total").value(7))
                .andExpect(jsonPath("$.data.items", hasSize(3)))
                .andExpect(jsonPath("$.data.items[*].title", contains(
                        "二手显示器", "晨光中性笔 12 支装", "考研数学复习全书")));
    }

    @Test
    @DisplayName("pageSize=3 第 3 页：只剩 1 条")
    void lastPartialPage() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "3").param("pageSize", "3"))
                .andExpect(jsonPath("$.data.total").value(7))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].title").value("降噪蓝牙耳机"));
    }

    @Test
    @DisplayName("超出末页 → 空数组，total 仍正确（不是 0）")
    void beyondLastPage() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "99").param("pageSize", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(7))
                .andExpect(jsonPath("$.data.page").value(99))
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("pageSize 边界：1 和 50 均合法")
    void pageSizeBoundaries() throws Exception {
        mockMvc.perform(get("/api/products").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)));

        mockMvc.perform(get("/api/products").param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(50))
                .andExpect(jsonPath("$.data.items", hasSize(7)));
    }

    @Test
    @DisplayName("分页 + 筛选：total 是筛选后总数，不是全表数")
    void paginationWithFilterTotal() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("status", "on")
                        .param("page", "1")
                        .param("pageSize", "2"))
                .andExpect(jsonPath("$.data.total").value(5))   // 筛选后 5 条
                .andExpect(jsonPath("$.data.items", hasSize(2))); // 当前页 2 条
    }

    // ================= 空结果 =================

    @Test
    @DisplayName("无匹配 → total=0，items 为空数组（不是 null）")
    void emptyResult() throws Exception {
        mockMvc.perform(get("/api/products").param("keyword", "不存在的商品"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    @DisplayName("空表 → total=0，items=[]")
    void emptyTable() throws Exception {
        jdbc.update("DELETE FROM products");
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    // ================= 非法参数 =================

    @Test
    @DisplayName("status 非 on/off → 400 / 40001")
    void invalidStatus() throws Exception {
        mockMvc.perform(get("/api/products").param("status", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("status")));
    }

    @Test
    @DisplayName("status 大小写敏感：ON 不合法")
    void statusCaseSensitive() throws Exception {
        mockMvc.perform(get("/api/products").param("status", "ON"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("pageSize=0 → 400 / 40001，不做夹断")
    void pageSizeZero() throws Exception {
        mockMvc.perform(get("/api/products").param("pageSize", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("pageSize")));
    }

    @Test
    @DisplayName("pageSize=51 → 400 / 40001，不做夹断")
    void pageSizeOverMax() throws Exception {
        mockMvc.perform(get("/api/products").param("pageSize", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("pageSize")));
    }

    @Test
    @DisplayName("pageSize 为负数 → 400 / 40001")
    void pageSizeNegative() throws Exception {
        mockMvc.perform(get("/api/products").param("pageSize", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("page=0 → 400 / 40001")
    void pageZero() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("page")));
    }

    @Test
    @DisplayName("page 为负数 → 400 / 40001")
    void pageNegative() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("page 非数字 → 400 / 40001")
    void pageNotNumber() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("pageSize 为小数 → 400 / 40001")
    void pageSizeDecimal() throws Exception {
        mockMvc.perform(get("/api/products").param("pageSize", "2.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("keyword 空串视为未传，返回全部")
    void blankKeywordIgnored() throws Exception {
        mockMvc.perform(get("/api/products").param("keyword", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(7));
    }

    @Test
    @DisplayName("category 空串视为未传")
    void blankCategoryIgnored() throws Exception {
        mockMvc.perform(get("/api/products").param("category", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(7));
    }

    @Test
    @DisplayName("超长 keyword 不会报 500")
    void veryLongKeyword() throws Exception {
        mockMvc.perform(get("/api/products").param("keyword", "x".repeat(500)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // ================= helpers =================

    private void insert(String title, long price, String category, String status) {
        jdbc.update("INSERT INTO products (title, price, stock, category, status, seller_name) " +
                    "VALUES (?, ?, 10, ?, ?, '校园数码铺')", title, price, category, status);
    }
}
