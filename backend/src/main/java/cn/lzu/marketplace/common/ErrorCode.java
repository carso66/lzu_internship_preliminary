package cn.lzu.marketplace.common;

/**
 * 业务码。
 * 题目规定成功是 0（不是 200），HTTP 状态码另外对应，两个都要对。
 */
public final class ErrorCode {

    /** 成功 —— HTTP 200 / 201 */
    public static final int OK = 0;

    /** 参数不对 —— HTTP 400 */
    public static final int BAD_REQUEST = 40001;

    /** 商品 / 订单不存在 —— HTTP 404 */
    public static final int NOT_FOUND = 40401;

    /** 库存不足 —— HTTP 409 */
    public static final int OUT_OF_STOCK = 40901;

    /** 商品已下架 —— HTTP 409 */
    public static final int PRODUCT_OFF = 40902;

    /** 订单已支付 —— HTTP 409 */
    public static final int ALREADY_PAID = 40903;

    /** 服务出错 —— HTTP 500，不把堆栈返回给客户端 */
    public static final int SERVER_ERROR = 50000;

    private ErrorCode() {
    }
}
