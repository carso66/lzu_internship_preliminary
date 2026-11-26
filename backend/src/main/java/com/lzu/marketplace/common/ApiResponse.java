package com.lzu.marketplace.common;

/**
 * 统一响应外层，对应官方接口约定 {@code {code, message, data}}。
 *
 * <p>业务码统一定义在 {@link ErrorCode}（唯一来源）。所有接口无论成功失败都使用该结构。
 *
 * @param code    业务码，0 表示成功
 * @param message 提示文案
 * @param data    业务数据，无数据时为 {@code null}
 * @param <T>     业务数据类型
 */
public record ApiResponse<T>(int code, String message, T data) {

    private static final String OK_MESSAGE = "ok";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.code(), OK_MESSAGE, data);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.code(), errorCode.defaultMessage(), null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.code(), message, null);
    }
}
