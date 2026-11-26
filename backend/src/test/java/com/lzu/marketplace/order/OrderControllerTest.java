package com.lzu.marketplace.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:order-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderControllerTest {

    private static final String PHONE_A = "13800138000";
    private static final String PHONE_B = "13900139000";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsOrderWithServerCalculatedAmountAndReducesStock() throws Exception {
        long productId = insertProduct(4990, 10, "on");

        JsonNode created = mapper.readTree(performCreate(validBody(productId, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.orderNo").value(matchesPattern("^SO\\d{12,}$")))
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.productTitle").value("测试商品"))
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andExpect(jsonPath("$.data.unitPrice").value(4990))
                .andExpect(jsonPath("$.data.totalAmount").value(9980))
                .andExpect(jsonPath("$.data.status").value("created"))
                .andExpect(jsonPath("$.data.buyerName").value("张三"))
                .andExpect(jsonPath("$.data.buyerPhone").value(PHONE_A))
                .andExpect(jsonPath("$.data.address").value("兰州市城关区天水南路222号"))
                .andExpect(jsonPath("$.data.paidAt").value(nullValue()))
                .andReturn().getResponse().getContentAsString()).get("data");

        assertThat(created.get("id").asLong()).isPositive();
        assertThat(OffsetDateTime.parse(created.get("createdAt").asText())
                .getOffset().getTotalSeconds()).isZero();
        assertThat(stockOf(productId)).isEqualTo(8);
        assertThat(orderCount()).isEqualTo(1);
    }

    @Test
    void ignoresClientSuppliedPriceStatusAndOrderNumber() throws Exception {
        long productId = insertProduct(4990, 10, "on");
        ObjectNode body = validBody(productId, 2);
        body.put("unitPrice", 1);
        body.put("totalAmount", 1);
        body.put("status", "paid");
        body.put("orderNo", "HACKED");

        JsonNode created = mapper.readTree(performCreate(body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.unitPrice").value(4990))
                .andExpect(jsonPath("$.data.totalAmount").value(9980))
                .andExpect(jsonPath("$.data.status").value("created"))
                .andExpect(jsonPath("$.data.orderNo").value(not("HACKED")))
                .andExpect(jsonPath("$.data.paidAt").value(nullValue()))
                .andReturn().getResponse().getContentAsString()).get("data");

        assertThat(created.get("orderNo").asText()).startsWith("SO");
    }

    @Test
    void trimsBuyerFields() throws Exception {
        long productId = insertProduct(1000, 5, "on");
        ObjectNode body = validBody(productId, 1);
        body.put("buyerName", "  张三  ");
        body.put("buyerPhone", "  13800138000  ");
        body.put("address", "  兰州市城关区天水南路222号  ");

        performCreate(body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.buyerName").value("张三"))
                .andExpect(jsonPath("$.data.buyerPhone").value("13800138000"))
                .andExpect(jsonPath("$.data.address").value("兰州市城关区天水南路222号"));
    }

    @Test
    void rejectsZeroOrNegativeQuantity() throws Exception {
        long productId = insertProduct(1000, 10, "on");
        for (int quantity : new int[]{0, -1}) {
            ObjectNode body = validBody(productId, quantity);
            expectBadRequest(performCreate(body))
                    .andExpect(jsonPath("$.message", containsString("quantity")));
        }
        assertThat(stockOf(productId)).isEqualTo(10);
        assertThat(orderCount()).isZero();
    }

    @Test
    void rejectsNonIntegerQuantity() throws Exception {
        long productId = insertProduct(1000, 10, "on");

        ObjectNode stringQuantity = validBody(productId, 1);
        stringQuantity.put("quantity", "2");
        expectBadRequest(performCreate(stringQuantity))
                .andExpect(jsonPath("$.message", containsString("quantity")));

        ObjectNode decimalQuantity = validBody(productId, 1);
        decimalQuantity.put("quantity", 2.5);
        expectBadRequest(performCreate(decimalQuantity))
                .andExpect(jsonPath("$.message", containsString("quantity")));

        ObjectNode booleanQuantity = validBody(productId, 1);
        booleanQuantity.put("quantity", true);
        expectBadRequest(performCreate(booleanQuantity))
                .andExpect(jsonPath("$.message", containsString("quantity")));

        ObjectNode nullQuantity = validBody(productId, 1);
        nullQuantity.putNull("quantity");
        expectBadRequest(performCreate(nullQuantity))
                .andExpect(jsonPath("$.message", containsString("quantity")));

        assertThat(stockOf(productId)).isEqualTo(10);
        assertThat(orderCount()).isZero();
    }

    @Test
    void rejectsMissingQuantity() throws Exception {
        long productId = insertProduct(1000, 10, "on");
        ObjectNode body = validBody(productId, 1);
        body.remove("quantity");
        expectBadRequest(performCreate(body))
                .andExpect(jsonPath("$.message", containsString("quantity")));
    }

    @Test
    void rejectsInvalidProductId() throws Exception {
        for (Object productId : new Object[]{0L, -1L, "3"}) {
            ObjectNode body = validBody(1, 1);
            if (productId instanceof Long value) {
                body.put("productId", value);
            } else {
                body.put("productId", (String) productId);
            }
            expectBadRequest(performCreate(body))
                    .andExpect(jsonPath("$.message", containsString("productId")));
        }

        ObjectNode missing = validBody(1, 1);
        missing.remove("productId");
        expectBadRequest(performCreate(missing))
                .andExpect(jsonPath("$.message", containsString("productId")));
    }

    @Test
    void rejectsBlankBuyerFields() throws Exception {
        long productId = insertProduct(1000, 10, "on");

        ObjectNode blankName = validBody(productId, 1);
        blankName.put("buyerName", "   ");
        expectBadRequest(performCreate(blankName))
                .andExpect(jsonPath("$.message", containsString("buyerName")));

        ObjectNode blankPhone = validBody(productId, 1);
        blankPhone.put("buyerPhone", "");
        expectBadRequest(performCreate(blankPhone))
                .andExpect(jsonPath("$.message", containsString("buyerPhone")));

        ObjectNode blankAddress = validBody(productId, 1);
        blankAddress.put("address", "  ");
        expectBadRequest(performCreate(blankAddress))
                .andExpect(jsonPath("$.message", containsString("address")));
    }

    @Test
    void rejectsOverlongBuyerFields() throws Exception {
        long productId = insertProduct(1000, 10, "on");

        ObjectNode longName = validBody(productId, 1);
        longName.put("buyerName", "名".repeat(101));
        expectBadRequest(performCreate(longName))
                .andExpect(jsonPath("$.message", containsString("buyerName")));

        ObjectNode longPhone = validBody(productId, 1);
        longPhone.put("buyerPhone", "1".repeat(21));
        expectBadRequest(performCreate(longPhone))
                .andExpect(jsonPath("$.message", containsString("buyerPhone")));

        ObjectNode longAddress = validBody(productId, 1);
        longAddress.put("address", "址".repeat(501));
        expectBadRequest(performCreate(longAddress))
                .andExpect(jsonPath("$.message", containsString("address")));
    }

    @Test
    void returnsNotFoundWhenProductMissing() throws Exception {
        expectNotFound(performCreate(validBody(999_999L, 1)), "商品");
        assertThat(orderCount()).isZero();
    }

    @Test
    void rejectsOffShelfProduct() throws Exception {
        long productId = insertProduct(1000, 10, "off");

        expectConflict(performCreate(validBody(productId, 1)), 40902, "商品已下架");
        assertThat(stockOf(productId)).isEqualTo(10);
        assertThat(orderCount()).isZero();
    }

    @Test
    void rejectsInsufficientStock() throws Exception {
        long productId = insertProduct(1000, 1, "on");

        expectConflict(performCreate(validBody(productId, 2)), 40901, "库存不足");
        assertThat(stockOf(productId)).isEqualTo(1);
        assertThat(orderCount()).isZero();
    }

    @Test
    void rejectsAmountOverflow() throws Exception {
        long productId = insertProduct(100_000_000_000L, 1_000_000_000, "on");

        expectBadRequest(performCreate(validBody(productId, 1_000_000_000)))
                .andExpect(jsonPath("$.message", containsString("quantity")));
        assertThat(stockOf(productId)).isEqualTo(1_000_000_000);
        assertThat(orderCount()).isZero();
    }

    @Test
    void generatesUniqueOrderNumbers() throws Exception {
        long productId = insertProduct(1000, 10, "on");

        JsonNode first = mapper.readTree(performCreate(validBody(productId, 1))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("data");
        JsonNode second = mapper.readTree(performCreate(validBody(productId, 1))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("data");

        assertThat(first.get("orderNo").asText()).matches("^SO\\d{12,}$");
        assertThat(second.get("orderNo").asText()).matches("^SO\\d{12,}$");
        assertThat(first.get("orderNo").asText()).isNotEqualTo(second.get("orderNo").asText());
        assertThat(stockOf(productId)).isEqualTo(8);
        assertThat(orderCount()).isEqualTo(2);
    }

    @Test
    void getsOrderByOrderNo() throws Exception {
        long productId = insertProduct(4990, 10, "on");
        JsonNode created = createOrder(productId, 2, PHONE_A);

        mvc.perform(get("/api/orders/{orderNo}", created.get("orderNo").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(created.get("id").asLong()))
                .andExpect(jsonPath("$.data.orderNo").value(created.get("orderNo").asText()))
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andExpect(jsonPath("$.data.unitPrice").value(4990))
                .andExpect(jsonPath("$.data.totalAmount").value(9980))
                .andExpect(jsonPath("$.data.status").value("created"))
                .andExpect(jsonPath("$.data.buyerPhone").value(PHONE_A))
                .andExpect(jsonPath("$.data.paidAt").value(nullValue()));
    }

    @Test
    void returnsNotFoundForUnknownOrderNo() throws Exception {
        expectNotFound(mvc.perform(get("/api/orders/{orderNo}", "SO99999999999999")), "订单");
    }

    @Test
    void returnsNotFoundForOverlongOrderNo() throws Exception {
        expectNotFound(mvc.perform(get("/api/orders/{orderNo}", "X".repeat(65))), "订单");
    }

    @Test
    void listsOrdersByBuyerPhoneNewestFirst() throws Exception {
        long productId = insertProduct(1000, 20, "on");
        long firstA = createOrder(productId, 1, PHONE_A).get("id").asLong();
        createOrder(productId, 1, PHONE_B);
        long secondA = createOrder(productId, 2, PHONE_A).get("id").asLong();

        mvc.perform(get("/api/orders").param("buyerPhone", PHONE_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(secondA))
                .andExpect(jsonPath("$.data.items[1].id").value(firstA))
                .andExpect(jsonPath("$.data.items[0].buyerPhone").value(PHONE_A))
                .andExpect(jsonPath("$.data.items[1].buyerPhone").value(PHONE_A));
    }

    @Test
    void paginatesOrders() throws Exception {
        long productId = insertProduct(1000, 20, "on");
        long first = createOrder(productId, 1, PHONE_A).get("id").asLong();
        long second = createOrder(productId, 1, PHONE_A).get("id").asLong();

        mvc.perform(get("/api/orders")
                        .param("buyerPhone", PHONE_A)
                        .param("page", "1")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(second));

        mvc.perform(get("/api/orders")
                        .param("buyerPhone", PHONE_A)
                        .param("page", "2")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(first));
    }

    @Test
    void returnsEmptyListForUnknownPhone() throws Exception {
        long productId = insertProduct(1000, 10, "on");
        createOrder(productId, 1, PHONE_A);

        mvc.perform(get("/api/orders").param("buyerPhone", PHONE_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void usesExactPhoneMatch() throws Exception {
        long productId = insertProduct(1000, 10, "on");
        createOrder(productId, 1, PHONE_A);

        mvc.perform(get("/api/orders").param("buyerPhone", "1380013800%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void rejectsMissingBuyerPhone() throws Exception {
        expectBadRequest(mvc.perform(get("/api/orders")))
                .andExpect(jsonPath("$.message", containsString("buyerPhone")));
    }

    @Test
    void rejectsOverlongBuyerPhone() throws Exception {
        expectBadRequest(mvc.perform(get("/api/orders").param("buyerPhone", "1".repeat(21))))
                .andExpect(jsonPath("$.message", containsString("buyerPhone")));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "page|0", "page|-1", "page|abc", "page|1.5",
            "pageSize|0", "pageSize|51", "pageSize|abc", "pageSize|1.5"
    }, delimiter = '|')
    void rejectsInvalidListPagination(String field, String value) throws Exception {
        expectBadRequest(mvc.perform(get("/api/orders")
                        .param("buyerPhone", PHONE_A)
                        .param(field, value)))
                .andExpect(jsonPath("$.message", containsString(field)));
    }

    private ResultActions performCreate(ObjectNode body) throws Exception {
        return mvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    private JsonNode createOrder(long productId, int quantity, String buyerPhone) throws Exception {
        ObjectNode body = validBody(productId, quantity);
        body.put("buyerPhone", buyerPhone);
        return mapper.readTree(performCreate(body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("data");
    }

    private ObjectNode validBody(long productId, int quantity) {
        ObjectNode body = mapper.createObjectNode();
        body.put("productId", productId);
        body.put("quantity", quantity);
        body.put("buyerName", "张三");
        body.put("buyerPhone", PHONE_A);
        body.put("address", "兰州市城关区天水南路222号");
        return body;
    }

    private long insertProduct(long price, int stock, String status) {
        jdbc.update("""
                        INSERT INTO products (title, price, stock, category, seller_name, status)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                "测试商品", price, stock, "文具", "测试店铺", status);
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);
        assertThat(id).isNotNull();
        return id;
    }

    private int stockOf(long productId) {
        Integer stock = jdbc.queryForObject(
                "SELECT stock FROM products WHERE id = ?", Integer.class, productId);
        assertThat(stock).isNotNull();
        return stock;
    }

    private long orderCount() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
        return count == null ? 0 : count;
    }

    private ResultActions expectBadRequest(ResultActions actions) throws Exception {
        return actions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private void expectNotFound(ResultActions actions, String messagePart) throws Exception {
        actions.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message", containsString(messagePart)))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private void expectConflict(ResultActions actions, int code, String message) throws Exception {
        actions.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
