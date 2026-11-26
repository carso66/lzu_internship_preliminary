package com.lzu.marketplace.auth;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 测试共用：通过真实登录接口换取 Bearer token。
 *
 * <p>刻意走 HTTP 登录而不是直接调 {@link AuthService}：受保护接口的测试
 * 需要的是一个「从接口完整签发出来」的 token，这样链路（Controller →
 * Service → 拦截器校验）全部被走到，不是只测了半个系统。
 *
 * <p>与 {@link AuthService} 的常量对应：账号写死为 admin / market123。
 */
public final class AuthTestSupport {

    public static final String USERNAME = AuthService.USERNAME;
    public static final String PASSWORD = AuthService.PASSWORD;

    private AuthTestSupport() {
    }

    /** 登录并返回 token；断言 200 + code=0，任何失败直接让测试挂掉。 */
    public static String login(MockMvc mockMvc, com.fasterxml.jackson.databind.ObjectMapper objectMapper)
            throws Exception {
        String resp = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(resp).path("data").path("token").asText();
        if (token.isBlank()) {
            throw new IllegalStateException("登录响应缺少 token: " + resp);
        }
        return token;
    }

    /** 标准请求头值。 */
    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
