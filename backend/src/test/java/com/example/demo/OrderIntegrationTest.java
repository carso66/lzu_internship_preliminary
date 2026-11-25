package com.example.demo;

import com.example.demo.dto.CreateOrderRequest;
import com.example.demo.dto.CreateProductRequest;
import com.example.demo.dto.UpdateProductStatusRequest;
import com.example.demo.model.Product;
import com.example.demo.repository.ProductRepository;
import com.example.demo.service.OrderService;
import com.example.demo.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP and database tests; neither the class nor its methods has a test transaction. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:order_integration_test;DB_CLOSE_DELAY=-1",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderIntegrationTest {

    private static final String PHONE = "TEST-PHONE-001";
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    @LocalServerPort private int port;
    @Autowired private JsonMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProductService products;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderService orders;

    @BeforeEach
    void resetTestDatabase() {
        dropFailureConstraint();
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");
    }

    @Test
    void normalOrderUsesDatabasePriceAndPreservesSnapshots() throws Exception {
        Product product = product(1990L, 2);
        ApiResult created = request("POST", "/api/orders", orderBody(product.id(), 2));
        assertSuccess(created, 201);
        JsonNode data = created.body().path("data");
        assertEquals(1990L, data.path("unitPrice").asLong());
        assertEquals(3980L, data.path("totalAmount").asLong());
        assertEquals("created", data.path("status").asText());
        assertTrue(data.path("paidAt").isNull());
        String orderNo = data.path("orderNo").asText();
        assertTrue(orderNo.matches("SO[0-9]{8}[A-F0-9]{32}"));
        assertEquals(0, stock(product.id()));
        assertEquals(1L, orderCount());

        jdbc.update("UPDATE products SET title = ?, price = ? WHERE id = ?",
                "Changed title", 2990L, product.id());
        ApiResult detail = request("GET", "/api/orders/" + orderNo, null);
        assertSuccess(detail, 200);
        assertEquals(product.title(), detail.body().at("/data/productTitle").asText());
        assertEquals(1990L, detail.body().at("/data/unitPrice").asLong());
        assertEquals(3980L, detail.body().at("/data/totalAmount").asLong());
    }

    @Test
    void invalidOrdersDoNotChangeStockOrCreateOrders() throws Exception {
        Product product = product(1990L, 2);
        for (Object quantity : Arrays.asList(null, 0, -1, "2", 1.5)) {
            ApiResult invalid = request("POST", "/api/orders", orderBody(product.id(), quantity));
            assertError(invalid, 400, 40001);
            assertTrue(invalid.body().path("message").asText().contains("quantity"));
            assertEquals(2, stock(product.id()));
            assertEquals(0L, orderCount());
        }
        assertError(request("POST", "/api/orders", orderBody(product.id(), 3)), 409, 40901);
        assertError(request("POST", "/api/orders", orderBody(Long.MAX_VALUE, 1)), 404, 40401);
        assertError(request("POST", "/api/orders", orderBody(0, 1)), 400, 40001);
        products.updateStatus(product.id(), new UpdateProductStatusRequest("off"));
        assertError(request("POST", "/api/orders", orderBody(product.id(), 1)), 409, 40902);
        assertEquals(2, stock(product.id()));

        Product expensive = product(Long.MAX_VALUE, 2);
        assertError(request("POST", "/api/orders", orderBody(expensive.id(), 2)), 400, 40001);
        assertEquals(2, stock(expensive.id()));
        Product expensiveButShort = product(Long.MAX_VALUE, 1);
        assertError(request("POST", "/api/orders", orderBody(expensiveButShort.id(), 2)), 409, 40901);
        assertEquals(1, stock(expensiveButShort.id()));
        assertEquals(0L, orderCount());
    }

    @Test
    void orderQueriesNormalizePhoneAndUseFilteredStablePagination() throws Exception {
        Product product = product(1990L, 5);
        Map<String, Object> firstBody = orderBody(product.id(), 1);
        firstBody.put("buyerPhone", "  " + PHONE + "  ");
        ApiResult first = request("POST", "/api/orders", firstBody);
        ApiResult second = request("POST", "/api/orders", orderBody(product.id(), 1));
        assertSuccess(first, 201);
        assertSuccess(second, 201);
        Map<String, Object> otherBody = orderBody(product.id(), 1);
        otherBody.put("buyerPhone", "OTHER-PHONE");
        assertSuccess(request("POST", "/api/orders", otherBody), 201);

        String encodedPhone = URLEncoder.encode("  " + PHONE + "  ", StandardCharsets.UTF_8);
        String query = "/api/orders?buyerPhone=" + encodedPhone;
        ApiResult pageOne = request("GET", query + "&page=1&pageSize=1", null);
        assertSuccess(pageOne, 200);
        assertEquals(2L, pageOne.body().at("/data/total").asLong());
        assertEquals(1, pageOne.body().at("/data/items").size());
        assertEquals(second.body().at("/data/orderNo").asText(),
                pageOne.body().at("/data/items/0/orderNo").asText());
        assertEquals(PHONE, pageOne.body().at("/data/items/0/buyerPhone").asText());
        ApiResult pageTwo = request("GET", query + "&page=2&pageSize=1", null);
        assertSuccess(pageTwo, 200);
        assertEquals(first.body().at("/data/orderNo").asText(),
                pageTwo.body().at("/data/items/0/orderNo").asText());
        ApiResult beyond = request("GET", query + "&page=99", null);
        assertSuccess(beyond, 200);
        assertEquals(2L, beyond.body().at("/data/total").asLong());
        assertEquals(0, beyond.body().at("/data/items").size());
        ApiResult empty = request("GET", "/api/orders?buyerPhone=NO-ORDERS", null);
        assertSuccess(empty, 200);
        assertEquals(0L, empty.body().at("/data/total").asLong());
        assertEquals(0, empty.body().at("/data/items").size());
        assertError(request("GET", query + "&page=0", null), 400, 40001);
        assertError(request("GET", query + "&pageSize=51", null), 400, 40001);
        assertError(request("GET", query + "&page=abc", null), 400, 40001);
        assertError(request("GET", "/api/orders", null), 400, 40001);
        assertError(request("GET", "/api/orders/NO-SUCH-ORDER", null), 404, 40401);
    }

    @Test
    void databaseInsertFailureRollsBackStockAndOrderCount() {
        Product product = product(1990L, 2);
        long initialCount = orderCount();
        jdbc.execute("ALTER TABLE orders ADD CONSTRAINT ck_orders_test_force_failure "
                + "CHECK (buyer_name <> 'ROLLBACK_TEST')");
        try {
            DataIntegrityViolationException error = assertThrows(DataIntegrityViolationException.class,
                    () -> orders.create(new CreateOrderRequest(product.id(), 2,
                            "ROLLBACK_TEST", PHONE, "Test address", "")));
            SQLException sqlError = assertInstanceOf(SQLException.class, error.getMostSpecificCause());
            assertEquals("23513", sqlError.getSQLState());
            assertTrue(sqlError.getMessage().toUpperCase(Locale.ROOT)
                    .contains("CK_ORDERS_TEST_FORCE_FAILURE"));
            assertEquals(2, stock(product.id()), "Stock must be restored after the actual INSERT fails");
            assertEquals(initialCount, orderCount());
        } finally {
            dropFailureConstraint();
        }
    }

    @Test
    void concurrentOrdersDoNotOversell() throws Exception {
        Product product = product(1990L, 1);
        int requestCount = 10;
        ExecutorService workers = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ApiResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requestCount; i++) {
                futures.add(workers.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                    return request("POST", "/api/orders", orderBody(product.id(), 1));
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int successes = 0;
            int conflicts = 0;
            for (Future<ApiResult> future : futures) {
                ApiResult response = future.get(20, TimeUnit.SECONDS);
                if (response.status() == 201) {
                    assertSuccess(response, 201);
                    successes++;
                } else {
                    assertError(response, 409, 40901);
                    conflicts++;
                }
            }
            assertEquals(1, successes);
            assertEquals(9, conflicts);
            assertEquals(0, stock(product.id()));
            assertEquals(1L, orderCount());
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Product product(long price, int stock) {
        return products.create(new CreateProductRequest("Order test product", price, stock,
                "Tests", "", "Test seller", ""));
    }

    private int stock(long id) {
        return productRepository.findById(id).orElseThrow().stock();
    }

    private long orderCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
    }

    private void dropFailureConstraint() {
        jdbc.execute("ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_test_force_failure");
    }

    private Map<String, Object> orderBody(long productId, Object quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", productId);
        body.put("quantity", quantity);
        body.put("buyerName", "Test buyer");
        body.put("buyerPhone", PHONE);
        body.put("address", "Test address");
        return body;
    }

    private ApiResult request(String method, String path, Object body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json");
        if ("GET".equals(method)) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(body), StandardCharsets.UTF_8));
        }
        HttpResponse<String> response = client.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertTrue(response.headers().firstValue("Content-Type").orElse("")
                .startsWith("application/json"));
        return new ApiResult(response.statusCode(), mapper.readTree(response.body()));
    }

    private void assertSuccess(ApiResult result, int expectedStatus) {
        assertEquals(expectedStatus, result.status(), result.body().toString());
        assertEquals(0, result.body().path("code").asInt());
        assertEquals("ok", result.body().path("message").asText());
        assertFalse(result.body().path("data").isMissingNode());
    }

    private void assertError(ApiResult result, int expectedStatus, int expectedCode) {
        assertEquals(expectedStatus, result.status(), result.body().toString());
        assertEquals(expectedCode, result.body().path("code").asInt());
        assertFalse(result.body().path("message").asText().isBlank());
        assertTrue(result.body().path("data").isNull());
    }

    private record ApiResult(int status, JsonNode body) { }
}
