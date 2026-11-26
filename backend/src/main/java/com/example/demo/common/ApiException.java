package com.example.demo.common;

import org.springframework.http.HttpStatus;

/**
 * 业务异常：由 {@link GlobalExceptionHandler} 转成统一响应，带 HTTP 状态与业务码。
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final int code;

	public ApiException(HttpStatus status, int code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public int getCode() {
		return code;
	}

	/** 400 + 40001：参数校验失败 */
	public static ApiException badRequest(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, ApiResponse.CODE_BAD_REQUEST, message);
	}

	/** 404 + 40401：资源不存在 */
	public static ApiException notFound(String message) {
		return new ApiException(HttpStatus.NOT_FOUND, ApiResponse.CODE_NOT_FOUND, message);
	}

	/**
	 * 409 + 指定业务码：请求本身合法，但当前资源状态不允许（库存不足 / 已下架 / 已支付）。
	 * 用一个工厂方法承载 40901、40902、40903，避免为每种冲突各写一个方法。
	 */
	public static ApiException conflict(int code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}

	/** 409 + 40901：库存不足 */
	public static ApiException stockNotEnough() {
		return conflict(ApiResponse.CODE_CONFLICT_STOCK, "库存不足");
	}

	/** 409 + 40902：商品已下架 */
	public static ApiException productOffShelf() {
		return conflict(ApiResponse.CODE_CONFLICT_OFF_SHELF, "商品已下架");
	}
}
