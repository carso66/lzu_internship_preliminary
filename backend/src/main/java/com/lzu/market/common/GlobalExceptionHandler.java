package com.lzu.market.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 把所有异常收敛成统一的 {code,message,data} 结构，堆栈只写日志，不回给客户端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    /** Bean Validation 不通过：把出错字段名拼进 message，评审能直接看出哪个字段错了 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        if (message.isBlank()) {
            message = "参数不合法";
        }
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(Codes.BAD_REQUEST, message));
    }

    /** JSON 里的类型不对（"quantity": "2"、"price": "abc"）→ 40001，不是 500 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(Codes.BAD_REQUEST, "请求体格式错误，请检查字段类型"));
    }

    /** 路径或查询参数类型不对（/api/products/abc、page=abc）→ 40001 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(Codes.BAD_REQUEST, ex.getName() + " 类型不对，请检查"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(Codes.BAD_REQUEST, ex.getParameterName() + " 不能为空"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("未预期异常", ex);
        return ResponseEntity.status(500)
                .body(ApiResponse.fail(Codes.SERVER_ERROR, "服务出错"));
    }
}
