package com.lzu.marketplace.common;

/**
 * 所有接口统一的响应结构：{@code code}、{@code message}、{@code data}。
 *
 * @param <T> 业务数据类型
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ApiCode.OK, "ok", data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
