package com.lzu.marketplace.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:product-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsProductWithDatabaseDefaultsAndReadsItBack() throws Exception {
        ObjectNode body = validBody();
        body.put("title", "  测试商品  ");
        body.put("category", " 文具 ");
        body.put("sellerName", " 校园数码铺 ");
        body.put("id", 999999);
        body.put("status", "off");
        body.put("createdAt", "2000-01-01T00:00:00Z");

        JsonNode created = mapper.readTree(mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.title").value("测试商品"))
                .andExpect(jsonPath("$.data.category").value("文具"))
                .andExpect(jsonPath("$.data.sellerName").value("校园数码铺"))
                .andExpect(jsonPath("$.data.price").value(1990))
                .andExpect(jsonPath("$.data.stock").value(10))
                .andExpect(jsonPath("$.data.description").value("备注"))
                .andExpect(jsonPath("$.data.status").value("on"))
                .andReturn().getResponse().getContentAsString()).get("data");

        long id = created.get("id").asLong();
        assertThat(id).isPositive().isNotEqualTo(999999);
        assertThat(OffsetDateTime.parse(created.get("createdAt").asText()).getOffset().getTotalSeconds())
                .isZero();
        assertThat(jdbc.queryForObject("SELECT title FROM products WHERE id = ?", String.class, id))
                .isEqualTo("测试商品");
        JsonNode detail = mapper.readTree(mvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString()).get("data");
        assertThat(detail).isEqualTo(created);
    }

    @Test
    void acceptsZeroValuesAndOptionalDescription() throws Exception {
        ObjectNode body = validBody();
        body.put("price", 0);
        body.put("stock", 0);
        body.remove("description");
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.price").value(0))
                .andExpect(jsonPath("$.data.stock").value(0))
                .andExpect(jsonPath("$.data.description").value(nullValue()));
    }

    @Test
    void treatsBlankDescriptionAsNull() throws Exception {
        ObjectNode body = validBody().put("description", "   ");
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.description").value(nullValue()));
        assertThat(jdbc.queryForObject("SELECT description FROM products", String.class)).isNull();
    }

    @Test
    void storesPricesBeyondIntegerRangeExactly() throws Exception {
        ObjectNode body = validBody();
        long price = 9_999_999_999L;
        body.put("price", price);
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.price").value(price));
        assertThat(jdbc.queryForObject("SELECT price FROM products", Long.class)).isEqualTo(price);
    }

    @ParameterizedTest
    @MethodSource("invalidProductFields")
    void rejectsInvalidFieldsWithoutWritingData(String field, String jsonValue) throws Exception {
        ObjectNode body = validBody();
        body.set(field, mapper.readTree(jsonValue));
        expectBadRequest(mvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON).content(body.toString())))
                .andExpect(jsonPath("$.message", containsString(field)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products", Long.class)).isZero();
    }

    static Stream<Arguments> invalidProductFields() {
        return Stream.of(
                Arguments.of("title", "null"),
                Arguments.of("title", "\"   \""),
                Arguments.of("title", "\"" + "字".repeat(121) + "\""),
                Arguments.of("title", "123"),
                Arguments.of("title", "true"),
                Arguments.of("price", "null"),
                Arguments.of("price", "-1"),
                Arguments.of("price", "\"1990\""),
                Arguments.of("price", "1.9"),
                Arguments.of("price", "true"),
                Arguments.of("price", "100000000001"),
                Arguments.of("price", "9223372036854775808"),
                Arguments.of("stock", "null"),
                Arguments.of("stock", "-1"),
                Arguments.of("stock", "\"2\""),
                Arguments.of("stock", "1.0"),
                Arguments.of("stock", "false"),
                Arguments.of("stock", "2147483648"),
                Arguments.of("category", "null"),
                Arguments.of("category", "\" \""),
                Arguments.of("category", "\"" + "字".repeat(51) + "\""),
                Arguments.of("sellerName", "null"),
                Arguments.of("sellerName", "\" \""),
                Arguments.of("sellerName", "\"" + "字".repeat(101) + "\""),
                Arguments.of("description", "\"" + "字".repeat(2001) + "\""),
                Arguments.of("description", "123")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "price", "stock", "category", "sellerName"})
    void rejectsMissingRequiredFields(String field) throws Exception {
        ObjectNode body = validBody();
        body.remove(field);
        expectBadRequest(mvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON).content(body.toString())))
                .andExpect(jsonPath("$.message", containsString(field)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "null", "[]"})
    void rejectsMissingOrMalformedBody(String body) throws Exception {
        expectBadRequest(mvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    @Test
    void returnsNotFoundForMissingProduct() throws Exception {
        mvc.perform(get("/api/products/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.data").value(nullValue()));
        mvc.perform(patch("/api/products/{id}/status", Long.MAX_VALUE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"off\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "9223372036854775808"})
    void rejectsInvalidPathIds(String id) throws Exception {
        expectBadRequest(mvc.perform(get("/api/products/{id}", id)));
        expectBadRequest(mvc.perform(patch("/api/products/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"off\"}")));
    }

    @Test
    void updatesStatusRepeatedlyWithoutChangingOtherFields() throws Exception {
        long id = createProduct("状态测试", "文具");
        String createdAt = jdbc.queryForObject("SELECT created_at FROM products WHERE id = ?",
                OffsetDateTime.class, id).toString();
        for (String value : new String[]{"off", "off", "on", "on"}) {
            mvc.perform(patch("/api/products/{id}/status", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"" + value + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(id))
                    .andExpect(jsonPath("$.data.status").value(value));
            mvc.perform(get("/api/products/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(value))
                    .andExpect(jsonPath("$.data.price").value(1990))
                    .andExpect(jsonPath("$.data.stock").value(10));
        }
        assertThat(jdbc.queryForObject("SELECT created_at FROM products WHERE id = ?",
                OffsetDateTime.class, id).toString()).isEqualTo(createdAt);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":\"\"}",
            "{\"status\":\"ON\"}", "{\"status\":\"off \"}",
            "{\"status\":\"invalid\"}", "{\"status\":1}"})
    void rejectsInvalidStatusWithoutChangingProduct(String body) throws Exception {
        long id = createProduct("状态测试", "文具");
        expectBadRequest(mvc.perform(patch("/api/products/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON).content(body)))
                .andExpect(jsonPath("$.message", containsString("status")));
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = ?", String.class, id))
                .isEqualTo("on");
    }

    @Test
    void paginatesWithStableOrderAndFilteredTotals() throws Exception {
        long first = createProduct("数学书一", "图书");
        long second = createProduct("数学书二", "图书");
        long third = createProduct("数学笔记", "文具");
        jdbc.update("UPDATE products SET status = 'off' WHERE id = ?", first);
        jdbc.update("UPDATE products SET created_at = ?", OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        mvc.perform(get("/api/products").param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(third))
                .andExpect(jsonPath("$.data.items[1].id").value(second));
        mvc.perform(get("/api/products").param("page", "2").param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(first));
        mvc.perform(get("/api/products").param("keyword", " 数学 ")
                        .param("category", " 图书 ").param("status", "on").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(second));
        mvc.perform(get("/api/products").param("category", "图"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/products").param("status", "off"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(first));
        mvc.perform(get("/api/products").param("page", "2147483647").param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void returnsDefaultsAndEmptyArraysWhenNothingMatches() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.items").isEmpty());
        createProduct("商品", "文具");
        mvc.perform(get("/api/products").param("keyword", "不存在"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/products").param("keyword", "  ").param("category", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        // 空白 status 与“不传”同义，表示全部状态。
        mvc.perform(get("/api/products").param("status", " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "100%_!"})
    void treatsLikeWildcardsAsLiteralText(String keyword) throws Exception {
        long id = createProduct("优惠100%_!商品", "文具");
        createProduct("普通商品", "文具");
        mvc.perform(get("/api/products").param("keyword", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(id));
    }

    @Test
    void bindsSearchTextAsDataRatherThanSql() throws Exception {
        createProduct("普通商品", "文具");
        mvc.perform(get("/api/products").param("keyword", "' OR 1=1 --"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @ParameterizedTest
    @CsvSource(value = {"page|0", "page|-1", "page|abc", "page|1.5", "page|2147483648",
            "page|''", "pageSize|0", "pageSize|51", "pageSize|1.5", "pageSize|abc",
            "pageSize|''", "status|ON", "status|invalid"}, delimiter = '|')
    void rejectsInvalidListParameters(String field, String value) throws Exception {
        expectBadRequest(mvc.perform(get("/api/products").param(field, value)))
                .andExpect(jsonPath("$.message", containsString(field)));
    }

    @Test
    void rejectsOverlongFilters() throws Exception {
        expectBadRequest(mvc.perform(get("/api/products").param("keyword", "字".repeat(121))))
                .andExpect(jsonPath("$.message", containsString("keyword")));
        expectBadRequest(mvc.perform(get("/api/products").param("category", "字".repeat(51))))
                .andExpect(jsonPath("$.message", containsString("category")));
    }

    @Test
    void returnsUnified404ForUnknownPath() throws Exception {
        mvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.data").value(nullValue()));
        // 多写斜杠也应是 404，而不是被兜底成 500。
        mvc.perform(get("/api/products/"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void returnsUnified405ForUnsupportedMethod() throws Exception {
        mvc.perform(delete("/api/products/{id}", 1L))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(40500))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void returnsUnified415ForUnsupportedContentType() throws Exception {
        mvc.perform(post("/api/products")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"title\":\"测试商品\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(41500))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private ObjectNode validBody() {
        return mapper.createObjectNode()
                .put("title", "测试商品")
                .put("price", 1990)
                .put("stock", 10)
                .put("category", "文具")
                .put("description", "备注")
                .put("sellerName", "校园数码铺");
    }

    private long createProduct(String title, String category) throws Exception {
        ObjectNode body = validBody().put("title", title).put("category", category);
        String response = mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("data").get("id").asLong();
    }

    private ResultActions expectBadRequest(ResultActions response) throws Exception {
        return response.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
