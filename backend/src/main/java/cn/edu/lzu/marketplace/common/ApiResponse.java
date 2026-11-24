package cn.edu.lzu.marketplace.common;

/**
 * 统一响应结构：{ "code": 0, "message": "ok", "data": ... }
 *
 * @param code    业务码，0 表示成功；非 0 场景见 README 第 3 节接口约定
 * @param message 提示信息
 * @param data    业务数据，无数据时为 null
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null);
    }
}
