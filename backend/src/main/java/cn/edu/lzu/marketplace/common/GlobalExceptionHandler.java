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
 * 将 Spring MVC 捕获的业务、参数和协议异常转换为统一响应。
 * 未预期异常返回固定的 50000 提示，异常堆栈仅记录在服务端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ① 业务异常：沿用 Service 提供的业务码及客户端可读提示。
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode ec = ex.getErrorCode();
        log.warn("业务异常 code={} message={}", ec.code(), ex.getMessage());
        return build(ec, ex.getMessage());
    }

    // ② 参数校验与绑定：收集字段提示，统一映射为 40001。
    /** @Valid 校验 @RequestBody 对象失败：message 指出具体字段 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return handleParamInvalid("请求参数校验失败", detail);
    }

    /** 请求参数绑定到对象后产生的绑定或校验错误。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return handleParamInvalid("请求参数校验失败", detail);
    }

    /** 处理 Jakarta Validation 抛出的方法约束异常，提取属性路径末段作为参数名。 */
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

    // ③ JSON 反序列化失败：优先定位出错字段，无法定位时返回请求体格式提示。
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
                return handleParamInvalid("字段 [" + field + "] 取值不合法或类型错误", null);
            }
        }
        return handleParamInvalid("请求体 JSON 格式错误", null);
    }

    // ④ HTTP 协议错误：为不支持的媒体类型和请求方法返回各自的状态与业务码。
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
     * Spring MVC 内建方法校验失败时，汇总查询参数和路径变量的约束提示。
     * 商品和订单列表的分页范围校验由此映射为 40001。
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

    // ⑤ 响应构建：汇总校验提示，由错误码决定响应的 HTTP 状态。
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
