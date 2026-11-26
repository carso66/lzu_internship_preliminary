package com.lzu.marketplace;

import java.util.HashMap;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderQueryTest extends ApiTestSupport {
    @Test
    void keepsSnapshotsAndFiltersBuyerWithStablePagination() throws Exception {
        long id = product(1990, 10);
        String first = order(id), second = order(id);
        assertNotEquals(first, second);
        var other = new HashMap<>(orderBody(id, 1)); other.put("buyerPhone", "00000000001");
        postJson("/api/orders", other).andExpect(status().isCreated());
        jdbc.update("UPDATE products SET title='changed', price=999 WHERE id=?", id);
        mvc.perform(get("/api/orders/" + first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productTitle").value("Test product"))
                .andExpect(jsonPath("$.data.unitPrice").value(1990));
        mvc.perform(get("/api/orders?buyerPhone=00000000000&pageSize=1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].orderNo").value(second));
        mvc.perform(get("/api/orders?buyerPhone=00000000000&pageSize=1&page=2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].orderNo").value(first));
        mvc.perform(get("/api/orders?buyerPhone=00000000002"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/orders/missing")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void validatesOrderInputsAndQueryParameters() throws Exception {
        long id = product(100, 1);
        for (String field : new String[]{"productId", "buyerName", "buyerPhone", "address"}) {
            var body = new HashMap<>(orderBody(id, 1)); body.remove(field);
            postJson("/api/orders", body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
        }
        for (String query : new String[]{"", "?buyerPhone=abc", "?buyerPhone=00000000000&page=0",
                "?buyerPhone=00000000000&pageSize=51", "?buyerPhone=00000000000&page=abc"})
            mvc.perform(get("/api/orders" + query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
    }
}
