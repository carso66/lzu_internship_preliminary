package com.example.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_integration_test;DB_CLOSE_DELAY=-1",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthIntegrationTest {
    @LocalServerPort private int port;
    @Autowired private JsonMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    @BeforeEach
    void clearData() {
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");
    }

    @Test
    void loginReturnsDifferentRandomTokensAndBothRemainValid() throws Exception {
        String first = login();
        String second = login();
        assertTrue(first.matches("[A-Za-z0-9_-]{43}"));
        assertTrue(second.matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(first, second);
        success(send("POST", "/api/products", productBody(), "Bearer " + first), 201);
        success(send("POST", "/api/products", productBody(), "Bearer " + second), 201);
        assertEquals(2L, productCount());
    }

    @Test
    void invalidLoginNeverReturnsToken() throws Exception {
        error(send("POST", "/api/auth/login", Map.of("username", "demo", "password", "wrong"), null), 401, 40101);
        error(send("POST", "/api/auth/login", Map.of("username", "other", "password", "demo123"), null), 401, 40101);
        for (Object invalid : Arrays.asList(null, "", "   ", 1, true)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("username", invalid);
            body.put("password", "demo123");
            error(send("POST", "/api/auth/login", body, null), 400, 40001);
            body.put("username", "demo");
            body.put("password", invalid);
            error(send("POST", "/api/auth/login", body, null), 400, 40001);
        }
        error(send("POST", "/api/auth/login", Map.of(), null), 400, 40001);
        error(send("POST", "/api/auth/login", null, null), 400, 40001);
    }

    @Test
    void missingOrInvalidTokenBlocksBothWritesWithoutChangingData() throws Exception {
        String token = login();
        JsonNode product = success(send("POST", "/api/products", productBody(), "Bearer " + token), 201);
        long id = product.path("id").asLong();
        for (String header : new String[]{null, "", "Bearer", "Bearer ", "Basic abc", "Bearer unknown",
                token, "Bearer  " + token, "Bearer " + token + "x"}) {
            error(send("POST", "/api/products", productBody(), header), 401, 40101);
            error(send("PATCH", "/api/products/" + id + "/status", Map.of("status", "off"), header), 401, 40101);
            assertEquals(1L, productCount());
            assertEquals("on", jdbc.queryForObject("SELECT status FROM products WHERE id = ?", String.class, id));
            assertEquals(3, jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, id));
        }
    }

    @Test
    void validTokenAllowsStatusChangesAndPreservesParameterValidation() throws Exception {
        String header = "Bearer " + login();
        long id = success(send("POST", "/api/products", productBody(), header), 201).path("id").asLong();
        JsonNode off = success(send("PATCH", "/api/products/" + id + "/status", Map.of("status", "off"), header), 200);
        assertEquals("off", off.path("status").asText());
        assertEquals(3, off.path("stock").asInt());
        assertEquals("on", success(send("PATCH", "/api/products/" + id + "/status", Map.of("status", "on"), header), 200)
                .path("status").asText());
        Map<String, Object> bad = productBody();
        bad.put("stock", "3");
        error(send("POST", "/api/products", bad, header), 400, 40001);
        error(send("PATCH", "/api/products/" + id + "/status", Map.of("status", "unknown"), header), 400, 40001);
        error(send("PATCH", "/api/products/9223372036854775807/status", Map.of("status", "off"), header), 404, 40401);
        error(send("POST", "/api/products", null, header), 400, 40001);
        assertEquals(1L, productCount());
    }

    @Test
    void readsAndOrdersRemainAccessibleWithoutToken() throws Exception {
        long id = success(send("POST", "/api/products", productBody(), "Bearer " + login()), 201).path("id").asLong();
        success(send("GET", "/api/health", null, null), 200);
        success(send("GET", "/api/products", null, null), 200);
        success(send("GET", "/api/products/" + id, null, null), 200);
        JsonNode order = success(send("POST", "/api/orders", Map.of("productId", id, "quantity", 1,
                "buyerName", "Auth buyer", "buyerPhone", "AUTH-PHONE", "address", "Test address"), null), 201);
        String orderNo = order.path("orderNo").asText();
        success(send("GET", "/api/orders/" + orderNo, null, null), 200);
        success(send("GET", "/api/orders?buyerPhone=AUTH-PHONE", null, null), 200);
        assertEquals(2, jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, id));
    }

    private String login() throws Exception {
        return success(send("POST", "/api/auth/login", Map.of("username", "demo", "password", "demo123"), null), 200)
                .path("token").asText();
    }

    private long productCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM products", Long.class);
    }

    private Map<String, Object> productBody() {
        return new LinkedHashMap<>(Map.of("title", "Auth test product", "price", 1990,
                "stock", 3, "category", "Tests", "sellerName", "Test seller"));
    }

    private ApiResult send(String method, String path, Object body, String authorization) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Accept", "application/json");
        if (authorization != null) builder.header("Authorization", authorization);
        if (method.equals("GET")) builder.GET();
        else builder.header("Content-Type", "application/json").method(method, body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        return new ApiResult(response.statusCode(), mapper.readTree(response.body()));
    }

    private JsonNode success(ApiResult result, int status) {
        assertEquals(status, result.status(), result.body().toString());
        assertEquals(0, result.body().path("code").asInt());
        assertEquals("ok", result.body().path("message").asText());
        return result.body().path("data");
    }

    private void error(ApiResult result, int status, int code) {
        assertEquals(status, result.status(), result.body().toString());
        assertEquals(code, result.body().path("code").asInt());
        assertFalse(result.body().path("message").asText().isBlank());
        assertTrue(result.body().path("data").isNull());
    }

    private record ApiResult(int status, JsonNode body) { }
}
