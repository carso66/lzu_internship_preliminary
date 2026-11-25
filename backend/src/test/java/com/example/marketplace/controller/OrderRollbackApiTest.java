package com.example.marketplace.controller;

import com.example.marketplace.service.OrderNumberGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:order-rollback-test;DB_CLOSE_DELAY=-1"
)
class OrderRollbackApiTest {

    private static final String FIXED_ORDER_NO = "SO20261125FIXED000001";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private OrderNumberGenerator orderNumberGenerator;

    @BeforeEach
    void setUp() {
        when(orderNumberGenerator.generate()).thenReturn(FIXED_ORDER_NO);
    }

    @Test
    void rollsBackStockWhenOrderInsertFails() throws Exception {
        long productId = createProduct(10);

        ResponseEntity<String> firstOrder = postOrder(productId, 1);
        assertThat(firstOrder.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> conflictingOrder = postOrder(productId, 2);
        assertThat(conflictingOrder.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(objectMapper.readTree(conflictingOrder.getBody()).path("code").asInt())
                .isEqualTo(50000);

        ResponseEntity<String> productResponse = restTemplate.getForEntity(
                "/api/products/{id}", String.class, productId);
        assertThat(productResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode product = objectMapper.readTree(productResponse.getBody()).path("data");
        assertThat(product.path("stock").asInt()).isEqualTo(9);

        Long orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE product_id = ?", Long.class, productId);
        assertThat(orderCount).isEqualTo(1L);
    }

    private long createProduct(int stock) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/products",
                HttpMethod.POST,
                new HttpEntity<>("""
                        {
                          "title": "事务回滚测试商品",
                          "price": 4990,
                          "stock": %d,
                          "category": "Books",
                          "description": "用于验证订单写入失败时库存回滚",
                          "sellerName": "Campus shop"
                        }
                        """.formatted(stock), headers),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody()).path("data").path("id").asLong();
    }

    private ResponseEntity<String> postOrder(long productId, int quantity) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "/api/orders",
                HttpMethod.POST,
                new HttpEntity<>("""
                        {
                          "productId": %d,
                          "quantity": %d,
                          "buyerName": "张三",
                          "buyerPhone": "13800138000",
                          "address": "兰州市城关区天水南路222号"
                        }
                        """.formatted(productId, quantity), headers),
                String.class);
    }
}
