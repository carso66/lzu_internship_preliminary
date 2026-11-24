package com.example.marketplace.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductCreationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsProductWithOnStatus() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Test product",
                                  "price": 1990,
                                  "stock": 10,
                                  "category": "Stationery",
                                  "description": "A useful pen",
                                  "sellerName": "Campus shop"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.title").value("Test product"))
                .andExpect(jsonPath("$.data.price").value(1990))
                .andExpect(jsonPath("$.data.stock").value(10))
                .andExpect(jsonPath("$.data.category").value("Stationery"))
                .andExpect(jsonPath("$.data.description").value("A useful pen"))
                .andExpect(jsonPath("$.data.sellerName").value("Campus shop"))
                .andExpect(jsonPath("$.data.status").value("on"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    @Test
    void rejectsBlankTitleWithFieldName() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "   ",
                                  "price": 1990,
                                  "stock": 10,
                                  "category": "Stationery",
                                  "description": "A useful pen",
                                  "sellerName": "Campus shop"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("title")))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFieldPayloads")
    void rejectsInvalidFieldValues(String scenario, Consumer<ObjectNode> mutation, String fieldName) throws Exception {
        ObjectNode payload = validPayload();
        mutation.accept(payload);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(fieldName)))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @ParameterizedTest(name = "price value {0}")
    @ValueSource(strings = {"\"not-a-number\"", "1990.5"})
    void rejectsInvalidPriceTypeWithFieldName(String priceJson) throws Exception {
        String payload = """
                {
                  "title": "Test product",
                  "price": %s,
                  "stock": 10,
                  "category": "Stationery",
                  "description": "A useful pen",
                  "sellerName": "Campus shop"
                }
                """.formatted(priceJson);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("price")))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidScalarTypes")
    void rejectsUnexpectedJsonTypes(String scenario, Consumer<ObjectNode> mutation, String fieldName) throws Exception {
        ObjectNode payload = validPayload();
        mutation.accept(payload);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(fieldName)))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    private Stream<Arguments> invalidScalarTypes() {
        return Stream.of(
                Arguments.of("numeric title", mutation(node -> node.put("title", 123)), "title"),
                Arguments.of("numeric category", mutation(node -> node.put("category", 123)), "category"),
                Arguments.of("numeric seller name", mutation(node -> node.put("sellerName", 123)), "sellerName"),
                Arguments.of("object description", mutation(node -> node.putObject("description")), "description"),
                Arguments.of("string price", mutation(node -> node.put("price", "1990")), "price"),
                Arguments.of("string stock", mutation(node -> node.put("stock", "10")), "stock")
        );
    }

    private ObjectNode validPayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", "Test product");
        payload.put("price", 1990);
        payload.put("stock", 10);
        payload.put("category", "Stationery");
        payload.put("description", "A useful pen");
        payload.put("sellerName", "Campus shop");
        return payload;
    }

    private Stream<Arguments> invalidFieldPayloads() {
        return Stream.of(
                Arguments.of("missing title", mutation(node -> node.remove("title")), "title"),
                Arguments.of("title too long", mutation(node -> node.put("title", "x".repeat(121))), "title"),
                Arguments.of("blank category", mutation(node -> node.put("category", " ")), "category"),
                Arguments.of("category too long", mutation(node -> node.put("category", "x".repeat(65))), "category"),
                Arguments.of("description too long", mutation(node -> node.put("description", "x".repeat(1001))), "description"),
                Arguments.of("blank seller name", mutation(node -> node.put("sellerName", " ")), "sellerName"),
                Arguments.of("seller name too long", mutation(node -> node.put("sellerName", "x".repeat(81))), "sellerName"),
                Arguments.of("missing price", mutation(node -> node.remove("price")), "price"),
                Arguments.of("zero price", mutation(node -> node.put("price", 0)), "price"),
                Arguments.of("negative stock", mutation(node -> node.put("stock", -1)), "stock"),
                Arguments.of("missing stock", mutation(node -> node.remove("stock")), "stock")
        );
    }

    private Consumer<ObjectNode> mutation(Consumer<ObjectNode> mutation) {
        return mutation;
    }
}