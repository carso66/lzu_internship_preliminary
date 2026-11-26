package com.lzu.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// MockMvc 经过实际的控制器、校验和服务层，并把商品写入独立的 H2 测试库。
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:marketplace-product-test;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
// 测试结束自动回滚，因此可以检查每次非法请求都没有留下商品数据。
@Transactional
class ProductCreateTest {

    private static final String VALID_PRODUCT = """
            {"title":"测试商品","price":1990,"stock":10,"category":"文具",
             "description":"测试备注","sellerName":"测试商家"}
            """;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void createsProductAndReturnsPersistedValues() throws Exception {
        String response = mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PRODUCT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.title").value("测试商品"))
                .andExpect(jsonPath("$.data.price").value(1990))
                .andExpect(jsonPath("$.data.stock").value(10))
                .andExpect(jsonPath("$.data.status").value("on"))
                .andExpect(jsonPath("$.data.sellerName").value("测试商家"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        // 不只检查响应：还按返回的 ID 查询数据库，确认商品确实入库。
        JsonNode data = mapper.readTree(response).get("data");
        long id = data.get("id").asLong();
        assertTrue(id > 0);
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id = ?", Long.class, id));
        assertEquals(1990L, jdbc.queryForObject("SELECT price FROM products WHERE id = ?", Long.class, id));
        assertEquals("测试备注", jdbc.queryForObject("SELECT description FROM products WHERE id = ?", String.class, id));
    }

    @Test
    void supportsLongIntegerPricesZeroStockAndOptionalDescription() throws Exception {
        // 验证允许的边界：大整数价格、零库存，以及不填写描述。
        ObjectNode input = (ObjectNode) mapper.readTree(VALID_PRODUCT);
        input.put("price", 3_000_000_000L);
        input.put("stock", 0);
        input.remove("description");
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(input.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.price").value(3_000_000_000L))
                .andExpect(jsonPath("$.data.stock").value(0))
                .andExpect(jsonPath("$.data.description").value(""));
        assertEquals(3_000_000_000L, jdbc.queryForObject("SELECT price FROM products", Long.class));
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void rejectsInvalidFieldsWithoutInserting(String field, String jsonValue) throws Exception {
        // 每次只替换一个字段，让失败原因可以明确对应到该字段。
        ObjectNode input = (ObjectNode) mapper.readTree(VALID_PRODUCT);
        input.set(field, mapper.readTree(jsonValue));
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(input.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message", containsString(field)))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM products", Long.class));
    }

    static Stream<Arguments> invalidFields() {
        // 参数化测试分别覆盖缺值、空白、长度、类型、小数和整数溢出。
        return Stream.of(
                Arguments.of("title", "null"), Arguments.of("title", "\"   \""),
                Arguments.of("title", "\"" + "a".repeat(121) + "\""), Arguments.of("title", "123"),
                Arguments.of("price", "null"), Arguments.of("price", "0"), Arguments.of("price", "-1"),
                Arguments.of("price", "\"1990\""), Arguments.of("price", "19.90"),
                Arguments.of("price", "9223372036854775808"), Arguments.of("price", "true"),
                Arguments.of("stock", "null"), Arguments.of("stock", "-1"),
                Arguments.of("stock", "\"10\""), Arguments.of("stock", "1.5"),
                Arguments.of("stock", "2147483648"),
                Arguments.of("category", "\"\""), Arguments.of("category", "false"),
                Arguments.of("category", "\"" + "a".repeat(61) + "\""),
                Arguments.of("description", "\"" + "a".repeat(2001) + "\""),
                Arguments.of("sellerName", "\"\""),
                Arguments.of("sellerName", "\"" + "a".repeat(61) + "\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "price", "stock", "category", "sellerName"})
    void rejectsMissingRequiredFields(String field) throws Exception {
        ObjectNode input = (ObjectNode) mapper.readTree(VALID_PRODUCT);
        input.remove(field);
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(input.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message", containsString(field)));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM products", Long.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "null", "[]"})
    void rejectsMissingOrMalformedBody(String body) throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM products", Long.class));
    }
}
