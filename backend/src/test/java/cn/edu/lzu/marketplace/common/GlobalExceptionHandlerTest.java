package cn.edu.lzu.marketplace.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公共异常处理模块的测试：仅借用一个测试专用 Controller 触发各类异常，
 * 不依赖任何商品/订单业务代码。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    /** 测试专用接口，只存在于本测试的上下文中 */
    @RestController
    static class TestController {

        record IntValueDto(Integer value) {
        }

        record ValidatedDto(@NotNull @Min(1) Integer quantity, @NotBlank String name) {
        }

        @GetMapping("/_test/business/{code}")
        public ApiResponse<Void> business(@PathVariable int code) {
            throw new BusinessException(ErrorCode.fromCode(code));
        }

        @PostMapping("/_test/int")
        public ApiResponse<Integer> intValue(@RequestBody IntValueDto dto) {
            return ApiResponse.ok(dto.value());
        }

        @PostMapping("/_test/valid")
        public ApiResponse<Void> validated(@Valid @RequestBody ValidatedDto dto) {
            return ApiResponse.ok();
        }

        @PostMapping("/_test/map")
        public ApiResponse<Void> map(@RequestBody Map<String, Object> body) {
            return ApiResponse.ok();
        }

        @GetMapping("/_test/param")
        public ApiResponse<Void> param(@RequestParam String q) {
            return ApiResponse.ok();
        }

        @GetMapping("/_test/path/{id}")
        public ApiResponse<Void> path(@PathVariable int id) {
            return ApiResponse.ok();
        }

        /** 模拟 L0-3 的 pageSize：包装类型 + @NotNull/@Min/@Max，不走 Spring MVC 内置方法校验之外的旁路 */
        @GetMapping("/_test/range")
        public ApiResponse<Integer> range(
                @RequestParam @NotNull @Min(1) @Max(50) Integer page) {
            return ApiResponse.ok(page);
        }

        @GetMapping("/_test/boom")
        public ApiResponse<Void> boom() {
            throw new IllegalStateException("SECRET-内部异常细节");
        }
    }

    // ---------- 业务异常：README 第 3 节的码表逐一核对 ----------

    @Test
    void businessStockInsufficientReturns409And40901() throws Exception {
        mockMvc.perform(get("/_test/business/40901"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.message").value("库存不足"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void businessProductOffSaleReturns409And40902() throws Exception {
        mockMvc.perform(get("/_test/business/40902"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902))
                .andExpect(jsonPath("$.message").value("商品已下架"));
    }

    @Test
    void businessOrderAlreadyPaidReturns409And40903() throws Exception {
        mockMvc.perform(get("/_test/business/40903"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903))
                .andExpect(jsonPath("$.message").value("订单已支付，不可重复支付"));
    }

    @Test
    void businessNotFoundReturns404And40401() throws Exception {
        mockMvc.perform(get("/_test/business/40401"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    // ---------- 整数字段的严格类型：字符串数字 / 小数 / 空串必须拒绝 ----------

    @Test
    void integerFieldAcceptsPlainInteger() throws Exception {
        mockMvc.perform(post("/_test/int")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":1990}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(1990));
    }

    @Test
    void integerFieldRejectsNumericString() throws Exception {
        mockMvc.perform(post("/_test/int")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"1990\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("value")));
    }

    @Test
    void integerFieldRejectsDecimal() throws Exception {
        mockMvc.perform(post("/_test/int")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":1990.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void integerFieldRejectsFloatWithZeroFraction() throws Exception {
        // 1990.0 数学上等于整数，但默认 Jackson 会静默截断，这里同样拒绝
        mockMvc.perform(post("/_test/int")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":1990.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void integerFieldRejectsEmptyString() throws Exception {
        mockMvc.perform(post("/_test/int")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    // ---------- 请求体本身的问题 ----------

    @Test
    void malformedJsonReturns400And40001() throws Exception {
        mockMvc.perform(post("/_test/map")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-a-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("JSON")));
    }

    @Test
    void beanValidationFailureMentionsField() throws Exception {
        // quantity=0 违反 @Min(1)，name 为空违反 @NotBlank
        mockMvc.perform(post("/_test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0,\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("quantity")))
                .andExpect(jsonPath("$.message").value(containsString("name")));
    }

    // ---------- 路径 / 查询参数的问题 ----------

    @Test
    void missingRequiredParamReturns400And40001() throws Exception {
        mockMvc.perform(get("/_test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("q")));
    }

    @Test
    void pathVariableTypeMismatchReturns400And40001() throws Exception {
        mockMvc.perform(get("/_test/path/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("id")));
    }

    @Test
    void unknownPathReturns404WithEnvelope() throws Exception {
        mockMvc.perform(get("/_test/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    // ---------- 兜底 500：不泄露内部信息 ----------

    @Test
    void unexpectedExceptionReturns500And50000WithoutDetails() throws Exception {
        mockMvc.perform(get("/_test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50000))
                .andExpect(jsonPath("$.message").value("服务内部错误"))
                // 异常 message、类名、堆栈帧都不允许出现在响应体里
                .andExpect(content().string(not(containsString("SECRET-内部异常细节"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at cn.edu"))));
    }

    // ---------- 本轮审查新增：HTTP 协议层错误与方法级校验 ----------

    @Test
    void wrongHttpMethodShouldBe405NotFallback500() throws Exception {
        // /_test/param 只支持 GET，用 POST 触发 HttpRequestMethodNotSupportedException
        mockMvc.perform(post("/_test/param"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(40501));
    }

    @Test
    void unsupportedContentTypeShouldBe415() throws Exception {
        // /_test/int 需要 JSON，发 text/plain 触发 HttpMediaTypeNotSupportedException
        mockMvc.perform(post("/_test/int")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(41501));
    }

    @Test
    void unknownJsonFieldIsIgnoredByDefault() throws Exception {
        // 核实 Spring Boot 默认行为：未知字段是否被拒绝（修复前对现状的确认测试，
        // 本轮不改变策略，见审查问题 2）
        mockMvc.perform(post("/_test/int")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":2,\"notAField\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void requestParamOutOfRangeShouldBe40001NotFallback500() throws Exception {
        mockMvc.perform(get("/_test/range").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("page")));
    }

    @Test
    void requestParamOverMaxShouldBe40001NotFallback500() throws Exception {
        mockMvc.perform(get("/_test/range").param("page", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("page")));
    }

    @Test
    void requestParamInValidRangeShouldPass() throws Exception {
        mockMvc.perform(get("/_test/range").param("page", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(10));
    }

    @Test
    void missingRequiredIntegerParamShouldBe40001NotDefault0() throws Exception {
        // 包装类型 + @NotNull：缺参必须报错，而不是被赋默认值
        mockMvc.perform(get("/_test/range"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }
}
