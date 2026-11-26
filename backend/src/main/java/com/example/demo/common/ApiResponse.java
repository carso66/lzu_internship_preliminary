package com.example.demo.common;

/**
 * 统一响应体：所有接口成功或失败都返回 code / message / data，失败时 data 为 null。
 */
public record ApiResponse<T>(int code, String message, T data) {

	public static final int CODE_OK = 0;
	public static final int CODE_BAD_REQUEST = 40001;
	public static final int CODE_INVALID_JSON = 40002;
	public static final int CODE_NOT_FOUND = 40401;
	public static final int CODE_CONFLICT_STOCK = 40901;
	public static final int CODE_CONFLICT_OFF_SHELF = 40902;
	public static final int CODE_CONFLICT_PAID = 40903;
	public static final int CODE_SERVER_ERROR = 50000;

	private static final String OK_MESSAGE = "ok";

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(CODE_OK, OK_MESSAGE, data);
	}

	public static <T> ApiResponse<T> fail(int code, String message) {
		return new ApiResponse<>(code, message, null);
	}
}
