package com.lzu.marketplace.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公共层（统一响应 / 异常映射 / 参数校验）的验收测试。
 *
 * <p>本阶段不实现业务接口，使用<b>仅测试用</b>控制器走真实 Spring MVC 链路。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CommonLayerTest.ProbeController.class)
class CommonLayerTest {

    @Autowired
    private MockMvc mockMvc;

    // ================= ApiResponse 结构 =================

    @Test
    @DisplayName("成功响应始终包含 code/message/data")
    void successResponseShape() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("失败响应同样包含三个顶层字段，data 为 null")
    void failureResponseShape() throws Exception {
        mockMvc.perform(get("/test/probe/business/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ================= 业务异常 → HTTP 状态映射 =================

    @Test
    @DisplayName("库存不足 → 409 / 40901")
    void stockInsufficient() throws Exception {
        mockMvc.perform(get("/test/probe/business/stock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    @DisplayName("商品已下架 → 409 / 40902")
    void productOffShelf() throws Exception {
        mockMvc.perform(get("/test/probe/business/off-shelf"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902));
    }

    @Test
    @DisplayName("订单已支付 → 409 / 40903")
    void orderAlreadyPaid() throws Exception {
        mockMvc.perform(get("/test/probe/business/paid"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));
    }

    @Test
    @DisplayName("资源不存在 → 404 / 40401")
    void notFound() throws Exception {
        mockMvc.perform(get("/test/probe/business/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    // ================= 请求体错误 =================

    @Test
    @DisplayName("非法 JSON → 400 / 40002")
    void malformedJson() throws Exception {
        mockMvc.perform(post("/test/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    @DisplayName("缺失字段 → 400 / 40001，message 指明字段")
    void missingField() throws Exception {
        mockMvc.perform(post("/test/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stock\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("title")));
    }

    @Test
    @DisplayName("字段校验失败（@Min 越界）→ 400 / 40001")
    void validationMinViolation() throws Exception {
        mockMvc.perform(post("/test/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"quantity\":0,\"stock\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("quantity")));
    }

    // ================= Jackson 严格类型（重点） =================

    @Test
    @DisplayName("整数字段收到字符串 \"2\" → 400，不静默转换")
    void stringIsNotAnInteger() throws Exception {
        assertRejected("{\"title\":\"t\",\"quantity\":\"2\",\"stock\":5}");
    }

    @Test
    @DisplayName("整数字段收到小数 2.5 → 400")
    void decimalIsNotAnInteger() throws Exception {
        assertRejected("{\"title\":\"t\",\"quantity\":2.5,\"stock\":5}");
    }

    @Test
    @DisplayName("整数字段收到整值小数 2.0 → 400（仍非整数写法）")
    void wholeDecimalIsNotAnInteger() throws Exception {
        assertRejected("{\"title\":\"t\",\"quantity\":2.0,\"stock\":5}");
    }

    @Test
    @DisplayName("整数字段收到布尔 true → 400")
    void booleanIsNotAnInteger() throws Exception {
        assertRejected("{\"title\":\"t\",\"quantity\":true,\"stock\":5}");
    }

    @Test
    @DisplayName("整数字段收到 null → 400（@NotNull）")
    void nullIsNotAnInteger() throws Exception {
        assertRejected("{\"title\":\"t\",\"quantity\":null,\"stock\":5}");
    }

    @Test
    @DisplayName("整数超出 int 范围 → 400")
    void overflowIsRejected() throws Exception {
        assertRejected("{\"title\":\"t\",\"quantity\":2147483648,\"stock\":5}");
    }

    @Test
    @DisplayName("合法整数 2 通过")
    void plainIntegerAccepted() throws Exception {
        mockMvc.perform(post("/test/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"quantity\":2,\"stock\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.quantity").value(2));
    }

    // ================= 查询参数 =================

    @Test
    @DisplayName("查询参数 ?page=1 正常（JSON 严格化不影响 URL 参数）")
    void queryParamOk() throws Exception {
        mockMvc.perform(get("/test/probe/query").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("查询参数 ?page=abc → 400 / 40001")
    void queryParamNotNumber() throws Exception {
        mockMvc.perform(get("/test/probe/query").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("查询参数 ?page=2.5 → 400 / 40001")
    void queryParamDecimal() throws Exception {
        mockMvc.perform(get("/test/probe/query").param("page", "2.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("缺少必填查询参数 → 400 / 40001")
    void queryParamMissing() throws Exception {
        mockMvc.perform(get("/test/probe/query"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    // ================= 路由 =================

    @Test
    @DisplayName("不存在的路径 → 404 / 40400，结构统一")
    void unknownPath() throws Exception {
        mockMvc.perform(get("/api/definitely-not-exists"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("方法不支持 → 405 / 40500")
    void methodNotAllowed() throws Exception {
        mockMvc.perform(post("/api/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(40500));
    }

    // ================= 兜底 500：不得泄漏内部信息 =================

    @Test
    @DisplayName("未预期异常 → 500 / 50000，且不泄漏异常消息/SQL/堆栈")
    void unexpectedErrorLeaksNothing() throws Exception {
        String body = mockMvc.perform(get("/test/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50000))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // 服务端内部细节一律不得出现在响应体中
        assertFalse(body.contains("SECRET_INTERNAL_DETAIL"), "响应体泄漏了异常消息: " + body);
        assertFalse(body.contains("java."), "响应体泄漏了类名/堆栈: " + body);
        assertFalse(body.contains("SQL"), "响应体泄漏了 SQL: " + body);
        assertFalse(body.contains("Exception"), "响应体泄漏了异常类型: " + body);
    }

    // ================= helpers =================

    private void assertRejected(String json) throws Exception {
        mockMvc.perform(post("/test/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    // ================= 仅测试用控制器（不进入生产包） =================

    @TestConfiguration
    @RestController
    static class ProbeController {

        @PostMapping("/test/probe/body")
        public ApiResponse<Map<String, Object>> body(@Valid @RequestBody Payload p) {
            Map<String, Object> m = new HashMap<>();
            m.put("title", p.title);
            m.put("quantity", p.quantity);
            m.put("stock", p.stock);
            return ApiResponse.ok(m);
        }

        @GetMapping("/test/probe/query")
        public ApiResponse<Map<String, Object>> query(@RequestParam int page) {
            return ApiResponse.ok(Map.of("page", page));
        }

        @GetMapping("/test/probe/business/{kind}")
        public ApiResponse<Void> business(@org.springframework.web.bind.annotation.PathVariable String kind) {
            switch (kind) {
                case "not-found" -> throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
                case "stock" -> throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
                case "off-shelf" -> throw new BusinessException(ErrorCode.PRODUCT_OFF_SHELF);
                case "paid" -> throw new BusinessException(ErrorCode.ORDER_ALREADY_PAID);
                default -> throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
        }

        @GetMapping("/test/probe/boom")
        public ApiResponse<Void> boom() {
            throw new IllegalStateException("SECRET_INTERNAL_DETAIL: SELECT * FROM secret_table");
        }
    }

    /** 包装类型 + 校验注解，模拟后续真实 DTO 写法。 */
    static class Payload {
        @NotBlank(message = "不能为空")
        public String title;

        @NotNull(message = "不能为空")
        @Min(value = 1, message = "必须大于等于 1")
        @Max(value = 9999, message = "必须小于等于 9999")
        public Integer quantity;

        @NotNull(message = "不能为空")
        @Min(value = 0, message = "不能为负数")
        public Integer stock;
    }
}
