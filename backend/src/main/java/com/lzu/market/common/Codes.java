package com.lzu.market.common;

/**
 * 业务码常量，与题目 README 第 3 节一致。
 */
public final class Codes {

    /** 成功 */
    public static final int OK = 0;
    /** 400 参数不对 */
    public static final int BAD_REQUEST = 40001;
    /** 404 商品 / 订单不存在 */
    public static final int NOT_FOUND = 40401;
    /** 409 库存不足 */
    public static final int STOCK_NOT_ENOUGH = 40901;
    /** 409 商品已下架 */
    public static final int PRODUCT_OFF_SHELF = 40902;
    /** 409 订单已支付，不可重复支付 */
    public static final int ORDER_ALREADY_PAID = 40903;
    /** 500 服务出错（不返回堆栈） */
    public static final int SERVER_ERROR = 50000;

    private Codes() {
    }
}
