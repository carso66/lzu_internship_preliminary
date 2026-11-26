package com.lzu.marketplace;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyTest extends ApiTestSupport {
    @Test
    void tenBuyersCompeteForOneItem() throws Exception {
        long id = product(100, 1);
        var responses = concurrently(() -> postJson("/api/orders", orderBody(id, 1)).andReturn());
        assertEquals(1, responses.stream().filter(r -> r.getResponse().getStatus() == 201).count());
        for (var response : responses) {
            if (response.getResponse().getStatus() != 201) {
                assertEquals(409, response.getResponse().getStatus());
                assertEquals(40901, mapper.readTree(response.getResponse().getContentAsString()).get("code").asInt());
            }
        }
        assertEquals(0, jdbc.queryForObject("SELECT stock FROM products WHERE id=?", Integer.class, id));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE product_id=?", Long.class, id));
    }

    @Test
    void concurrentPaymentsCreateExactlyOneRecord() throws Exception {
        String number = order(product(100, 1));
        var responses = concurrently(() -> postJson("/api/payments", Map.of("orderNo", number, "method", "alipay")).andReturn());
        assertEquals(1, responses.stream().filter(r -> r.getResponse().getStatus() == 200).count());
        for (var response : responses) {
            if (response.getResponse().getStatus() != 200) {
                assertEquals(409, response.getResponse().getStatus());
                assertEquals(40903, mapper.readTree(response.getResponse().getContentAsString()).get("code").asInt());
            }
        }
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE order_no=?", Long.class, number));
    }

    private <T> List<T> concurrently(Callable<T> action) throws Exception {
        var pool = Executors.newFixedThreadPool(10);
        var gate = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i=0; i<10; i++) futures.add(pool.submit(() -> { gate.await(); return action.call(); }));
            gate.countDown();
            List<T> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
