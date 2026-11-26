package cn.edu.lzu.marketplace.common;

import org.springframework.http.HttpStatus;

/**
 * 业务码与 HTTP 状态码的对应关系，严格遵循 README 第 3 节接口约定。
 *
 * <p>成功时 HTTP 为 200 或 201（创建类接口由 Controller 自行指定），
 * 其余场景 HTTP 状态由本枚举唯一确定，异常处理器据此返回。
 */
public enum ErrorCode {

    // 核心业务码：供业务层抛出异常及 Web 层生成响应。
    /** 成功；实际 HTTP 为 200/201，由具体接口决定 */
    SUCCESS(0, "ok", HttpStatus.OK),

    /** 参数不对：缺字段、类型错、超范围、枚举非法 */
    PARAM_INVALID(40001, "参数不合法", HttpStatus.BAD_REQUEST),

    /** 商品 / 订单不存在 */
    RESOURCE_NOT_FOUND(40401, "资源不存在", HttpStatus.NOT_FOUND),

    /** 库存不足 */
    STOCK_INSUFFICIENT(40901, "库存不足", HttpStatus.CONFLICT),

    /** 商品已下架 */
    PRODUCT_OFF_SALE(40902, "商品已下架", HttpStatus.CONFLICT),

    /** 为支付场景保留的错误码 */
    ORDER_ALREADY_PAID(40903, "订单已支付，不可重复支付", HttpStatus.CONFLICT),

    /** 服务出错：仅返回固定文案，不包含堆栈等内部细节 */
    INTERNAL_ERROR(50000, "服务内部错误", HttpStatus.INTERNAL_SERVER_ERROR),

    // 协议错误码：按 HTTP 405、415 的语义单独映射，与接口参数校验区分。

    /** HTTP 方法不被支持，如 GET 接口用 POST 调用 */
    METHOD_NOT_ALLOWED(40501, "请求方法不被支持", HttpStatus.METHOD_NOT_ALLOWED),

    /** 请求 Content-Type 不被支持（JSON 接口收到非 JSON 请求体） */
    UNSUPPORTED_MEDIA_TYPE(41501, "请求 Content-Type 不被支持", HttpStatus.UNSUPPORTED_MEDIA_TYPE);

    private final int code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    /** 按业务码反查枚举；未定义的码抛 IllegalArgumentException，防止误用 */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode ec : values()) {
            if (ec.code == code) {
                return ec;
            }
        }
        throw new IllegalArgumentException("未知的业务码: " + code);
    }
}
