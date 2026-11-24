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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductStatusApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void updatesProductStatusAndReturnsCurrentState() throws Exception {
        long productId = createProduct();

        mockMvc.perform(patch("/api/products/{id}/status", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"off"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.id").value(productId))
                .andExpect(jsonPath("$.data.status").value("off"));

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("off"));
    }

    @Test
    void rejectsStatusOutsideOnOff() throws Exception {
        long productId = createProduct();

        mockMvc.perform(patch("/api/products/{id}/status", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"up"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("status must be on or off"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsMissingStatus() throws Exception {
        long productId = createProduct();

        mockMvc.perform(patch("/api/products/{id}/status", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("status must be on or off"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void returnsNotFoundForMissingProduct() throws Exception {
        mockMvc.perform(patch("/api/products/{id}/status", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"off"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private long createProduct() throws Exception {
        MvcResult creation = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Status test product",
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
        return createdProduct.path("id").asLong();
    }
}
