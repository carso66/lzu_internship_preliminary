package com.zyh.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L0-4 GET /api/products/{id} —— 商品详情。
 */
class L04ProductDetailApiTest extends ApiTestSupport {

    private long onId;
    private long offId;

    @BeforeEach
    void setUp() {
        truncateProducts();
        onId = insertProductDirectly("上架商品", 1990L, 10, "文具", "on", "校园数码铺", "2026-01-01 10:00:00");
        offId = insertProductDirectly("下架商品", 2990L, 3, "数码", "off", "数码铺", "2026-01-01 11:00:00");
    }

    @Test
    @DisplayName("L0-4 上架商品：200 + 完整字段（含 description/createdAt）")
    void detailOfOnProduct() throws Exception {
        getProduct(String.valueOf(onId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.id").value(onId))
                .andExpect(jsonPath("$.data.title").value("上架商品"))
                .andExpect(jsonPath("$.data.price").value(1990))
                .andExpect(jsonPath("$.data.stock").value(10))
                .andExpect(jsonPath("$.data.category").value("文具"))
                .andExpect(jsonPath("$.data.status").value("on"))
                .andExpect(jsonPath("$.data.sellerName").value("校园数码铺"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-01-01 10:00:00"));
    }

    @Test
    @DisplayName("L0-4 下架商品仍可查询详情，status=off")
    void detailOfOffProduct() throws Exception {
        getProduct(String.valueOf(offId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(offId))
                .andExpect(jsonPath("$.data.status").value("off"));
    }

    @Test
    @DisplayName("L0-4 不存在的商品 → 404 + 40401")
    void detailNotFound() throws Exception {
        getProduct("999999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.data").isEmpty());
        getProduct("0")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @ParameterizedTest(name = "非法 ID [{0}] → 400")
    @ValueSource(strings = {"abc", "1.5", "1e3"})
    @DisplayName("L0-4 非法 ID（非数字 / 小数 / 科学计数）→ 400 + 40001，而不是 500")
    void detailInvalidId(String id) throws Exception {
        getProduct(id)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("id")));
    }

    @Test
    @DisplayName("L0-4 ID 超出 BIGINT 范围 → 400 + 40001")
    void detailIdOverflow() throws Exception {
        getProduct("99999999999999999999")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("L0-4 负 ID → 404（合法整数但不存在）")
    void detailNegativeIdNotFound() throws Exception {
        getProduct("-1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }
}