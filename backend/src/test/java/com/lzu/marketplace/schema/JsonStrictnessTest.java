package com.lzu.marketplace.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JSON 严格类型校验与查询参数转换的验收测试。
 *
 * <p>本阶段不实现业务接口，因此用<b>仅测试用</b>的回显控制器，走真实的
 * Spring MVC 消息转换链路，验证 {@code spring.jackson.*} 配置确实生效，
 * 而不是仅仅"配置项写对了"。
 *
 * <p>同时验证：关闭标量强转<b>不影响</b> URL 查询参数（{@code ?page=1}）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(JsonStrictnessTest.ProbeController.class)
class JsonStrictnessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ---------- JSON 请求体：严格整数 ----------

    @Test
    @DisplayName("quantity=2 合法")
    void integerAccepted() throws Exception {
        postJson("{\"quantity\":2}").andExpect(status().isOk());
    }

    @Test
    @DisplayName("quantity=\"2\"（字符串）被拒绝")
    void stringRejected() throws Exception {
        postJson("{\"quantity\":\"2\"}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("quantity=2.5（浮点）被拒绝")
    void floatRejected() throws Exception {
        postJson("{\"quantity\":2.5}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("quantity=2.0（整值浮点）同样被拒绝")
    void wholeFloatRejected() throws Exception {
        postJson("{\"quantity\":2.0}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("quantity=true（布尔）被拒绝")
    void booleanRejected() throws Exception {
        postJson("{\"quantity\":true}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("quantity=null 被拒绝")
    void nullRejected() throws Exception {
        postJson("{\"quantity\":null}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("quantity 超出 int 范围被拒绝")
    void overflowRejected() throws Exception {
        postJson("{\"quantity\":2147483648}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("price / stock 同样不接受字符串")
    void priceAndStockStrict() throws Exception {
        postJson("{\"price\":\"1990\",\"stock\":\"10\"}").andExpect(status().isBadRequest());
    }

    // ---------- 查询参数：不受影响 ----------

    @Test
    @DisplayName("?page=1 正常（JSON 严格化不影响 URL 参数）")
    void queryParamStillWorks() throws Exception {
        mockMvc.perform(post("/test/probe/query").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    @DisplayName("?page=2.5 被拒绝（非法整数参数，拒绝才是正确行为）")
    void nonIntegerQueryParamRejected() throws Exception {
        mockMvc.perform(post("/test/probe/query").param("page", "2.5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("?page=abc 被拒绝")
    void textQueryParamRejected() throws Exception {
        mockMvc.perform(post("/test/probe/query").param("page", "abc"))
                .andExpect(status().isBadRequest());
    }

    // ---------- 配置项确实绑定到了 ObjectMapper ----------

    @Test
    @DisplayName("ObjectMapper 已应用 allow-coercion-of-scalars=false 与 accept-float-as-int=false")
    void objectMapperFeaturesApplied() {
        com.fasterxml.jackson.databind.DeserializationConfig cfg = objectMapper.getDeserializationConfig();
        org.junit.jupiter.api.Assertions.assertFalse(
                cfg.isEnabled(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS),
                "ALLOW_COERCION_OF_SCALARS 应被关闭");
        org.junit.jupiter.api.Assertions.assertFalse(
                cfg.isEnabled(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT),
                "ACCEPT_FLOAT_AS_INT 应被关闭");
    }

    // ---------- helpers ----------

    private org.springframework.test.web.servlet.ResultActions postJson(String body) throws Exception {
        return mockMvc.perform(post("/test/probe/int")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** 仅测试用：回显整数入参，不构成任何业务接口。 */
    @TestConfiguration
    @RestController
    static class ProbeController {

        @PostMapping("/test/probe/int")
        public java.util.Map<String, Object> echo(@jakarta.validation.Valid @RequestBody IntPayload p) {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("quantity", p.quantity);
            return m;
        }

        @PostMapping("/test/probe/query")
        public java.util.Map<String, Object> query(@RequestParam int page) {
            return java.util.Map.of("page", page);
        }
    }

    /** 包装类型 + 校验注解，对应后续真实 DTO 的写法。 */
    static class IntPayload {
        @jakarta.validation.constraints.NotNull
        @jakarta.validation.constraints.Min(1)
        public Integer quantity;

        public Long price;
        public Integer stock;
    }
}
