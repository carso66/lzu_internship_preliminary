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

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 下单参数校验的补充验收（官方 §5 L1-6）。
 *
 * <p>本类只覆盖 {@link OrderCreateTest} 未覆盖的三类缺口：
 * <ol>
 *   <li><b>校验顺序</b>：题目把校验步骤固定为「quantity → 商品存在 → 在架 → 库存」，
 *       组合错误必须按顺序短路。例如 quantity=0 且商品不存在时应是 400，
 *       而不是先查商品得到 404；商品下架且库存不足时应是 40902 而不是 40901。</li>
 *   <li><b>productId 的类型与取值</b>：字符串 {@code "3"}、小数 {@code 2.0}、
 *       {@code 0}、负数、null、缺失一律 400 + 40001，不能被静默强转。</li>
 *   <li><b>buyerName / buyerPhone / address</b> 的长度、空白与首尾空格归一化。</li>
 * </ol>
 *
 * <p>每个用例前清空三张表，数据互不依赖、与执行顺序无关。
 * 不使用测试级事务：被测事务必须真实提交/回滚，测试级事务会掩盖提交边界。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderValidationTest {

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

    // ================= 校验顺序：quantity 最先 =================

    @Test
    @DisplayName("校验顺序：quantity=0 且商品不存在 → 400，不是 404")
    void quantityCheckedBeforeProductExistence() throws Exception {
        Map<String, Object> body = orderBody(999_999L, 0);
        expectBadRequest(body);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
    }

    @Test
    @DisplayName("校验顺序：quantity=-1 且商品不存在 → 400，不是 404")
    void negativeQuantityBeforeProductExistence() throws Exception {
        expectBadRequest(orderBody(999_999L, -1));
    }

    @Test
    @DisplayName("校验顺序：quantity=\"2\"（字符串）且商品不存在 → 400，不是 404")
    void stringQuantityBeforeProductExistence() throws Exception {
        expectBadRequest(orderBody(999_999L, "2"));
    }

    @Test
    @DisplayName("校验顺序：quantity=0 且商品已下架 → 400，不是 40902")
    void quantityCheckedBeforeOffShelf() throws Exception {
        long productId = insertProduct("下架商品", 1000, 10, "off");
        expectBadRequest(orderBody(productId, 0));
    }

    @Test
    @DisplayName("校验顺序：quantity=0 且库存不足 → 400，不是 40901")
    void quantityCheckedBeforeStock() throws Exception {
        long productId = insertProduct("库存紧张商品", 1000, 1, "on");
        expectBadRequest(orderBody(productId, 0));
    }

    @Test
    @DisplayName("校验顺序：商品已下架且库存为 0 → 40902（在架检查先于库存检查）")
    void offShelfCheckedBeforeStock() throws Exception {
        long productId = insertProduct("下架且无货", 1000, 0, "off");

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(json(orderBody(productId, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902))
                .andExpect(jsonPath("$.message").value("商品已下架"));
    }

    @Test
    @DisplayName("校验顺序：商品不存在且数量超库存 → 404（存在性先于库存）")
    void existenceCheckedBeforeStock() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(json(orderBody(999_999L, 5))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    // ================= productId 严格类型与取值 =================

    @Test
    @DisplayName("productId 为字符串 \"3\" → 400 / 40001（严格类型）")
    void productIdAsString() throws Exception {
        expectBadRequest(orderBody("3", 1));
    }

    @Test
    @DisplayName("productId 为小数 2.0 → 400 / 40001（严格类型）")
    void productIdAsDecimal() throws Exception {
        expectBadRequest(orderBody(2.0, 1));
    }

    @Test
    @DisplayName("productId=0 → 400 / 40001")
    void productIdZero() throws Exception {
        expectBadRequest(orderBody(0L, 1));
    }

    @Test
    @DisplayName("productId=-1 → 400 / 40001")
    void productIdNegative() throws Exception {
        expectBadRequest(orderBody(-1L, 1));
    }

    @Test
    @DisplayName("productId=null → 400 / 40001")
    void productIdNull() throws Exception {
        expectBadRequest(orderBody(null, 1));
    }

    @Test
    @DisplayName("缺少 productId → 400 / 40001，message 含 productId")
    void productIdMissing() throws Exception {
        Map<String, Object> body = orderBody(1L, 1);
        body.remove("productId");

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("productId")));
    }

    // ================= 买家信息：长度与空白 =================

    @Test
    @DisplayName("buyerName 超过 64 字符 → 400 / 40001")
    void buyerNameTooLong() throws Exception {
        long productId = insertProduct("测试商品", 1000, 10, "on");
        Map<String, Object> body = orderBody(productId, 1);
        body.put("buyerName", "x".repeat(65));
        expectBadRequest(body);
    }

    @Test
    @DisplayName("buyerPhone 超过 20 字符 → 400 / 40001")
    void buyerPhoneTooLong() throws Exception {
        long productId = insertProduct("测试商品", 1000, 10, "on");
        Map<String, Object> body = orderBody(productId, 1);
        body.put("buyerPhone", "1".repeat(21));
        expectBadRequest(body);
    }

    @Test
    @DisplayName("address 超过 255 字符 → 400 / 40001")
    void addressTooLong() throws Exception {
        long productId = insertProduct("测试商品", 1000, 10, "on");
        Map<String, Object> body = orderBody(productId, 1);
        body.put("address", "x".repeat(256));
        expectBadRequest(body);
    }

    @Test
    @DisplayName("buyerName 为纯空白 → 400 / 40001")
    void buyerNameBlank() throws Exception {
        long productId = insertProduct("测试商品", 1000, 10, "on");
        Map<String, Object> body = orderBody(productId, 1);
        body.put("buyerName", "   ");
        expectBadRequest(body);
    }

    @Test
    @DisplayName("buyerPhone 为纯空白 → 400 / 40001")
    void buyerPhoneBlank() throws Exception {
        long productId = insertProduct("测试商品", 1000, 10, "on");
        Map<String, Object> body = orderBody(productId, 1);
        body.put("buyerPhone", "");
        expectBadRequest(body);
    }

    @Test
    @DisplayName("address 为纯空白 → 400 / 40001")
    void addressBlank() throws Exception {
        long productId = insertProduct("测试商品", 1000, 10, "on");
        Map<String, Object> body = orderBody(productId, 1);
        body.put("address", "  ");
        expectBadRequest(body);
    }

    @Test
    @DisplayName("buyerName / buyerPhone / address 首尾空格被去除后落库")
    void buyerFieldsTrimmed() throws Exception {
        long productId = insertProduct("测试商品", 1000, 10, "on");
        Map<String, Object> body = orderBody(productId, 1);
        body.put("buyerName", "  张三  ");
        body.put("buyerPhone", "  13800138000  ");
        body.put("address", "  兰州市城关区天水南路222号  ");

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.buyerName").value("张三"))
                .andExpect(jsonPath("$.data.buyerPhone").value("13800138000"))
                .andExpect(jsonPath("$.data.address").value("兰州市城关区天水南路222号"));
    }

    // ================= helpers =================

    private long insertProduct(String title, long price, int stock, String status) {
        jdbc.update("INSERT INTO products (title, price, stock, category, status, seller_name) " +
                    "VALUES (?, ?, ?, '图书', ?, '校园数码铺')", title, price, stock, status);
        return jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);
    }

    private Map<String, Object> orderBody(Object productId, Object quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", productId);
        body.put("quantity", quantity);
        body.put("buyerName", "张三");
        body.put("buyerPhone", "13800138000");
        body.put("address", "兰州市城关区天水南路222号");
        return body;
    }

    private void expectBadRequest(Map<String, Object> body) throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }
}
