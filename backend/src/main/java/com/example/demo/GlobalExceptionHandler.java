package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：保证异常响应也使用统一 JSON 结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.failure(ex.getCode(), ex.getMessage()));
    }

    /**
     * 请求体不是合法 JSON、缺少请求体等情况统一按参数错误返回。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableRequest(HttpMessageNotReadableException ex) {
        log.debug("请求体解析失败", ex);
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(BizException.BAD_REQUEST_CODE, "请求体格式错误，必须是合法的 JSON"));
    }
}
