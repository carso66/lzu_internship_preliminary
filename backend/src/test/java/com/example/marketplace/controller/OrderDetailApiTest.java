package com.example.marketplace.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderDetailApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returnsOrderDetailsByOrderNo() throws Exception {
        long productId = createProduct();
        MvcResult creation = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": 2,
                                  "buyerName": "张三",
                                  "buyerPhone": "13800138000",
                                  "address": "兰州市城关区天水南路222号"
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createdOrder = objectMapper.readTree(creation.getResponse().getContentAsString())
                .path("data");
        String orderNo = createdOrder.path("orderNo").asText();

        mockMvc.perform(get("/api/orders/{orderNo}", orderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.id").value(createdOrder.path("id").asLong()))
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.productTitle").value("考研数学复习全书"))
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andExpect(jsonPath("$.data.unitPrice").value(4990))
                .andExpect(jsonPath("$.data.totalAmount").value(9980))
                .andExpect(jsonPath("$.data.status").value("created"))
                .andExpect(jsonPath("$.data.buyerPhone").value("13800138000"))
                .andExpect(jsonPath("$.data.address").value("兰州市城关区天水南路222号"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    @Test
    void returnsNotFoundForMissingOrder() throws Exception {
        mockMvc.perform(get("/api/orders/{orderNo}", "SO-NOT-FOUND"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private long createProduct() throws Exception {
        MvcResult creation = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "考研数学复习全书",
                                  "price": 4990,
                                  "stock": 5,
                                  "category": "Books",
                                  "description": "A useful book",
                                  "sellerName": "Campus shop"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(creation.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();
    }
}
