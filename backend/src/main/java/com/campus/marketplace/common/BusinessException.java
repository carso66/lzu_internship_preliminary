package com.campus.marketplace.common;

/**
 * 业务异常：携带业务码，由 GlobalExceptionHandler 映射为 HTTP 状态。
 * 例如：throw new BusinessException(40001, "title 不能为空");
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
