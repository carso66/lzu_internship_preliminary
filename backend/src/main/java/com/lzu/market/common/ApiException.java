package com.lzu.market.common;

/**
 * 业务异常。httpStatus 决定 HTTP 状态码，code 决定响应体里的业务码。
 */
public class ApiException extends RuntimeException {

    private final int httpStatus;
    private final int code;

    public ApiException(int httpStatus, int code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public int getCode() {
        return code;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, Codes.BAD_REQUEST, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, Codes.NOT_FOUND, message);
    }

    public static ApiException stockNotEnough() {
        return new ApiException(409, Codes.STOCK_NOT_ENOUGH, "库存不足");
    }

    public static ApiException productOffShelf() {
        return new ApiException(409, Codes.PRODUCT_OFF_SHELF, "商品已下架");
    }

    public static ApiException orderAlreadyPaid() {
        return new ApiException(409, Codes.ORDER_ALREADY_PAID, "订单已支付，不可重复支付");
    }
}
