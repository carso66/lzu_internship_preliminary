package com.lzu.marketplace.common;

/** 可预期的业务失败，交给统一异常处理器转换成 HTTP 状态和业务码。 */
public class BusinessException extends RuntimeException {
    private final int status;
    private final int code;

    public BusinessException(int status, int code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public int code() { return code; }

    public static BusinessException badRequest(String message) {
        return new BusinessException(400, 40001, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(404, 40401, message);
    }
}
