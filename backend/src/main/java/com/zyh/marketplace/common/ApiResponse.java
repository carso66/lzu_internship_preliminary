package com.zyh.marketplace.common;

/**
 * 统一响应结构：所有接口（成功 / 失败）都返回 code、message、data 三个顶层字段。
 *
 * @param code    业务码，0 表示成功
 * @param message 文案
 * @param data    业务数据，无数据时为 null
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static final int CODE_OK = 0;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(CODE_OK, "ok", data);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        // 失败时 data 显式为 null，保证三个顶层字段始终存在
        return new ApiResponse<>(code, message, null);
    }
}
