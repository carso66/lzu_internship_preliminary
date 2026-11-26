package com.lzu.marketplace.common;

import org.springframework.http.HttpStatus;

/**
 * 业务码与 HTTP 状态的绑定表。
 *
 * <p>业务码取值来自官方接口约定（backend/README.md §3），HTTP 状态与题目表格一致。
 * 把两者绑定在一个枚举里，避免在每个抛异常的地方手写两次。
 *
 * <p>题目未定义、由本项目自行决定的码（如「接口不存在」「JSON 格式错误」）在下方
 * 单独标注为 <b>自主</b>，并会在 SUBMISSION.md 中说明。
 */
public enum ErrorCode {

    /** 成功。HTTP 200 / 201，由具体接口决定。 */
    SUCCESS(0, HttpStatus.OK, "ok"),

    /** 参数不对：缺字段、类型错、超范围、枚举非法。题目 §3 */
    BAD_REQUEST(40001, HttpStatus.BAD_REQUEST, "请求参数不合法"),

    /** 商品 / 订单不存在。题目 §3 */
    NOT_FOUND(40401, HttpStatus.NOT_FOUND, "资源不存在"),

    /** 库存不足。题目 §3 */
    STOCK_INSUFFICIENT(40901, HttpStatus.CONFLICT, "库存不足"),

    /** 商品已下架。题目 §3 */
    PRODUCT_OFF_SHELF(40902, HttpStatus.CONFLICT, "商品已下架"),

    /** 订单已支付，不可重复支付。题目 §3 */
    ORDER_ALREADY_PAID(40903, HttpStatus.CONFLICT, "订单已支付，请勿重复支付"),

    /** 服务出错，不向客户端暴露堆栈。题目 §3 */
    SERVER_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "服务出错"),

    /**
     * 接口不存在。<b>自主</b>：题目码表未定义，沿用 404 状态与 404 段业务码。
     */
    API_NOT_FOUND(40400, HttpStatus.NOT_FOUND, "接口不存在"),

    /**
     * 请求体不是合法 JSON。<b>自主</b>：题目码表未定义，归入 400 段。
     */
    MALFORMED_JSON(40002, HttpStatus.BAD_REQUEST, "请求体不是合法的 JSON"),

    /**
     * HTTP 方法不支持。<b>自主</b>：题目码表未定义。
     */
    METHOD_NOT_ALLOWED(40500, HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"),

    /**
     * 请求的 Content-Type 不受支持。<b>自主</b>：题目码表未定义。
     *
     * <p>例如向只接受 JSON 的接口发送 {@code text/plain}。
     * 若不单独处理，会被兜底的 {@code Exception} 分支吞成 50000，
     * 把「客户端用错格式」误报成「服务端故障」。
     */
    UNSUPPORTED_MEDIA_TYPE(41500, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请求的 Content-Type 不支持"),

    /**
     * 无法产生客户端可接受的响应。<b>自主</b>：题目码表未定义。
     *
     * <p>例如 {@code Accept: application/xml}。若不处理，Spring 会返回空响应体，
     * 破坏「所有接口都返回统一 JSON 结构」的约定。
     */
    NOT_ACCEPTABLE(40600, HttpStatus.NOT_ACCEPTABLE, "无法产生客户端可接受的响应类型"),

    /**
     * 缺少 Authorization: Bearer token 请求头。<b>自主</b>：题目 §3 允许鉴权失败使用
     * 自定业务码（仅当选择 L2-11 时适用），本项目沿 401 段自定 40101。
     */
    UNAUTHORIZED_MISSING_TOKEN(40101, HttpStatus.UNAUTHORIZED, "缺少 Authorization: Bearer <token> 请求头"),

    /**
     * token 无效或已失效（不是 Bearer 格式 / 未签发 / 服务已重启）。<b>自主</b>，沿 401 段自定 40102。
     */
    UNAUTHORIZED_INVALID_TOKEN(40102, HttpStatus.UNAUTHORIZED, "token 无效或已失效");

    private final int code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
