package cn.edu.lzu.marketplace.common;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局异常处理：把所有错误统一收敛为 {code, message, data} 结构。
 *
 * <p>业务码与 HTTP 状态的对应严格遵循 README 第 3 节；
 * 未预期异常一律 50000 + 固定文案，堆栈只写日志，不下发客户端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：HTTP 状态与业务码由 ErrorCode 决定，message 为面向客户端的提示 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode ec = ex.getErrorCode();
        log.warn("业务异常 code={} message={}", ec.code(), ex.getMessage());
        return build(ec, ex.getMessage());
    }

    /** @Valid 校验 @RequestBody 对象失败：message 指出具体字段 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return handleParamInvalid("请求参数校验失败", detail);
    }

    /** 查询参数对象绑定 + @Valid 失败（如 @ParameterObject 风格的 DTO） */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return handleParamInvalid("请求参数校验失败", detail);
    }

    /** @Validated 方法级参数校验失败（如 @RequestParam 上的 @Min/@Max） */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> {
                    String path = v.getPropertyPath().toString();
                    return path.substring(path.lastIndexOf('.') + 1) + ": " + v.getMessage();
                })
                .collect(Collectors.joining("; "));
        return handleParamInvalid("请求参数校验失败", detail);
    }

    /** 必填查询参数缺失 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return handleParamInvalid("缺少必填参数", ex.getParameterName());
    }

    /** 路径变量/查询参数类型转换失败，如 /api/products/abc */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return handleParamInvalid("参数类型错误", ex.getName());
    }

    /** 请求体 JSON 格式错误、字段类型错误（含整数字段收到字符串/小数，见 JacksonConfig） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof InvalidFormatException ife) {
            String field = describePath(ife);
            return handleParamInvalid("字段 [" + field + "] 类型错误，期望 "
                    + ife.getTargetType().getSimpleName(), null);
        }
        if (cause instanceof JsonMappingException jme) {
            String field = describePath(jme);
            if (!field.isEmpty()) {
                // 不特指整数：任何类型的字段都可能有取值/类型问题
                return handleParamInvalid("字段 [" + field + "] 取值不合法或类型错误", null);
            }
        }
        return handleParamInvalid("请求体 JSON 格式错误", null);
    }

    /** Content-Type 不支持：按 HTTP 标准语义返回 415 + 自定码 41501（见 ErrorCode 注释） */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        log.warn("Content-Type 不支持: {}", ex.getContentType());
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.defaultMessage());
    }

    /**
     * 请求方法错误（如 GET 资源用 POST 调用）：405 + 自定码 40501。
     * 若不单独处理，会落入下方 Exception 兜底被误判为 500 + 50000。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String message = ErrorCode.METHOD_NOT_ALLOWED.defaultMessage();
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
            message = message + "，该资源支持: " + supported;
        }
        log.warn("请求方法不支持: {}", ex.getMethod());
        return build(ErrorCode.METHOD_NOT_ALLOWED, message);
    }

    /**
     * Spring Framework 6.1+ 内建方法级校验：@RequestParam/@PathVariable 参数上的
     * @Min/@Max/@NotNull 等约束失败时抛 HandlerMethodValidationException。
     * 若不单独处理，会落入 Exception 兜底把非法参数误判为 500。
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        String detail = ex.getParameterValidationResults().stream()
                .map(GlobalExceptionHandler::describeParameterResult)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("; "));
        return handleParamInvalid("请求参数校验失败", detail);
    }

    /** 未匹配到任何接口的路径：404 + 40401，保持响应结构统一 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        log.warn("未匹配的路径: /{}", ex.getResourcePath());
        return build(ErrorCode.RESOURCE_NOT_FOUND, "资源不存在");
    }

    /** 兜底：任何未预期异常 → 500 + 50000，只回固定文案，堆栈进日志 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("未处理的服务端异常", ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    private ResponseEntity<ApiResponse<Void>> handleParamInvalid(String what, String detail) {
        String message = (detail == null || detail.isEmpty()) ? what : what + ": " + detail;
        log.warn("参数错误: {}", message);
        return build(ErrorCode.PARAM_INVALID, message);
    }

    private static String describeParameterResult(ParameterValidationResult result) {
        String name = result.getMethodParameter().getParameterName();
        String messages = result.getResolvableErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(","));
        return (name == null ? "参数" : name) + ": " + messages;
    }

    private static String describePath(JsonMappingException ex) {
        return ex.getPath().stream()
                .map(Reference::getFieldName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("."));
    }

    private static ResponseEntity<ApiResponse<Void>> build(ErrorCode ec, String message) {
        return ResponseEntity.status(ec.httpStatus())
                .body(new ApiResponse<>(ec.code(), message, null));
    }
}
