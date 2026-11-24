package com.lzu.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L2-12：五条端到端用例，走真实 HTTP（RANDOM_PORT + TestRestTemplate），不做 mock。
 *
 * <p>不加 @Transactional：Spring 的测试事务会把请求和测试放在同一个事务里，
 * 测不出真实提交后的效果，跨线程的并发场景也会挂。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderFlowTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("正常下单：201，金额由服务端算，库存减一")
    void placeOrderSucceeds() throws Exception {
        long productId = createProduct(1999, 10);

        ResponseEntity<String> resp = postOrder(productId, 2, "13800000001");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = json(resp);
        assertThat(body.get("code").asInt()).isZero();

        JsonNode order = body.get("data");
        assertThat(order.get("orderNo").asText()).startsWith("SO");
        assertThat(order.get("unitPrice").asInt()).isEqualTo(1999);
        assertThat(order.get("totalAmount").asLong()).isEqualTo(1999L * 2);
        assertThat(order.get("status").asText()).isEqualTo("created");
        assertThat(order.get("quantity").asInt()).isEqualTo(2);

        assertThat(stockOf(productId)).isEqualTo(8);
    }

    @Test
    @DisplayName("库存不足：409/40901，且库存一分不掉")
    void placeOrderFailsWhenStockNotEnough() throws Exception {
        long productId = createProduct(500, 1);

        ResponseEntity<String> resp = postOrder(productId, 2, "13800000002");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode body = json(resp);
        assertThat(body.get("code").asInt()).isEqualTo(40901);
        assertThat(body.get("data").isNull()).isTrue();

        assertThat(stockOf(productId)).isEqualTo(1);
    }

    @Test
    @DisplayName("quantity = 0：400/40001，参数校验拦截")
    void placeOrderFailsWhenQuantityIsZero() throws Exception {
        long productId = createProduct(100, 5);

        ResponseEntity<String> resp = postOrder(productId, 0, "13800000003");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json(resp).get("code").asInt()).isEqualTo(40001);
        assertThat(stockOf(productId)).isEqualTo(5);
    }

    @Test
    @DisplayName("商品不存在：404/40401")
    void placeOrderFailsWhenProductMissing() throws Exception {
        ResponseEntity<String> resp = postOrder(999_999L, 1, "13800000004");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json(resp).get("code").asInt()).isEqualTo(40401);
    }

    @Test
    @DisplayName("重复支付：第一次 200，第二次 409/40903，payments 只落一行")
    void payTwiceKeepsSinglePayment() throws Exception {
        long productId = createProduct(2500, 3);
        ResponseEntity<String> ok = postOrder(productId, 2, "13800000005");
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String orderNo = json(ok).get("data").get("orderNo").asText();

        ResponseEntity<String> first = postPayment(orderNo, "alipay");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode paid = json(first).get("data");
        assertThat(paid.get("status").asText()).isEqualTo("paid");
        assertThat(paid.get("amount").asLong()).isEqualTo(5000);

        // 换一种支付方式再打一次，幂等不应该因为 method 变了就放行
        ResponseEntity<String> second = postPayment(orderNo, "wechat");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json(second).get("code").asInt()).isEqualTo(40903);

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id = "
                        + "(SELECT id FROM orders WHERE order_no = ?)",
                Integer.class, orderNo);
        assertThat(rows).isEqualTo(1);
    }

    // ---------------------------------------------------------------- helpers

    private long createProduct(int price, int stock) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "测试商品");
        body.put("price", price);
        body.put("stock", stock);
        body.put("category", "test");
        body.put("description", "created by OrderFlowTest");
        body.put("sellerName", "junit");

        ResponseEntity<String> resp = rest.postForEntity("/api/products", body, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json(resp).get("data").get("id").asLong();
    }

    private ResponseEntity<String> postOrder(long productId, int quantity, String phone) {
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        body.put("quantity", quantity);
        body.put("buyerName", "张三");
        body.put("buyerPhone", phone);
        body.put("address", "兰州大学榆中校区");
        return rest.postForEntity("/api/orders", body, String.class);
    }

    private ResponseEntity<String> postPayment(String orderNo, String method) {
        Map<String, Object> body = new HashMap<>();
        body.put("orderNo", orderNo);
        body.put("method", method);
        return rest.postForEntity("/api/payments", body, String.class);
    }

    private int stockOf(long productId) {
        ResponseEntity<String> resp = rest.getForEntity("/api/products/" + productId, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json(resp).get("data").get("stock").asInt();
    }

    private JsonNode json(ResponseEntity<String> resp) {
        try {
            return objectMapper.readTree(resp.getBody());
        } catch (Exception e) {
            throw new AssertionError("响应不是合法 JSON: " + resp.getBody(), e);
        }
    }
}
