package com.lzu.marketplace;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;

class ProductReadTest extends ApiTestSupport {
    @Test
    void detailAndMissingProduct() throws Exception {
        long id = product(1990, 10);
        mvc.perform(get("/api/products/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.price").value(1990));
        mvc.perform(get("/api/products/9223372036854775807")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(40401));
        for (String invalid : new String[]{"abc", "0", "-1", "9223372036854775808"})
            mvc.perform(get("/api/products/" + invalid)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void statusOnlyChangesStatusAndAcceptsRepeatedUpdate() throws Exception {
        long id = product(1990, 10);
        for (String state : new String[]{"off", "off", "on"}) {
            mvc.perform(patch("/api/products/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(Map.of("status", state))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(state));
            assertEquals(state, jdbc.queryForObject("SELECT status FROM products WHERE id = ?", String.class, id));
        }
        assertEquals(10, jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, id));
        assertEquals(1990L, jdbc.queryForObject("SELECT price FROM products WHERE id = ?", Long.class, id));
        for (String body : new String[]{"{}", "{\"status\":null}", "{\"status\":\"bad\"}", "{\"status\":1}"})
            mvc.perform(patch("/api/products/" + id + "/status").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
        mvc.perform(patch("/api/products/9223372036854775807/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"off\"}")).andExpect(status().isNotFound());
    }

    @Test
    void hugePageDoesNotOverflowOffset() throws Exception {
        product(100, 1);
        mvc.perform(get("/api/products?page=2147483647&pageSize=50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }
}
