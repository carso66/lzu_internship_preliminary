package com.example.marketplace.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderCreationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsOrderWithServerCalculatedAmountsAndDeductsStock() throws Exception {
        long productId = createProduct(5);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.orderNo", startsWith("SO")))
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.productTitle").value("考研数学复习全书"))
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andExpect(jsonPath("$.data.unitPrice").value(4990))
                .andExpect(jsonPath("$.data.totalAmount").value(9980))
                .andExpect(jsonPath("$.data.status").value("created"))
                .andExpect(jsonPath("$.data.buyerPhone").value("13800138000"))
                .andExpect(jsonPath("$.data.address").value("兰州市城关区天水南路222号"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock").value(3));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQuantityPayloads")
    void rejectsInvalidQuantity(String scenario, Consumer<ObjectNode> mutation) throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("productId", 1);
        payload.put("quantity", 1);
        payload.put("buyerName", "张三");
        payload.put("buyerPhone", "13800138000");
        payload.put("address", "兰州市城关区天水南路222号");
        mutation.accept(payload);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("quantity")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsOrderForUnknownProduct() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(999999L, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value(containsString("999999")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @ParameterizedTest(name = "productId {0}")
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveProductId(long productId) throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("productId")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsOrderWhenStockIsInsufficient() throws Exception {
        long productId = createProduct(1);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.message").value("库存不足"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock").value(1));
    }

    @Test
    void rejectsOrderForOffSaleProduct() throws Exception {
        long productId = createProduct(5);
        mockMvc.perform(patch("/api/products/{id}/status", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"off"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902))
                .andExpect(jsonPath("$.message").value("商品已下架"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock").value(5));
    }
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequiredFieldPayloads")
    void rejectsInvalidRequiredOrderFields(String scenario, Consumer<ObjectNode> mutation, String fieldName) throws Exception {
        long productId = createProduct(5);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("productId", productId);
        payload.put("quantity", 1);
        payload.put("buyerName", "张三");
        payload.put("buyerPhone", "13800138000");
        payload.put("address", "兰州市城关区天水南路222号");
        mutation.accept(payload);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString(fieldName)))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
    private String orderPayload(long productId, int quantity) {
        return """
                {
                  "productId": %d,
                  "quantity": %d,
                  "buyerName": "张三",
                  "buyerPhone": "13800138000",
                  "address": "兰州市城关区天水南路222号"
                }
                """.formatted(productId, quantity);
    }

    private long createProduct(int stock) throws Exception {
        MvcResult creation = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "考研数学复习全书",
                                  "price": 4990,
                                  "stock": %d,
                                  "category": "Books",
                                  "description": "A useful book",
                                  "sellerName": "Campus shop"
                                }
                                """.formatted(stock)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createdProduct = objectMapper.readTree(creation.getResponse().getContentAsString())
                .path("data");
        return createdProduct.path("id").asLong();
    }

    private static Stream<Arguments> invalidRequiredFieldPayloads() {
        return Stream.of(
                Arguments.of("missing productId", mutation(node -> node.remove("productId")), "productId"),
                Arguments.of("blank buyerName", mutation(node -> node.put("buyerName", " ")), "buyerName"),
                Arguments.of("blank buyerPhone", mutation(node -> node.put("buyerPhone", " ")), "buyerPhone"),
                Arguments.of("blank address", mutation(node -> node.put("address", " ")), "address")
        );
    }
    private static Stream<Arguments> invalidQuantityPayloads() {
        return Stream.of(
                Arguments.of("zero", mutation(node -> node.put("quantity", 0))),
                Arguments.of("negative", mutation(node -> node.put("quantity", -1))),
                Arguments.of("string", mutation(node -> node.put("quantity", "2"))),
                Arguments.of("missing", mutation(node -> node.remove("quantity")))
        );
    }

    private static Consumer<ObjectNode> mutation(Consumer<ObjectNode> mutation) {
        return mutation;
    }
}
