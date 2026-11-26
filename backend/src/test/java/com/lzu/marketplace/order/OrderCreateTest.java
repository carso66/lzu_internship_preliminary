package com.lzu.marketplace.order;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/orders（官方 §5 L1-6）验收测试。
 *
 * <p>不使用测试级事务（本类没有 {@code @Transactional}）：下单事务必须由
 * 被请求线程真正提交/回滚，测试级事务会把所有操作包进同一事务，
 * 掩盖「是否真的回滚」的问题。清理数据靠每个用例前 DELETE。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderCreateTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");
    }

    // ================= 成功 =================

    @Test
    @DisplayName("正常下单 → 201，库存减 quantity，订单落库")
    void createSuccess() throws Exception {
        long productId = insertProduct("考研数学复习全书", 4990, 10, "on");

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(productId, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.orderNo").isString())
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.productTitle").value("考研数学复习全书"))
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andExpect(jsonPath("$.data.unitPrice").value(4990))
                .andExpect(jsonPath("$.data.totalAmount").value(9980))
                .andExpect(jsonPath("$.data.status").value("created"))
                .andExpect(jsonPath("$.data.buyerPhone").value("13800138000"))
                .andExpect(jsonPath("$.data.createdAt").exists());

        // 真实数据库状态：库存 10 - 2 = 8，恰 1 条订单
        assertEquals(8, stockOf(productId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
    }

    @Test
    @DisplayName("金额由服务端按数据库价格计算：客户端传 price 被忽略")
    void clientPriceIgnored() throws Exception {
        long productId = insertProduct("测试商品", 1990, 10, "on");

        Map<String, Object> body = orderBodyAsMap(productId, 3);
        body.put("unitPrice", 1);        // 试图篡改单价
        body.put("totalAmount", 3);      // 试图篡改总额
        body.put("price", 1);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.unitPrice").value(1990))
                .andExpect(jsonPath("$.data.totalAmount").value(5970));

        Long storedTotal = jdbc.queryForObject(
                "SELECT total_amount FROM orders", Long.class);
        assertEquals(5970L, storedTotal, "库里的 total_amount 必须是服务端计算值");
    }

    @Test
    @DisplayName("订单号唯一：并发/重复下单生成不同 order_no")
    void orderNoUnique() throws Exception {
        long productId = insertProduct("测试商品", 1000, 10, "on");

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(productId, 1)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(productId, 1)))
                .andExpect(status().isCreated());

        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(DISTINCT order_no) FROM orders", Integer.class));
    }

    // ================= 业务失败 =================

    @Test
    @DisplayName("库存不足 → 409 / 40901，且库存与订单数不变")
    void insufficientStock() throws Exception {
        long productId = insertProduct("测试商品", 1000, 1, "on");

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(productId, 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.message").value("库存不足"));

        assertEquals(1, stockOf(productId), "库存不足时不能扣减");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
    }

    @Test
    @DisplayName("商品已下架 → 409 / 40902，库存不变")
    void productOffShelf() throws Exception {
        long productId = insertProduct("测试商品", 1000, 10, "off");

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(productId, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902))
                .andExpect(jsonPath("$.message").value("商品已下架"));

        assertEquals(10, stockOf(productId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
    }

    @Test
    @DisplayName("商品不存在 → 404 / 40401")
    void productNotFound() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(999_999L, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value("商品不存在"));
    }

    @Test
    @DisplayName("库存恰好等于购买数量时可以下单，扣完为 0")
    void exactStockBoundary() throws Exception {
        long productId = insertProduct("测试商品", 1000, 2, "on");

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(productId, 2)))
                .andExpect(status().isCreated());

        assertEquals(0, stockOf(productId));
    }

    // ================= quantity 类型与取值校验 =================

    @Test
    @DisplayName("quantity=0 → 400 / 40001")
    void quantityZero() throws Exception {
        long productId = insertProduct("测试商品", 1000, 10, "on");
        expectBadRequest(productId, "quantity", 0);
    }

    @Test
    @DisplayName("quantity=-1 → 400 / 40001")
    void quantityNegative() throws Exception {
        long productId = insertProduct("测试商品", 1000, 10, "on");
        expectBadRequest(productId, "quantity", -1);
    }

    @Test
    @DisplayName("quantity 为字符串 \"2\" → 400 / 40001")
    void quantityAsString() throws Exception {
        long productId = insertProduct("测试商品", 1000, 10, "on");
        expectBadRequest(productId, "quantity", "2");
    }

    @Test
    @DisplayName("quantity 为小数 2.5 → 400 / 40001")
    void quantityAsDecimal() throws Exception {
        long productId = insertProduct("测试商品", 1000, 10, "on");
        expectBadRequest(productId, "quantity", 2.5);
    }

    @Test
    @DisplayName("quantity 为整值小数 2.0 → 400 / 40001")
    void quantityAsWholeDecimal() throws Exception {
        long productId = insertProduct("测试商品", 1000, 10, "on");
        expectBadRequest(productId, "quantity", 2.0);
    }

    @Test
    @DisplayName("quantity 为 null → 400 / 40001")
    void quantityNull() throws Exception {
        long productId = insertProduct("测试商品", 1000, 10, "on");
        expectBadRequest(productId, "quantity", null);
    }

    @Test
    @DisplayName("缺少 quantity → 400 / 40001，message 含 quantity")
    void quantityMissing() throws Exception {
        long productId = insertProduct("测试商品", 1000, 10, "on");

        Map<String, Object> body = orderBodyAsMap(productId, 1);
        body.remove("quantity");

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("quantity")));
    }

    @Test
    @DisplayName("quantity 超出 int 范围 → 400 / 40001")
    void quantityOverflowInt() throws Exception {
        long productId = insertProduct("测试商品", 1000, 10, "on");

        Map<String, Object> body = orderBodyAsMap(productId, 1);
        body.put("quantity", 2147483648L);

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("缺少 buyerPhone / address → 400 / 40001")
    void missingRequiredFields() throws Exception {
        long productId = insertProduct("测试商品", 1000, 10, "on");

        Map<String, Object> noPhone = orderBodyAsMap(productId, 1);
        noPhone.remove("buyerPhone");
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noPhone)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        Map<String, Object> noAddress = orderBodyAsMap(productId, 1);
        noAddress.remove("address");
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noAddress)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("参数非法时没有任何副作用")
    void invalidRequestHasNoSideEffect() throws Exception {
        long productId = insertProduct("测试商品", 1000, 10, "on");
        expectBadRequest(productId, "quantity", 0);

        assertEquals(10, stockOf(productId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
    }

    // ================= helpers =================

    private long insertProduct(String title, long price, int stock, String status) {
        jdbc.update("INSERT INTO products (title, price, stock, category, status, seller_name) " +
                    "VALUES (?, ?, ?, '图书', ?, '校园数码铺')", title, price, stock, status);
        return jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);
    }

    private int stockOf(long productId) {
        return jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
    }

    private Map<String, Object> orderBodyAsMap(long productId, Object quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", productId);
        body.put("quantity", quantity);
        body.put("buyerName", "张三");
        body.put("buyerPhone", "13800138000");
        body.put("address", "兰州市城关区天水南路222号");
        return body;
    }

    private String orderBody(long productId, int quantity) throws Exception {
        return objectMapper.writeValueAsString(orderBodyAsMap(productId, quantity));
    }

    private void expectBadRequest(long productId, String field, Object value) throws Exception {
        Map<String, Object> body = orderBodyAsMap(productId, value);
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }
}
