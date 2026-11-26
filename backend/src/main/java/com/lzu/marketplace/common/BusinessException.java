package com.lzu.marketplace.common;

/**
 * 业务异常：携带业务码与 HTTP 状态，由 {@link GlobalExceptionHandler} 统一转换。
 *
 * <p>业务上「可预期」的失败一律抛这个异常，不要抛裸的 RuntimeException，
 * 否则会被当成 50000 处理。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
