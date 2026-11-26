package com.lzu.marketplace;

import com.lzu.marketplace.order.OrderRepository;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentTest extends ApiTestSupport {
    @MockitoSpyBean private OrderRepository orders;

    @Test
    void paysBothMethodsAndRejectsSecondPayment() throws Exception {
        long id = product(1990, 2);
        for (String method : new String[]{"alipay", "wechat"}) {
            String number = order(id);
            postJson("/api/payments", Map.of("orderNo", number, "method", method, "amount", 1))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.amount").value(1990))
                    .andExpect(jsonPath("$.data.status").value("paid")).andExpect(jsonPath("$.data.paidAt").isNotEmpty());
            postJson("/api/payments", Map.of("orderNo", number, "method", method))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40903));
            assertEquals("paid", jdbc.queryForObject("SELECT status FROM orders WHERE order_no=?", String.class, number));
            assertNotNull(jdbc.queryForObject("SELECT paid_at FROM orders WHERE order_no=?", LocalDateTime.class, number));
        }
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM payments", Long.class));
    }

    @Test
    void rejectsMissingOrdersAndInvalidMethods() throws Exception {
        postJson("/api/payments", Map.of("orderNo", "missing", "method", "alipay"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(40401));
        for (Object body : new Object[]{Map.of("orderNo", "x", "method", "cash"), Map.of("method", "alipay"), Map.of("orderNo", "x")})
            postJson("/api/payments", body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM payments", Long.class));
    }

    @Test
    void rollsBackPaymentWhenOrderUpdateFails() throws Exception {
        String number = order(product(100, 1));
        doThrow(new IllegalStateException("forced order update failure")).when(orders).markPaid(eq(number), any(LocalDateTime.class));
        postJson("/api/payments", Map.of("orderNo", number, "method", "wechat"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(50000));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM payments", Long.class));
        assertEquals("created", jdbc.queryForObject("SELECT status FROM orders WHERE order_no=?", String.class, number));
    }
}
