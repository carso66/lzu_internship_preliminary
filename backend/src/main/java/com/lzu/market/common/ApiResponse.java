package com.lzu.market.common;

/**
 * 统一响应外层：{"code":0,"message":"ok","data":null}
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
