package com.lzu.marketplace.common;

import org.springframework.http.HttpStatus;

/**
 * 业务异常：同时携带 HTTP 状态码和业务码，由全局异常处理器统一转成 ApiResponse。
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final int code;

    public BusinessException(HttpStatus status, int code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /** 参数非法：400 + 40001 */
    public static BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, 40001, message);
    }

    /** 资源不存在：404 + 40401 */
    public static BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, 40401, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public int getCode() {
        return code;
    }
}
