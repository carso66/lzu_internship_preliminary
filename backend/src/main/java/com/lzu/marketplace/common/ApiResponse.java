package com.lzu.marketplace.common;

/** 统一响应结构；T 表示 data 的具体类型，例如商品信息或空值。 */
public record ApiResponse<T>(int code, String message, T data) {

    // 业务码 0 表示成功；HTTP 状态码由控制器另行设置。
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "ok", data);
    }
}
