package com.zyh.marketplace.exception;

import com.zyh.marketplace.common.ErrorCode;

/**
 * 业务异常：只带业务码和面向用户的文案，绝不放堆栈或 SQL 细节。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
