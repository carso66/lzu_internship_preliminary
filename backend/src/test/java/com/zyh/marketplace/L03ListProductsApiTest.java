package com.zyh.marketplace;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L0-3 GET /api/products —— 列表、筛选与分页。
 * 数据集：id 1..12，category 交替「文具/数码」，id=6 为 off；
 * created_at 随 id 递增，因此按时间倒序时 id 大的在前，排序断言可确定。
 */
class L03ListProductsApiTest extends ApiTestSupport {

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @BeforeEach
    void setUp() {
        truncateProducts();
        for (int i = 1; i <= 12; i++) {
            String category = (i % 2 == 0) ? "数码" : "文具";
            String status = (i == 6) ? "off" : "on";
            String title = "商品" + String.format("%02d", i) + (i % 2 == 0 ? "耳机" : "笔记本");
            insertProductDirectly(title, 1000L * i, i, category, status, "卖家",
                    "2026-01-01 00:00:" + String.format("%02d", i));
        }
    }

    @Test
    @DisplayName("L0-3 默认分页：page=1 pageSize=10，total=12，items 10 条，data 四个字段齐全")
    void defaultPaging() throws Exception {
        listProducts(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(12))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.items", hasSize(10)));
    }

    @Test
    @DisplayName("L0-3 自定义分页：page=2&pageSize=5，total 仍为 12，items 5 条")
    void customPaging() throws Exception {
        listProducts("page=2&pageSize=5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(12))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(5))
                .andExpect(jsonPath("$.data.items", hasSize(5)));
    }

    @Test
    @DisplayName("L0-3 跨页查询：pageSize=5 共 3 页，id 不重叠且并集为全部 12 条")
    void crossPageNoOverlap() throws Exception {
        Set<Integer> ids = new TreeSet<>();
        int count = 0;
        for (int p = 1; p <= 3; p++) {
            String body = listProducts("page=" + p + "&pageSize=5")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(12))
                    .andReturn().getResponse().getContentAsString();
            List<Integer> pageIds = JsonPath.parse(body).read("$.data.items[*].id");
            count += pageIds.size();
            ids.addAll(pageIds);
        }
        assertThat(count).isEqualTo(12);
        assertThat(ids).hasSize(12);
    }

    @Test
    @DisplayName("L0-3 稳定排序：created_at 倒序（同时间再按 id 倒序）")
    void stableOrdering() throws Exception {
        listProducts("pageSize=3")
                .andExpect(jsonPath("$.data.items[0].id").value(12))
                .andExpect(jsonPath("$.data.items[1].id").value(11))
                .andExpect(jsonPath("$.data.items[2].id").value(10));
    }

    @Test
    @DisplayName("L0-3 keyword 标题模糊匹配（前缀 / 中段 / 后缀均可命中）")
    void keywordFuzzyMatch() throws Exception {
        listProductsWithParams("keyword", "耳机", "pageSize", "50")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(6));
        listProductsWithParams("keyword", "商品0", "pageSize", "50")
                .andExpect(jsonPath("$.data.total").value(9));
        listProductsWithParams("keyword", "商品12耳机")
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("L0-3 category 精确匹配 / status 精确匹配 / 不传表示全部")
    void categoryAndStatusFilters() throws Exception {
        listProductsWithParams("category", "文具", "pageSize", "50")
                .andExpect(jsonPath("$.data.total").value(6));
        listProductsWithParams("category", "文")
                .andExpect(jsonPath("$.data.total").value(0));
        listProducts("status=on&pageSize=50").andExpect(jsonPath("$.data.total").value(11));
        listProducts("status=off&pageSize=50").andExpect(jsonPath("$.data.total").value(1));
        listProducts("pageSize=50").andExpect(jsonPath("$.data.total").value(12));
    }

    @Test
    @DisplayName("L0-3 三条件叠加：total 为筛选后的总数，而不是当前页条数")
    void combinedFilters() throws Exception {
        listProductsWithParams("keyword", "笔记本", "category", "文具", "status", "on",
                "page", "1", "pageSize", "2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andExpect(jsonPath("$.data.items", hasSize(2)));

        listProductsWithParams("keyword", "商品06", "category", "数码", "status", "off")
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(6))
                .andExpect(jsonPath("$.data.items[0].status").value("off"));
    }

    @Test
    @DisplayName("L0-3 无匹配结果：total=0，items 为空数组（不是 null、不是 404）")
    void noMatchReturnsEmptyArray() throws Exception {
        listProducts("keyword=zzzz")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    @DisplayName("L0-3 超出有效页数：total 仍为筛选总数，items 为空数组")
    void pageBeyondLast() throws Exception {
        listProducts("page=99&pageSize=10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(12))
                .andExpect(jsonPath("$.data.page").value(99))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @ParameterizedTest(name = "pageSize={0} → 400")
    @ValueSource(strings = {"0", "-1", "51", "1000", "abc", "1.5", "+10", " 10"})
    @DisplayName("L0-3 非法 pageSize（0 / 负 / 51 / 超大 / 非数字 / 小数）→ 400 + 40001")
    void invalidPageSizeRejected(String pageSize) throws Exception {
        listProducts("pageSize=" + pageSize.replace(" ", "%20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("pageSize")));
    }

    @ParameterizedTest(name = "page={0} → 400")
    @ValueSource(strings = {"0", "-5", "abc", "1.5", "+1"})
    @DisplayName("L0-3 非法 page（0 / 负 / 非数字 / 小数）→ 400 + 40001")
    void invalidPageRejected(String page) throws Exception {
        listProducts("page=" + page)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("page")));
    }

    @Test
    @DisplayName("L0-3 空参数值 page= / pageSize= 按未传递处理，走默认值")
    void blankParamsFallBackToDefaults() throws Exception {
        listProducts("page=&pageSize=&keyword=&status=&category=")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.total").value(12));
    }

    @Test
    @DisplayName("L0-3 pageSize 边界 1 与 50 均可正常返回")
    void pageSizeBoundariesAccepted() throws Exception {
        listProducts("pageSize=1").andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(1)));
        listProducts("pageSize=50").andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(12)));
    }

    @Test
    @DisplayName("L0-3 非法 status（ON / unknown / on,off）→ 400 + 40001")
    void invalidStatusFilterRejected() throws Exception {
        for (String s : new String[]{"ON", "unknown", "on,off"}) {
            listProductsWithParams("status", s)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001))
                    .andExpect(jsonPath("$.message").value(containsString("status")));
        }
    }

    @Test
    @DisplayName("L0-3 分页偏移量：page=10000000 可查询；超上限 / 超 int 均 400 而不是 500")
    void offsetLimits() throws Exception {
        listProducts("page=10000000&pageSize=50")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(12))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
        listProducts("page=10000001")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        listProducts("page=9007199254740991&pageSize=50")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        listProducts("page=2147483648")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }
}