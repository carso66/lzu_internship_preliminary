package com.zyh.marketplace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理专项：客户端错误一律 4xx + 业务码；服务端错误 500 + 50000 且不泄漏堆栈。
 */
class GlobalExceptionHandlingTest extends ApiTestSupport {

    @Test
    @DisplayName("服务端异常 → 500 + 50000，且响应体不含异常细节")
    void runtimeExceptionMapsTo500WithoutLeak() throws Exception {
        String body = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new BrokenController())
                .setControllerAdvice(new com.zyh.marketplace.exception.GlobalExceptionHandler())
                .build()
                .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/broken"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50000))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("jdbc:h2")
                .doesNotContain("IllegalStateException")
                .doesNotContain("内部细节");
    }

    @Test
    @DisplayName("非法查询参数不会引发 5xx")
    void invalidQueryParamIsNot5xx() throws Exception {
        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/products")
                                .queryParam("keyword", "\uFFFD\uFFFD"))
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isLessThan(500);
    }

    @Test
    @DisplayName("Content-Type 不支持（application/xml）→ 400 + 40001（不返回 500）")
    void unsupportedMediaType() throws Exception {
        // 控制器参数为 String，Spring 对多数 Content-Type 仍会读取 body；
        // 因此这里只断言「不是 5xx，且仍是 40001 统一结构」，不限定文案。
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/products")
                        .contentType(org.springframework.http.MediaType.APPLICATION_XML)
                        .content("<product/>"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @RestController
    @RequestMapping("/broken")
    static class BrokenController {
        @GetMapping
        String boom() {
            throw new IllegalStateException("内部细节：数据库连接串 jdbc:h2:xxx 不应出现在响应中");
        }
    }
}