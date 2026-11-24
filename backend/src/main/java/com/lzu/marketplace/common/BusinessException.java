package com.lzu.marketplace.common;

import org.springframework.http.HttpStatus;

/** 已知的业务失败，由全局异常处理器转换为统一响应。 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final int code;

    public BusinessException(HttpStatus status, int code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public int getCode() {
        return code;
    }
}
