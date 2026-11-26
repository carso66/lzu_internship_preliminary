package com.lzu.marketplace.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一响应结构：所有接口都返回 code、message、data 三个字段。
 *
 * <p>data 为 null 时字段也必须保留，所以这里显式写 ALWAYS，
 * 这样将来即使把全局序列化配置改成忽略 null，也不会把 data 吞掉。</p>
 *
 * @param code    业务码，0 表示成功
 * @param message 提示文案
 * @param data    业务数据，可以为 null
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(int code, String message, T data) {

    /** 成功，带业务数据。 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    /** 成功，但没有数据可返回（data 为 null）。 */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    /** 失败：HTTP 状态码之外，再用业务码区分具体错误。 */
    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
