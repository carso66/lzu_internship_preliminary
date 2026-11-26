package com.lzu.marketplace;

import com.lzu.marketplace.order.*;
import com.lzu.marketplace.product.Product;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** L2-12：五个测试方法，经 HTTP 层和真实数据库验证下单行为。 */
class OrderTest extends ApiTestSupport {
    @MockitoSpyBean private OrderRepository orders;

    @Test
    void successfulOrderUsesServerPriceAndDeductsOne() throws Exception {
        long id = product(1990, 2);
        var body = new HashMap<>(orderBody(id, 1));
        body.put("unitPrice", 1); body.put("totalAmount", 1);
        postJson("/api/orders", body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.unitPrice").value(1990))
                .andExpect(jsonPath("$.data.totalAmount").value(1990));
        assertEquals(1, jdbc.queryForObject("SELECT stock FROM products WHERE id=?", Integer.class, id));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class));
    }

    @Test
    void insufficientStockDoesNotWriteAnything() throws Exception {
        long id = product(1990, 0);
        postJson("/api/orders", orderBody(id, 1)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40901));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class));
        assertEquals(0, jdbc.queryForObject("SELECT stock FROM products WHERE id=?", Integer.class, id));
    }

    @Test
    void rejectsInvalidQuantityTypesAndRanges() throws Exception {
        long id = product(1990, 10);
        for (Object quantity : new Object[]{0, -1, "2", 1.5, true, 2147483648L})
            postJson("/api/orders", orderBody(id, quantity)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
        var missing = new HashMap<>(orderBody(id, 1)); missing.remove("quantity");
        postJson("/api/orders", missing).andExpect(status().isBadRequest());
        assertEquals(10, jdbc.queryForObject("SELECT stock FROM products WHERE id=?", Integer.class, id));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class));
    }

    @Test
    void failedInsertReallyRollsBackStock() throws Exception {
        long id = product(1990, 2);
        // 只模拟订单写入失败，库存更新和事务管理仍由真实数据库执行。
        doThrow(new DataIntegrityViolationException("forced insert failure"))
                .when(orders).insert(anyString(), any(Product.class), any(CreateOrderRequest.class), anyLong());
        postJson("/api/orders", orderBody(id, 1)).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(50000));
        assertEquals(2, jdbc.queryForObject("SELECT stock FROM products WHERE id=?", Integer.class, id));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class));
    }

    @Test
    void rejectsOffMissingAndOverflowingProducts() throws Exception {
        long off = product(100, 1);
        jdbc.update("UPDATE products SET status='off' WHERE id=?", off);
        postJson("/api/orders", orderBody(off, 1)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40902));
        postJson("/api/orders", orderBody(Long.MAX_VALUE, 1)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(40401));
        long expensive = product(Long.MAX_VALUE, 2);
        postJson("/api/orders", orderBody(expensive, 2)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
        assertEquals(2, jdbc.queryForObject("SELECT stock FROM products WHERE id=?", Integer.class, expensive));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class));
    }
}
