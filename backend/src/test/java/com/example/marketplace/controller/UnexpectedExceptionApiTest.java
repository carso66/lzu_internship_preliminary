package com.example.marketplace.controller;

import com.example.marketplace.domain.Product;
import com.example.marketplace.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证未知异常返回统一 500 响应，且不泄露异常详情。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:unexpected-exception-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class UnexpectedExceptionApiTest {

    private static final String SENSITIVE_ERROR = "database password=secret";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductMapper productMapper;

    @BeforeEach
    void setUp() {
        when(productMapper.insert(any(Product.class)))
                .thenThrow(new IllegalStateException(SENSITIVE_ERROR));
    }

    @Test
    void returnsUnifiedServerErrorWithoutLeakingExceptionDetails() throws Exception {
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
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50000))
                .andExpect(jsonPath("$.message").value("internal server error"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(content().string(not(containsString(SENSITIVE_ERROR))));
    }
}