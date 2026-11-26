package com.example.marketplace.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.transaction.annotation.Transactional;

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
class ProductListApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void returnsFirstPageWithDefaultPagination() throws Exception {
        createProduct("Blue Pen", "Stationery");

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].title").value("Blue Pen"))
                .andExpect(jsonPath("$.data.items[0].price").value(1990))
                .andExpect(jsonPath("$.data.items[0].status").value("on"));
    }

    @Test
    void combinesFiltersAndPaginates() throws Exception {
        createProduct("Blue Pen", "Stationery");
        createProduct("Blue Notebook", "Stationery");
        createProduct("Blue Lamp", "Dorm");
        createProduct("Blue Pencil", "Stationery");
        jdbcTemplate.update("UPDATE products SET status = 'off' WHERE title = ?", "Blue Pencil");

        mockMvc.perform(get("/api/products")
                        .param("keyword", "Blue")
                        .param("category", "Stationery")
                        .param("status", "on")
                        .param("page", "2")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].title").value("Blue Notebook"));
    }

    @Test
    void returnsEmptyItemsWhenNoProductMatches() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("keyword", "missing")
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
    void handlesLargePositivePageWithoutOffsetOverflow() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("page", String.valueOf(Integer.MAX_VALUE))
                        .param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").value(Integer.MAX_VALUE))
                .andExpect(jsonPath("$.data.pageSize").value(50))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @ParameterizedTest(name = "{0}={1} is rejected")
    @MethodSource("invalidParameters")
    void rejectsInvalidListParameters(String parameter, String value) throws Exception {
        mockMvc.perform(get("/api/products").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString(parameter)))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private static Stream<Arguments> invalidParameters() {
        return Stream.of(
                Arguments.of("status", "pending"),
                Arguments.of("page", "0"),
                Arguments.of("page", "-1"),
                Arguments.of("page", "abc"),
                Arguments.of("pageSize", "0"),
                Arguments.of("pageSize", "51"),
                Arguments.of("pageSize", "abc")
        );
    }

    private void createProduct(String title, String category) throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", title);
        payload.put("price", 1990);
        payload.put("stock", 10);
        payload.put("category", category);
        payload.put("description", "A useful item");
        payload.put("sellerName", "Campus shop");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isCreated());
    }
}
