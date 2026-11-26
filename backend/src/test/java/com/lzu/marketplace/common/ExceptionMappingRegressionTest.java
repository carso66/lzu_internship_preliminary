package com.lzu.marketplace.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzu.marketplace.auth.AuthTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 异常映射盲区回归测试（P08 修复）。
 *
 * <p>覆盖两个此前会把「客户端错误」误报成「服务端故障」/「空响应」的场景。
 *
 * <p><b>鉴权注意</b>：拦截器在进入 Controller 之前执行，无 token 的请求会先
 * 拿到 401，根本到不了内容协商阶段。所以这几个用例都带有效 token——
 * 它们测的是「过了鉴权的请求发错格式」时的异常映射，鉴权拒绝路径归 {@code AuthTest}。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExceptionMappingRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        token = AuthTestSupport.login(mockMvc, objectMapper);
    }

    @Test
    @DisplayName("Content-Type 不支持 → 415 / 41500，不是 50000")
    void unsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(41500))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("415 响应仍是统一 JSON 结构")
    void unsupportedMediaTypeKeepsEnvelope() throws Exception {
        var result = mockMvc.perform(post("/api/products")
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<x/>"))
                .andExpect(status().isUnsupportedMediaType())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"code\""));
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"message\""));
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"data\""));
    }

    @Test
    @DisplayName("createdAt 序列化为毫秒精度 ISO-8601")
    void createdAtMillisecondPrecision() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", AuthTestSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"price\":100,\"stock\":1," +
                                 "\"category\":\"文具\",\"sellerName\":\"铺\"}"))
                .andExpect(status().isCreated())
                // yyyy-MM-ddTHH:mm:ss.SSS —— 恰好 3 位小数
                .andExpect(jsonPath("$.data.createdAt")
                        .value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")));
    }
}
