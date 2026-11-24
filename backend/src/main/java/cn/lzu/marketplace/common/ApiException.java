package cn.lzu.marketplace.common;

/**
 * 业务异常。
 * 命中预期内的错误（库存不足、商品不存在…）时直接 throw，
 * 由 GlobalExceptionHandler 统一转成 {code, message}，不用每个接口各写一遍 if。
 */
public class ApiException extends RuntimeException {

    private final int code;
    private final int httpStatus;

    public ApiException(int code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(ErrorCode.BAD_REQUEST, 400, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.NOT_FOUND, 404, message);
    }

    public static ApiException outOfStock(String message) {
        return new ApiException(ErrorCode.OUT_OF_STOCK, 409, message);
    }

    public static ApiException productOff(String message) {
        return new ApiException(ErrorCode.PRODUCT_OFF, 409, message);
    }

    public static ApiException alreadyPaid(String message) {
        return new ApiException(ErrorCode.ALREADY_PAID, 409, message);
    }

    public int getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
