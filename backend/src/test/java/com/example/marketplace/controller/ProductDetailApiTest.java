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
class ProductDetailApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returnsProductDetailsById() throws Exception {
        MvcResult creation = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Blue Pen",
                                  "price": 1990,
                                  "stock": 10,
                                  "category": "Stationery",
                                  "description": "A useful pen",
                                  "sellerName": "Campus shop"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createdProduct = objectMapper.readTree(creation.getResponse().getContentAsString())
                .path("data");
        long productId = createdProduct.path("id").asLong();

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.id").value(productId))
                .andExpect(jsonPath("$.data.title").value("Blue Pen"))
                .andExpect(jsonPath("$.data.price").value(1990))
                .andExpect(jsonPath("$.data.stock").value(10))
                .andExpect(jsonPath("$.data.category").value("Stationery"))
                .andExpect(jsonPath("$.data.description").value("A useful pen"))
                .andExpect(jsonPath("$.data.sellerName").value("Campus shop"))
                .andExpect(jsonPath("$.data.status").value("on"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    @Test
    void returnsNotFoundForMissingProduct() throws Exception {
        mockMvc.perform(get("/api/products/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
