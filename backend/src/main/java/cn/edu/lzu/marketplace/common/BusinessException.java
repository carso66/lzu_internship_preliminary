package cn.edu.lzu.marketplace.common;

/**
 * 业务异常：Service 层对"可预期的业务失败"（库存不足、商品已下架等）抛出，
 * 由 {@link GlobalExceptionHandler} 统一转成 {code, message, data} 响应。
 *
 * <p>不承载业务码之外的技术细节；message 面向客户端展示。
 */
public class BusinessException extends RuntimeException {

    // 将业务错误码与可展示的提示一起交给全局异常处理器。
    private final ErrorCode errorCode;

    /** 使用错误码的默认文案 */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    /** 自定义面向客户端的提示，业务码与 HTTP 状态仍由 errorCode 决定 */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
