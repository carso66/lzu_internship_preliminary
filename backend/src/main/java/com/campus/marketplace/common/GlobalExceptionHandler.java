package com.campus.marketplace.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：按业务码映射 HTTP 状态 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusinessException(BusinessException e) {
        return ResponseEntity.status(httpStatusOf(e.getCode()))
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    /** 请求体不是合法 JSON（或 body 缺失） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(40001, "请求体不是合法的 JSON 格式"));
    }

    /** 路由不存在：/api/xxx */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(40400, "接口不存在"));
    }

    /** HTTP 方法不支持：GET 打 PATCH-only 接口等 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(40500, "请求方法不被允许"));
    }

    /** 参数类型绑定失败（当前 String 接参已绕开，作为保险） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(40001, "参数类型不匹配"));
    }

    /** 兜底：不把堆栈返回给客户端 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception e) {
        log.error("unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(50000, "服务出错"));
    }

    private HttpStatus httpStatusOf(int code) {
        return switch (code) {
            case 40001 -> HttpStatus.BAD_REQUEST;
            case 40400 -> HttpStatus.NOT_FOUND;            // 接口路由不存在
            case 40500 -> HttpStatus.METHOD_NOT_ALLOWED;   // 请求方法不被允许
            case 40401 -> HttpStatus.NOT_FOUND;
            case 40901, 40902, 40903 -> HttpStatus.CONFLICT;
            case 50000 -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
