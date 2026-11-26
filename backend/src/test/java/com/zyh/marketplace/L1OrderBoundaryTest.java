package com.zyh.marketplace;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L1 边界与不变量：订单号长度 / 客户端改价 / 跨页稳定排序（对应审查项，非新功能）。
 */
class L1OrderBoundaryTest extends ApiTestSupport {

    private long productId;

    @BeforeEach
    void setUp() {
        truncateProducts();
        productId = insertProductDirectly("边界商品", 1990L, 20, "图书", "on", "店铺", "2026-01-01 10:00:00");
    }

    private String orderJson(long id, int quantity) {
        return "{\"productId\":" + id + ",\"quantity\":" + quantity + ",\"buyerName\":\"张三\","
                + "\"buyerPhone\":\"13800138000\",\"address\":\"a\"}";
    }

    @Test
    @DisplayName("审查：订单号长度恒在 VARCHAR(32) 内（SO + 14 位时间 + ≥6 位序号 → 22~32）且互不重复")
    void orderNoFitsColumnAndStaysUnique() throws Exception {
        List<String> orderNos = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String body = postOrder(orderJson(productId, 1)).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            String orderNo = JsonPath.read(body, "$.data.orderNo");
            assertThat(orderNo).matches("^SO\\d{20}$");
            assertThat(orderNo.length()).isBetween(22, 32);
            orderNos.add(orderNo);
        }
        assertThat(orderNos).doesNotHaveDuplicates();
        Integer distinct = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT order_no) FROM orders", Integer.class);
        assertThat(distinct).isEqualTo(8);
    }

    @Test
    @DisplayName("审查：客户端传 unitPrice / totalAmount / price 一律 40001，金额只能来自数据库")
    void clientCannotOverrideAmount() throws Exception {
        for (String extra : new String[]{"\"unitPrice\":1", "\"totalAmount\":1", "\"price\":1"}) {
            postOrder("{\"productId\":" + productId + ",\"quantity\":1,\"buyerName\":\"张三\","
                    + "\"buyerPhone\":\"13800138000\",\"address\":\"a\"," + extra + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();

        // 改价后下单：单价取改后的数据库值，客户端无法影响
        jdbcTemplate.update("UPDATE products SET price = 5000 WHERE id = ?", productId);
        postOrder(orderJson(productId, 2)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.unitPrice").value(5000))
                .andExpect(jsonPath("$.data.totalAmount").value(10000));
    }

    @Test
    @DisplayName("审查：历史订单使用快照价，商品改价不影响已存在订单的金额")
    void snapshotPriceIsFrozenInOrder() throws Exception {
        postOrder(orderJson(productId, 1)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.unitPrice").value(1990));
        jdbcTemplate.update("UPDATE products SET price = 99999 WHERE id = ?", productId);
        Long unitPrice = jdbcTemplate.queryForObject("SELECT unit_price FROM orders", Long.class);
        Long totalAmount = jdbcTemplate.queryForObject("SELECT total_amount FROM orders", Long.class);
        assertThat(unitPrice).isEqualTo(1990L);
        assertThat(totalAmount).isEqualTo(1990L);
    }

    @Test
    @DisplayName("审查：跨页 pageSize=1 逐页取 3 条，无重复、顺序为创建时间倒序（稳定）")
    void crossPageStableDescending() throws Exception {
        insertOrderDirectly("SO20260101000000000001", productId, "书", 1, 100L, "张三", "13800138000", "a1", "2026-01-01 10:00:00");
        insertOrderDirectly("SO20260101000000000002", productId, "书", 1, 100L, "张三", "13800138000", "a2", "2026-01-02 10:00:00");
        insertOrderDirectly("SO20260101000000000003", productId, "书", 1, 100L, "张三", "13800138000", "a3", "2026-01-03 10:00:00");

        List<String> seen = new ArrayList<>();
        for (int p = 1; p <= 3; p++) {
            String body = listOrders("buyerPhone", "13800138000", "page", String.valueOf(p), "pageSize", "1")
                    .andExpect(jsonPath("$.data.total").value(3))
                    .andReturn().getResponse().getContentAsString();
            List<String> nos = JsonPath.read(body, "$.data.items[*].orderNo");
            assertThat(nos).hasSize(1);
            seen.addAll(nos);
        }
        assertThat(seen).containsExactly(
                "SO20260101000000000003", "SO20260101000000000002", "SO20260101000000000001");
    }
}