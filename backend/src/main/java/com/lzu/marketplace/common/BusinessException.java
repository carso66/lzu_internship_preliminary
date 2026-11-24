package com.lzu.marketplace.common;

/**
 * 业务异常：同时携带业务码与 HTTP 状态码，交给 GlobalExceptionHandler 统一转成响应体。
 */
public class BusinessException extends RuntimeException {

    private final int code;
    private final int httpStatus;

    public BusinessException(int code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
