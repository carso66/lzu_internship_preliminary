package com.zyh.marketplace.common;

/**
 * 业务码与 HTTP 状态的唯一映射处（对应 README 第 3 节的约定表）。
 * 本期只定义已用到的码，L1/L2 需要 40901 / 40902 / 40903 时在此追加。
 */
public enum ErrorCode {

    /** 参数不对（缺字段、类型错、超范围、枚举非法） */
    BAD_REQUEST(40001, 400, "参数错误"),
    /** 商品 / 订单不存在 */
    NOT_FOUND(40401, 404, "资源不存在"),
    /**
     * 请求方法不支持（HTTP 405）。
     * 本方向不适用 40001：请求本身格式没错，只是该路径不支持这个方法。
     * 选择以 4 开头的 40004 而不是 5 开头的码，是为了让客户端能仅凭业务码的首位判断
     * 「这是客户端错误、不要重试」，同时与「40001 参数错误」区分开。
     */
    METHOD_NOT_ALLOWED(40004, 405, "请求方法不支持"),
    /** 服务出错（不把堆栈返回给客户端） */
    INTERNAL_ERROR(50000, 500, "服务出错");

    private final int code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
