package com.lzu.marketplace.common;

/**
 * 统一响应业务码。HTTP 状态码与业务码的对应关系见题目约定。
 */
public final class ApiCode {

    public static final int OK = 0;
    public static final int BAD_REQUEST = 40001;
    public static final int ENDPOINT_NOT_FOUND = 40400;
    public static final int NOT_FOUND = 40401;
    public static final int METHOD_NOT_ALLOWED = 40500;
    public static final int OUT_OF_STOCK = 40901;
    public static final int PRODUCT_OFF_SHELF = 40902;
    public static final int ORDER_ALREADY_PAID = 40903;
    public static final int UNSUPPORTED_MEDIA_TYPE = 41500;
    public static final int INTERNAL_ERROR = 50000;

    private ApiCode() {
    }
}
