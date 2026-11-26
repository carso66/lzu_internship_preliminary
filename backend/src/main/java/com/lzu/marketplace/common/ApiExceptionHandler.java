package com.lzu.marketplace.common;

import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** 将控制器及 Spring MVC 抛出的异常转换为统一响应，避免客户端收到默认错误结构。 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // POST 请求体（@RequestBody）校验失败时抛出的异常。
        String message = firstFieldError(ex.getBindingResult().getFieldErrors());
        return handleExceptionInternal(ex, new ApiResponse<>(40001, message, null), headers, status, request);
    }

    // 查询参数绑定（@ModelAttribute）构造/类型转换失败时抛 BindException（如 page=abc）。
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException ex) {
        String message = firstFieldError(ex.getBindingResult().getFieldErrors());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(40001, message, null));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // JSON 解析或类型转换失败时，优先提取字段路径，不直接暴露底层异常全文。
        String message = "请求体不能为空且必须是合法的 JSON 对象";
        if (ex.getCause() instanceof JsonMappingException mapping) {
            String field = mapping.getPath().stream().map(JsonMappingException.Reference::getFieldName)
                    .filter(Objects::nonNull).collect(Collectors.joining("."));
            if (!field.isEmpty()) {
                message = field + ": 类型或数值范围不正确";
            }
        }
        return handleExceptionInternal(ex, new ApiResponse<>(40001, message, null), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // 已包装的响应直接保留，其余 MVC 错误（如地址或方法错误）在此统一包装。
        if (!(body instanceof ApiResponse<?>)) {
            int code = status.is5xxServerError() ? 50000 : status.value() == 404 ? 40401 : 40001;
            String message = status.is5xxServerError() ? "服务异常，请稍后重试" : "请求地址、方法或参数不正确";
            body = new ApiResponse<>(code, message, null);
        }
        return super.handleExceptionInternal(ex, body, headers, status, request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.status()).body(new ApiResponse<>(ex.code(), ex.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        // 完整异常只写入服务端日志，客户端只收到简短提示和业务码 50000。
        log.error("Unexpected API error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(50000, "服务异常，请稍后重试", null));
    }

    // 按字段名排序后取第一个错误，保证同一请求的错误提示稳定可复现。
    private static String firstFieldError(List<FieldError> errors) {
        return errors.stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(ApiExceptionHandler::describeFieldError)
                .findFirst().orElse("请求参数不正确");
    }

    // 类型转换失败（如把 "abc" 传给数字参数）与普通校验失败区分开，提示更明确。
    private static String describeFieldError(FieldError error) {
        String field = error.getField();
        return error.isBindingFailure() ? field + ": 类型或数值格式不正确" : field + ": " + error.getDefaultMessage();
    }
}
