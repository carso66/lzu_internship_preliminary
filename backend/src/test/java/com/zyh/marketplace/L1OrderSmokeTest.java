package com.zyh.marketplace;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L1-6 ~ L1-8 订单接口冒烟测试（第四阶段：基本路径，全量验收留待下一阶段）。
 * 覆盖：正常下单与服务端算价、库存扣减落库、404/40902/40901、参数校验、详情、列表分页排序。
 */
class L1OrderSmokeTest extends ApiTestSupport {

    private long productId;

    @BeforeEach
    void setUp() {
        truncateProducts();
        productId = insertProductDirectly("考研数学复习全书", 1990L, 10, "图书", "on", "校园书店",
                "2026-01-01 10:00:00");
    }

    private String orderJson(long productId, String quantity) {
        return "{\"productId\":" + productId + ",\"quantity\":" + quantity
                + ",\"buyerName\":\"张三\",\"buyerPhone\":\"13800138000\",\"address\":\"兰州市城关区天水南路222号\"}";
    }

    private int stockOf(long id) {
        Integer stock = jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, id);
        return stock == null ? -1 : stock;
    }

    @Test
    @DisplayName("L2-12 正常下单 quantity=1：201 + code 0，库存 10 → 9，订单落库且金额等于商品单价")
    void normalOrderDecreasesStockByOne() throws Exception {
        // 前置：setUp 已创建库存为 10、状态为 on 的商品
        assertThat(stockOf(productId)).as("下单前库存为 10").isEqualTo(10);

        String body = postOrder(orderJson(productId, "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.quantity").value(1))
                .andExpect(jsonPath("$.data.unitPrice").value(1990))
                .andExpect(jsonPath("$.data.totalAmount").value(1990))
                .andReturn().getResponse().getContentAsString();

        // 库存从 10 变为 9
        assertThat(stockOf(productId)).as("下单后库存减 1").isEqualTo(9);

        // 订单成功落库，且金额等于商品单价（quantity = 1）
        String orderNo = JsonPath.read(body, "$.data.orderNo");
        Integer orderRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE order_no = ?", Integer.class, orderNo);
        assertThat(orderRows).as("订单已落库").isEqualTo(1);

        Long unitPrice = jdbcTemplate.queryForObject(
                "SELECT unit_price FROM orders WHERE order_no = ?", Long.class, orderNo);
        Long totalAmount = jdbcTemplate.queryForObject(
                "SELECT total_amount FROM orders WHERE order_no = ?", Long.class, orderNo);
        Long productPrice = jdbcTemplate.queryForObject(
                "SELECT price FROM products WHERE id = ?", Long.class, productId);
        assertThat(unitPrice).as("单价等于商品价格").isEqualTo(productPrice).isEqualTo(1990L);
        assertThat(totalAmount).as("quantity=1 时总额等于单价").isEqualTo(unitPrice).isEqualTo(1990L);
    }

    @Test
    @DisplayName("L1-6 正常下单：201 + orderNo + 服务端算价（单价×数量）+ created + 快照 + 库存扣减落库")
    void createOrderHappyPath() throws Exception {
        String body = postOrder(orderJson(productId, "2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.productTitle").value("考研数学复习全书"))
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andExpect(jsonPath("$.data.unitPrice").value(1990))
                .andExpect(jsonPath("$.data.totalAmount").value(3980))
                .andExpect(jsonPath("$.data.status").value("created"))
                .andExpect(jsonPath("$.data.buyerName").value("张三"))
                .andExpect(jsonPath("$.data.buyerPhone").value("13800138000"))
                .andExpect(jsonPath("$.data.address").value("兰州市城关区天水南路222号"))
                .andExpect(jsonPath("$.data.paidAt").isEmpty())
                .andExpect(jsonPath("$.data.createdAt").isString())
                .andReturn().getResponse().getContentAsString();
        String orderNo = JsonPath.read(body, "$.data.orderNo");
        assertThat(orderNo).matches("SO\\d{20}");

        assertThat(stockOf(productId)).isEqualTo(8);
        Long unitPriceDb = jdbcTemplate.queryForObject("SELECT unit_price FROM orders WHERE order_no = ?",
                Long.class, orderNo);
        assertThat(unitPriceDb).isEqualTo(1990L);
        Integer orderRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        assertThat(orderRows).isEqualTo(1);
    }

    @Test
    @DisplayName("L1-6 连续下两单：订单号互不相同、库存累计扣减")
    void twoOrdersGetDistinctOrderNo() throws Exception {
        String first = postOrder(orderJson(productId, "1")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = postOrder(orderJson(productId, "1")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(first, "$.data.orderNo"))
                .isNotEqualTo((String) JsonPath.read(second, "$.data.orderNo"));
        assertThat(stockOf(productId)).isEqualTo(8);
    }

    @Test
    @DisplayName("L1-6 商品不存在 → 404 + 40401")
    void productNotFound() throws Exception {
        postOrder(orderJson(999999, "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("L1-6 商品已下架 → 409 + 40902，库存不变化")
    void productOffRejected() throws Exception {
        jdbcTemplate.update("UPDATE products SET status = 'off' WHERE id = ?", productId);
        postOrder(orderJson(productId, "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902))
                .andExpect(jsonPath("$.message").value(containsString("已下架")));
        assertThat(stockOf(productId)).isEqualTo(10);
    }

    @Test
    @DisplayName("L1-6 库存不足 → 409 + 40901，库存不变化、订单不落库")
    void insufficientStockRejected() throws Exception {
        postOrder(orderJson(productId, "11"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.message").value(containsString("库存不足")));
        assertThat(stockOf(productId)).isEqualTo(10);
        Integer orderRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        assertThat(orderRows).isZero();
    }

    @Test
    @DisplayName("L1-6 quantity 非法：0 / -1 / \"2\" / 1.5 / true / 缺失 → 400 + 40001")
    void invalidQuantityRejected() throws Exception {
        String[] cases = {
                orderJson(productId, "0"),
                orderJson(productId, "-1"),
                "{\"productId\":" + productId + ",\"quantity\":\"2\",\"buyerName\":\"张三\",\"buyerPhone\":\"13800138000\",\"address\":\"a\"}",
                "{\"productId\":" + productId + ",\"quantity\":1.5,\"buyerName\":\"张三\",\"buyerPhone\":\"13800138000\",\"address\":\"a\"}",
                "{\"productId\":" + productId + ",\"quantity\":true,\"buyerName\":\"张三\",\"buyerPhone\":\"13800138000\",\"address\":\"a\"}",
                "{\"productId\":" + productId + ",\"buyerName\":\"张三\",\"buyerPhone\":\"13800138000\",\"address\":\"a\"}"
        };
        for (String json : cases) {
            postOrder(json)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001))
                    .andExpect(jsonPath("$.data").isEmpty());
        }
        Integer orderRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        assertThat(orderRows).isZero();
    }

    @Test
    @DisplayName("L1-6 缺 buyerName / buyerPhone / address → 400 且指出字段")
    void missingBuyerFieldsRejected() throws Exception {
        postOrder("{\"productId\":" + productId + ",\"quantity\":1,\"buyerPhone\":\"13800138000\",\"address\":\"a\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("buyerName")));
        postOrder("{\"productId\":" + productId + ",\"quantity\":1,\"buyerName\":\"张三\",\"address\":\"a\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("buyerPhone")));
        postOrder("{\"productId\":" + productId + ",\"quantity\":1,\"buyerName\":\"张三\",\"buyerPhone\":\"13800138000\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("address")));
    }

    @Test
    @DisplayName("L1-6 buyerPhone 含非数字 / 超 20 位 → 400；productId 小数 → 400")
    void invalidBuyerFieldsRejected() throws Exception {
        postOrder("{\"productId\":" + productId + ",\"quantity\":1,\"buyerName\":\"张三\",\"buyerPhone\":\"138-0000\",\"address\":\"a\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("buyerPhone")));
        postOrder("{\"productId\":" + productId + ",\"quantity\":1,\"buyerName\":\"张三\",\"buyerPhone\":\"123456789012345678901\",\"address\":\"a\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        postOrder("{\"productId\":" + productId + ".5,\"quantity\":1,\"buyerName\":\"张三\",\"buyerPhone\":\"13800138000\",\"address\":\"a\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("L1-6 地址超过 200 字符 → 400")
    void addressTooLongRejected() throws Exception {
        String longAddress = "兰".repeat(201);
        postOrder("{\"productId\":" + productId + ",\"quantity\":1,\"buyerName\":\"张三\","
                + "\"buyerPhone\":\"13800138000\",\"address\":\"" + longAddress + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("address")));
    }

    @Test
    @DisplayName("L1-6 边界值：buyerName 64 / address 200 字符可用；65 / 201 拒绝")
    void lengthBoundaries() throws Exception {
        String name64 = "名".repeat(64);
        String addr200 = "址".repeat(200);
        postOrder("{\"productId\":" + productId + ",\"quantity\":1,\"buyerName\":\"" + name64 + "\","
                + "\"buyerPhone\":\"13800138000\",\"address\":\"" + addr200 + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.buyerName").value(name64));

        postOrder("{\"productId\":" + productId + ",\"quantity\":1,\"buyerName\":\"" + "名".repeat(65) + "\","
                + "\"buyerPhone\":\"13800138000\",\"address\":\"a\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("buyerName")));
    }

    @Test
    @DisplayName("L1-6 quantity 溢出：2147483648 / 超过 long → 400（不落库、不扣库存）")
    void quantityOverflowRejected() throws Exception {
        postOrder("{\"productId\":" + productId + ",\"quantity\":2147483648,\"buyerName\":\"张三\","
                + "\"buyerPhone\":\"13800138000\",\"address\":\"a\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("quantity")));
        postOrder("{\"productId\":" + productId + ",\"quantity\":99999999999999999999,\"buyerName\":\"张三\","
                + "\"buyerPhone\":\"13800138000\",\"address\":\"a\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        Integer stock = jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id = ?",
                Integer.class, productId);
        assertThat(stock).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    }

    @Test
    @DisplayName("L1-6 必填字段为 null / 空白 → 400 并指出字段")
    void nullAndBlankFieldsRejected() throws Exception {
        String[] bodies = {
                "{\"productId\":null,\"quantity\":1,\"buyerName\":\"张三\",\"buyerPhone\":\"13800138000\",\"address\":\"a\"}",
                "{\"productId\":" + productId + ",\"quantity\":1,\"buyerName\":\"   \",\"buyerPhone\":\"13800138000\",\"address\":\"a\"}",
                "{\"productId\":" + productId + ",\"quantity\":1,\"buyerName\":\"张三\",\"buyerPhone\":\"\",\"address\":\"a\"}",
                "{\"productId\":" + productId + ",\"quantity\":1,\"buyerName\":\"张三\",\"buyerPhone\":\"13800138000\",\"address\":\"  \"}"
        };
        for (String body : bodies) {
            postOrder(body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    }

    @Test
    @DisplayName("L1-6 未知字段 price/unitPrice（客户端试图指定价格）→ 400")
    void priceFieldIsNotAllowed() throws Exception {
        postOrder("{\"productId\":" + productId + ",\"quantity\":1,\"buyerName\":\"张三\","
                + "\"buyerPhone\":\"13800138000\",\"address\":\"a\",\"price\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("price")));
    }

    // --------------------------------------------------------------- L1-7 详情

    @Test
    @DisplayName("L1-7 按订单号查详情：完整字段；不存在 → 404 + 40401")
    void orderDetail() throws Exception {
        insertOrderDirectly("SO20260101000000000101", productId, "考研数学复习全书", 2, 1990L,
                "张三", "13800138000", "兰州市城关区天水南路222号", "2026-01-02 10:00:00");
        getOrder("SO20260101000000000101")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value("SO20260101000000000101"))
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.productTitle").value("考研数学复习全书"))
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andExpect(jsonPath("$.data.unitPrice").value(1990))
                .andExpect(jsonPath("$.data.totalAmount").value(3980))
                .andExpect(jsonPath("$.data.status").value("created"))
                .andExpect(jsonPath("$.data.buyerName").value("张三"))
                .andExpect(jsonPath("$.data.buyerPhone").value("13800138000"))
                .andExpect(jsonPath("$.data.paidAt").isEmpty())
                .andExpect(jsonPath("$.data.createdAt").value("2026-01-02 10:00:00"));

        getOrder("SOWRONG00000000000000")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    // --------------------------------------------------------------- L1-8 列表

    @Test
    @DisplayName("L1-8 按手机号查列表：创建时间倒序、total 为该手机号筛选总数、分页正确")
    void orderListByBuyerPhone() throws Exception {
        insertOrderDirectly("SO20260101000000000001", productId, "考研数学复习全书", 1, 1990L,
                "张三", "13800138000", "地址1", "2026-01-01 10:00:00");
        insertOrderDirectly("SO20260101000000000002", productId, "考研数学复习全书", 2, 1990L,
                "张三", "13800138000", "地址2", "2026-01-03 10:00:00");
        insertOrderDirectly("SO20260101000000000003", productId, "考研数学复习全书", 1, 1990L,
                "李四", "13900139000", "地址3", "2026-01-02 10:00:00");

        // 只统计该手机号的订单；倒序：最新在前
        listOrders("buyerPhone", "13800138000", "page", "1", "pageSize", "10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].orderNo").value("SO20260101000000000002"))
                .andExpect(jsonPath("$.data.items[1].orderNo").value("SO20260101000000000001"));

        // 第二页只剩一条
        listOrders("buyerPhone", "13800138000", "page", "2", "pageSize", "1")
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].orderNo").value("SO20260101000000000001"));

        // 其他手机号：total 0、items 空数组（不是 null）
        listOrders("buyerPhone", "13700137000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    @DisplayName("L1-8 缺 buyerPhone / 帧 pageSize=51 / 非数字 page → 400 + 40001")
    void orderListInvalidParams() throws Exception {
        listOrders("page", "1", "pageSize", "10")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("buyerPhone")));
        listOrders("buyerPhone", "13800138000", "pageSize", "51")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("pageSize")));
        listOrders("buyerPhone", "13800138000", "page", "abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("page")));
    }

    @Test
    @DisplayName("L1-8 默认分页（page=1、pageSize=10）与空参数回退")
    void orderListDefaultPaging() throws Exception {
        insertOrderDirectly("SO20260101000000000001", productId, "考研数学复习全书", 1, 1990L,
                "张三", "13800138000", "地址1", "2026-01-01 10:00:00");
        listOrders("buyerPhone", "13800138000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
        listOrders("buyerPhone", "13800138000", "page", "", "pageSize", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10));
    }

    @Test
    @DisplayName("L1-8 查询 buyerPhone 校验与下单一致：非数字 / 超 20 位均 400，下单成功的手机号必然查得到")
    void queryAndCreatePhoneRulesAreConsistent() throws Exception {
        // 查询侧同样拒绝非数字（与 create 一致）
        listOrders("buyerPhone", "138-0000-0000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("buyerPhone")));
        listOrders("buyerPhone", "123456789012345678901")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        // 下单侧接受后，用同一字符串查询必然命中（不存在「能下单却查不到」）
        postOrder(orderJson(productId, "1")).andExpect(status().isCreated());
        listOrders("buyerPhone", "13800138000")
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("L1-8 SQL 注入尝试：纯数字型 payload 被当字面量（total=0）；含引号的 payload 被手机号规则直接 400")
    void orderListSqlInjectionIsInert() throws Exception {
        insertOrderDirectly("SO20260101000000000001", productId, "书", 1, 1990L,
                "张三", "13800138000", "地址1", "2026-01-01 10:00:00");
        insertOrderDirectly("SO20260101000000000002", productId, "书", 1, 1990L,
                "李四", "13900139000", "地址2", "2026-01-02 10:00:00");

        // 含引号/分号/空格的 payload 先被「纯数字」校验拦下 → 400，根本到不了 SQL
        String[] rejected = {"' OR '1'='1", "13800138000' OR '1'='1", "'; DROP TABLE orders;--",
                "1 OR 1=1", "-1 OR 1=1"};
        for (String payload : rejected) {
            listOrders("buyerPhone", payload)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001));
        }

        // 纯数字 payload 进入 SQL 也只是字面量：查不到任何人（包含 1）
        listOrders("buyerPhone", "1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        listOrders("buyerPhone", "138001380001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        // 表仍在，两家订单都未受影响（未被越权查出、未被删除）
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("L1-8 超出有效页数：total 仍为筛选总数，items 为空数组")
    void orderListPageBeyondLast() throws Exception {
        insertOrderDirectly("SO20260101000000000001", productId, "书", 1, 1990L,
                "张三", "13800138000", "地址1", "2026-01-01 10:00:00");
        listOrders("buyerPhone", "13800138000", "page", "99", "pageSize", "10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(99))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }
}

