package com.example.marketplace.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderListApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void returnsMatchingBuyerOrdersNewestFirst() throws Exception {
        long productId = createProduct();
        CreatedOrder older = createOrder(productId, "13800138000");
        createOrder(productId, "13900139000");
        CreatedOrder newest = createOrder(productId, "13800138000");

        jdbcTemplate.update(
                "UPDATE orders SET created_at = ? WHERE id = ?",
                OffsetDateTime.parse("2026-11-20T10:00:00+08:00"),
                older.id()
        );
        jdbcTemplate.update(
                "UPDATE orders SET created_at = ? WHERE id = ?",
                OffsetDateTime.parse("2026-11-21T10:00:00+08:00"),
                newest.id()
        );

        mockMvc.perform(get("/api/orders")
                        .param("buyerPhone", "13800138000")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].orderNo").value(newest.orderNo()))
                .andExpect(jsonPath("$.data.items[1].orderNo").value(older.orderNo()))
                .andExpect(jsonPath("$.data.items[0].buyerPhone").value("13800138000"));
    }

    @Test
    void returnsRequestedPageWithTotalMatchingOrders() throws Exception {
        long productId = createProduct();
        CreatedOrder oldest = createOrder(productId, "13800138001");
        CreatedOrder middle = createOrder(productId, "13800138001");
        CreatedOrder newest = createOrder(productId, "13800138001");

        jdbcTemplate.update(
                "UPDATE orders SET created_at = ? WHERE id = ?",
                OffsetDateTime.parse("2026-11-20T10:00:00+08:00"),
                oldest.id()
        );
        jdbcTemplate.update(
                "UPDATE orders SET created_at = ? WHERE id = ?",
                OffsetDateTime.parse("2026-11-21T10:00:00+08:00"),
                middle.id()
        );
        jdbcTemplate.update(
                "UPDATE orders SET created_at = ? WHERE id = ?",
                OffsetDateTime.parse("2026-11-22T10:00:00+08:00"),
                newest.id()
        );

        mockMvc.perform(get("/api/orders")
                        .param("buyerPhone", "13800138001")
                        .param("page", "2")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].orderNo").value(middle.orderNo()));
    }

    @Test
    void returnsEmptyPageWhenBuyerHasNoOrders() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .param("buyerPhone", "13800138999")
                        .param("page", "2")
                        .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(5))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void rejectsMissingBuyerPhone() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("buyerPhone")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @ParameterizedTest(name = "{0}={1} is rejected")
    @MethodSource("invalidPaginationParameters")
    void rejectsInvalidPaginationParameters(String parameter, String value) throws Exception {
        var request = get("/api/orders");
        if (!"buyerPhone".equals(parameter)) {
            request.param("buyerPhone", "13800138000");
        }

        mockMvc.perform(request.param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString(parameter)))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private static Stream<Arguments> invalidPaginationParameters() {
        return Stream.of(
                Arguments.of("buyerPhone", " "),
                Arguments.of("page", "0"),
                Arguments.of("page", "abc"),
                Arguments.of("pageSize", "0"),
                Arguments.of("pageSize", "51"),
                Arguments.of("pageSize", "abc")
        );
    }

    private CreatedOrder createOrder(long productId, String buyerPhone) throws Exception {
        MvcResult creation = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": 1,
                                  "buyerName": "Buyer",
                                  "buyerPhone": "%s",
                                  "address": "Lanzhou"
                                }
                                """.formatted(productId, buyerPhone)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode order = objectMapper.readTree(creation.getResponse().getContentAsString()).path("data");
        return new CreatedOrder(order.path("id").asLong(), order.path("orderNo").asText());
    }

    private long createProduct() throws Exception {
        MvcResult creation = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Order list test product",
                                  "price": 1990,
                                  "stock": 10,
                                  "category": "Tests",
                                  "description": "Used to create orders",
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

    private record CreatedOrder(long id, String orderNo) {
    }
}
