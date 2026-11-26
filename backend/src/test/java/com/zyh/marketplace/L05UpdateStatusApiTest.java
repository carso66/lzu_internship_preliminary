package com.zyh.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L0-5 PATCH /api/products/{id}/status —— 上下架。
 * 同时验证「状态改动后详情与列表同步变化」。
 */
class L05UpdateStatusApiTest extends ApiTestSupport {

    private long id;

    @BeforeEach
    void setUp() {
        truncateProducts();
        id = insertProductDirectly("可上下架商品", 1990L, 5, "文具", "on", "店铺", "2026-01-01 10:00:00");
    }

    @Test
    @DisplayName("L0-5 正常下架：200 + data 只含 id/status；详情与列表同步为 off")
    void takeOff() throws Exception {
        patchStatus(String.valueOf(id), "{\"status\":\"off\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("off"))
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data.length()").value(2));

        getProduct(String.valueOf(id)).andExpect(jsonPath("$.data.status").value("off"));
        listProducts("status=off").andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(id));
        listProducts("status=on").andExpect(jsonPath("$.data.total").value(0));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, id))
                .isEqualTo("off");
    }

    @Test
    @DisplayName("L0-5 重新上架：off → on，详情与列表同步回 on")
    void putBackOn() throws Exception {
        patchStatus(String.valueOf(id), "{\"status\":\"off\"}").andExpect(status().isOk());
        patchStatus(String.valueOf(id), "{\"status\":\"on\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("on"));

        getProduct(String.valueOf(id)).andExpect(jsonPath("$.data.status").value("on"));
        listProducts("status=on").andExpect(jsonPath("$.data.total").value(1));
        listProducts("status=off").andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("L0-5 重复设置相同状态（on→on、off→off）幂等，均返回 200")
    void idempotentSameStatus() throws Exception {
        patchStatus(String.valueOf(id), "{\"status\":\"on\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("on"));
        patchStatus(String.valueOf(id), "{\"status\":\"off\"}").andExpect(status().isOk());
        patchStatus(String.valueOf(id), "{\"status\":\"off\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("off"));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, id))
                .isEqualTo("off");
    }

    @ParameterizedTest(name = "status=[{0}] → 400")
    @ValueSource(strings = {"", " ", "ON", "Off", "sold", "unknown", "on,off", "1", "true"})
    @DisplayName("L0-5 非法状态 → 400 + 40001，且库中状态不变")
    void invalidStatusRejected(String status) throws Exception {
        patchStatus(String.valueOf(id), "{\"status\":\"" + status + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("status")));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, id))
                .isEqualTo("on");
    }

    @Test
    @DisplayName("L0-5 status 为非字符串（数字 / null / 布尔）→ 400")
    void nonStringStatusRejected() throws Exception {
        patchStatus(String.valueOf(id), "{\"status\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        patchStatus(String.valueOf(id), "{\"status\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        patchStatus(String.valueOf(id), "{\"status\":false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @ParameterizedTest(name = "请求体 [{0}] → 400")
    @ValueSource(strings = {"", "{}", "not json", "[1]", "{\"status\":\"off\",}", "{\"s\":\"off\"}"})
    @DisplayName("L0-5 缺少 status / 空体 / 非法 JSON / 字段拼错 → 400 + 40001")
    void badBodyRejected(String body) throws Exception {
        patchStatus(String.valueOf(id), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("L0-5 不存在的商品 → 404 + 40401")
    void productNotFound() throws Exception {
        patchStatus("999999", "{\"status\":\"off\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("L0-5 不存在的商品 + 非法状态：先校验参数 → 400")
    void invalidStatusCheckedBeforeExistence() throws Exception {
        patchStatus("999999", "{\"status\":\"bad\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("L0-5 非法商品 ID（非数字）→ 400 + 40001")
    void invalidIdRejected() throws Exception {
        patchStatus("abc", "{\"status\":\"off\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("L0-5 只影响目标商品：多商品时仅一条状态变化")
    void onlyTargetProductAffected() throws Exception {
        long other = insertProductDirectly("另一个商品", 100L, 1, "文具", "on", "店铺", "2026-01-01 09:00:00");
        patchStatus(String.valueOf(id), "{\"status\":\"off\"}").andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM products WHERE id = ?", String.class, other))
                .isEqualTo("on");
        listProducts("status=off").andExpect(jsonPath("$.data.items", hasSize(1)));
        listProducts("status=on").andExpect(jsonPath("$.data.items", hasSize(1)));
    }
}