package com.lzu.marketplace;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:flow-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
abstract class ApiTestSupport {
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected JdbcTemplate jdbc;

    // 不包测试外层事务，保证能观察业务事务真正提交或回滚后的数据库结果。
    @BeforeEach
    void cleanData() {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");
    }

    protected ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)));
    }

    protected JsonNode data(ResultActions response) throws Exception {
        return mapper.readTree(response.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    protected long product(long price, int stock) throws Exception {
        return data(postJson("/api/products", Map.of("title", "Test product", "price", price, "stock", stock,
                "category", "Test", "sellerName", "Test seller")).andExpect(status().isCreated())).get("id").asLong();
    }

    protected Map<String, Object> orderBody(long productId, Object quantity) {
        return Map.of("productId", productId, "quantity", quantity, "buyerName", "Test buyer",
                "buyerPhone", "00000000000", "address", "Test address");
    }

    protected String order(long productId) throws Exception {
        return data(postJson("/api/orders", orderBody(productId, 1)).andExpect(status().isCreated())).get("orderNo").asText();
    }
}
