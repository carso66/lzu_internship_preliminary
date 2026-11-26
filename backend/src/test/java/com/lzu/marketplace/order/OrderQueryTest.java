package com.lzu.marketplace.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/orders/{orderNo}（官方 §5 L1-7）与 GET /api/orders（L1-8）验收测试。
 *
 * <p>订单直接用 JDBC 插入：查询接口的验收不依赖下单接口，
 * 也能精确控制 created_at 来验证排序。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderQueryTest {

    private static final String PHONE_A = "13800138000";
    private static final String PHONE_B = "13900139000";
    private static final String PHONE_C = "13700137000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private long productId;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");

        jdbc.update("INSERT INTO products (title, price, stock, category, status, seller_name) " +
                    "VALUES ('测试商品', 1000, 9999, '图书', 'on', '校园数码铺')");
        productId = jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);
    }

    // ================= L1-7 详情 =================

    @Test
    @DisplayName("详情：存在 → 200，返回题目规定的订单字段")
    void detailFound() throws Exception {
        String orderNo = insertOrder("SO-A-1", PHONE_A, 1, "2026-11-24 10:00:00");

        mockMvc.perform(get("/api/orders/{orderNo}", orderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.productTitle").value("测试商品"))
                .andExpect(jsonPath("$.data.quantity").value(1))
                .andExpect(jsonPath("$.data.unitPrice").value(1000))
                .andExpect(jsonPath("$.data.totalAmount").value(1000))
                .andExpect(jsonPath("$.data.status").value("created"))
                .andExpect(jsonPath("$.data.buyerPhone").value(PHONE_A))
                .andExpect(jsonPath("$.data.address").value("兰州市城关区天水南路222号"))
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    @DisplayName("详情：订单号不存在 → 404 / 40401")
    void detailNotFound() throws Exception {
        mockMvc.perform(get("/api/orders/{orderNo}", "SO-NOT-EXIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value(containsString("订单不存在")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("详情：空库 → 404 / 40401")
    void detailOnEmptyTable() throws Exception {
        mockMvc.perform(get("/api/orders/{orderNo}", "SO-A-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("详情：SQL 注入式订单号按字面量处理，不报 500")
    void detailSqlInjectionLiteral() throws Exception {
        mockMvc.perform(get("/api/orders").param("buyerPhone", "' OR '1'='1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/orders/{orderNo}", "' OR '1'='1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    // ================= L1-8 手机号筛选 =================

    @Test
    @DisplayName("按 buyerPhone 筛选：只返回该买家的订单")
    void filterByBuyerPhone() throws Exception {
        insertOrder("SO-A-1", PHONE_A, 1, "2026-11-24 10:00:00");
        insertOrder("SO-A-2", PHONE_A, 1, "2026-11-24 11:00:00");
        insertOrder("SO-B-1", PHONE_B, 1, "2026-11-24 12:00:00");

        mockMvc.perform(get("/api/orders").param("buyerPhone", PHONE_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[*].buyerPhone",
                        contains(PHONE_A, PHONE_A)));
    }

    @Test
    @DisplayName("多个买家隔离：A 永远看不到 B 的任何订单")
    void buyerIsolation() throws Exception {
        insertOrder("SO-A-1", PHONE_A, 1, "2026-11-24 10:00:00");
        for (int i = 0; i < 5; i++) {
            insertOrder("SO-B-" + i, PHONE_B, 1, "2026-11-24 11:00:0" + i);
        }

        mockMvc.perform(get("/api/orders").param("buyerPhone", PHONE_A))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].orderNo").value("SO-A-1"));

        // 即使 pageSize 拉满，也不会混入他人订单
        mockMvc.perform(get("/api/orders")
                        .param("buyerPhone", PHONE_A)
                        .param("pageSize", "50"))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("手机号精确匹配：前缀相同但号码不同的买家不互相污染")
    void phoneExactMatchNotPrefix() throws Exception {
        insertOrder("SO-A-1", "13800138000", 1, "2026-11-24 10:00:00");
        insertOrder("SO-A-2", "13800138001", 1, "2026-11-24 11:00:00");

        mockMvc.perform(get("/api/orders").param("buyerPhone", "13800138000"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].orderNo").value("SO-A-1"));
    }

    @Test
    @DisplayName("空结果 → total=0，items 为空数组（不是 null）")
    void emptyResult() throws Exception {
        insertOrder("SO-A-1", PHONE_A, 1, "2026-11-24 10:00:00");

        mockMvc.perform(get("/api/orders").param("buyerPhone", PHONE_C))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    @DisplayName("空表 → total=0，items=[]")
    void emptyTable() throws Exception {
        mockMvc.perform(get("/api/orders").param("buyerPhone", PHONE_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    @DisplayName("缺少 buyerPhone → 400 / 40001，不退化成查全部")
    void missingBuyerPhone() throws Exception {
        insertOrder("SO-A-1", PHONE_A, 1, "2026-11-24 10:00:00");

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("buyerPhone")));
    }

    @Test
    @DisplayName("buyerPhone 为空串/纯空白 → 400 / 40001")
    void blankBuyerPhone() throws Exception {
        mockMvc.perform(get("/api/orders").param("buyerPhone", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(get("/api/orders").param("buyerPhone", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    // ================= 排序 =================

    @Test
    @DisplayName("按创建时间倒序：最新的在最前")
    void descendingByCreatedAt() throws Exception {
        insertOrder("SO-OLD", PHONE_A, 1, "2026-11-20 08:00:00");
        insertOrder("SO-NEW", PHONE_A, 1, "2026-11-24 08:00:00");
        insertOrder("SO-MID", PHONE_A, 1, "2026-11-22 08:00:00");

        mockMvc.perform(get("/api/orders").param("buyerPhone", PHONE_A))
                .andExpect(jsonPath("$.data.items[*].orderNo",
                        contains("SO-NEW", "SO-MID", "SO-OLD")));
    }

    @Test
    @DisplayName("同一时间戳 → 按 id 倒序稳定，翻页不重复不遗漏")
    void stableTieBreakBySameTimestamp() throws Exception {
        // 3 条完全相同的 created_at
        String sameTime = "2026-11-24 12:00:00";
        String first = insertOrder("SO-T-1", PHONE_A, 1, sameTime);
        String second = insertOrder("SO-T-2", PHONE_A, 1, sameTime);
        String third = insertOrder("SO-T-3", PHONE_A, 1, sameTime);

        // 时间并列时 id 大的在前 → 插入顺序的逆序
        mockMvc.perform(get("/api/orders").param("buyerPhone", PHONE_A))
                .andExpect(jsonPath("$.data.items[*].orderNo",
                        contains(third, second, first)));

        // 逐条翻页，收集所有 orderNo，断言既不重复也不遗漏
        List<String> paged = new java.util.ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            String body = mockMvc.perform(get("/api/orders")
                            .param("buyerPhone", PHONE_A)
                            .param("page", String.valueOf(page))
                            .param("pageSize", "1"))
                    .andExpect(jsonPath("$.data.total").value(3))
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andReturn().getResponse().getContentAsString();
            paged.add(new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(body).path("data").path("items").get(0).path("orderNo").asText());
        }
        assertEquals(List.of(third, second, first), paged, "翻页顺序必须与整体排序一致");
        assertEquals(3, paged.stream().distinct().count(), "翻页不应重复返回同一条");
    }

    // ================= 分页 =================

    @Test
    @DisplayName("默认分页：page=1 pageSize=10")
    void defaultPagination() throws Exception {
        for (int i = 0; i < 12; i++) {
            insertOrder("SO-" + i, PHONE_A, 1, "2026-11-24 10:00:" + String.format("%02d", i));
        }

        mockMvc.perform(get("/api/orders").param("buyerPhone", PHONE_A))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.total").value(12))
                .andExpect(jsonPath("$.data.items", hasSize(10)));
    }

    @Test
    @DisplayName("分页：total 是筛选后的总数，不是当前页条数")
    void totalIsFilteredCount() throws Exception {
        for (int i = 0; i < 5; i++) {
            insertOrder("SO-A-" + i, PHONE_A, 1, "2026-11-24 10:00:0" + i);
        }
        for (int i = 0; i < 7; i++) {
            insertOrder("SO-B-" + i, PHONE_B, 1, "2026-11-24 11:00:0" + i);
        }

        mockMvc.perform(get("/api/orders")
                        .param("buyerPhone", PHONE_A)
                        .param("pageSize", "2"))
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.items", hasSize(2)));
    }

    @Test
    @DisplayName("分页：最后一页只剩部分记录")
    void lastPartialPage() throws Exception {
        for (int i = 0; i < 5; i++) {
            insertOrder("SO-" + i, PHONE_A, 1, "2026-11-24 10:00:0" + i);
        }

        mockMvc.perform(get("/api/orders")
                        .param("buyerPhone", PHONE_A)
                        .param("page", "3")
                        .param("pageSize", "2"))
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].orderNo").value("SO-0"));
    }

    @Test
    @DisplayName("超出末页 → 200，items=[]，total 仍正确")
    void beyondLastPage() throws Exception {
        insertOrder("SO-A-1", PHONE_A, 1, "2026-11-24 10:00:00");

        mockMvc.perform(get("/api/orders")
                        .param("buyerPhone", PHONE_A)
                        .param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(99))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    @DisplayName("分页边界：pageSize=1 与 50 都合法")
    void pageSizeBoundaries() throws Exception {
        insertOrder("SO-A-1", PHONE_A, 1, "2026-11-24 10:00:00");

        mockMvc.perform(get("/api/orders").param("buyerPhone", PHONE_A).param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(1));

        mockMvc.perform(get("/api/orders").param("buyerPhone", PHONE_A).param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(50));
    }

    @Test
    @DisplayName("分页非法值 → 400 / 40001（与商品列表同一套规则）")
    void invalidPagination() throws Exception {
        for (String[] kv : new String[][]{
                {"pageSize", "0"}, {"pageSize", "51"}, {"pageSize", "-1"},
                {"page", "0"}, {"page", "-1"}, {"page", "abc"}, {"pageSize", "2.5"}}) {
            mockMvc.perform(get("/api/orders")
                            .param("buyerPhone", PHONE_A)
                            .param(kv[0], kv[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001));
        }
    }

    @Test
    @DisplayName("page 极大不会整数溢出成 500")
    void hugePageNoOverflow() throws Exception {
        insertOrder("SO-A-1", PHONE_A, 1, "2026-11-24 10:00:00");

        mockMvc.perform(get("/api/orders")
                        .param("buyerPhone", PHONE_A)
                        .param("page", "2147483647")
                        .param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    // ================= helpers =================

    private String insertOrder(String orderNo, String buyerPhone, int quantity, String createdAt) {
        jdbc.update("INSERT INTO orders (order_no, product_id, product_title, quantity, unit_price, " +
                    "total_amount, buyer_name, buyer_phone, address, created_at) " +
                    "VALUES (?, ?, '测试商品', ?, 1000, ?, '张三', ?, " +
                    "'兰州市城关区天水南路222号', ?)",
                orderNo, productId, quantity, 1000L * quantity, buyerPhone,
                Timestamp.valueOf(LocalDateTime.parse(createdAt.replace(' ', 'T'))));
        return orderNo;
    }
}
