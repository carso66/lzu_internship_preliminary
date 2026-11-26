package com.zyh.marketplace.exception;

import com.zyh.marketplace.common.ApiResponse;
import com.zyh.marketplace.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：把异常统一映射成「HTTP 状态 + 业务码」，响应体仍是 code/message/data，
 * 任何情况下都不把异常堆栈或 SQL 细节返回给客户端。
 * HTTP 状态一律从 ErrorCode 取，不依赖内容协商的默认状态，避免回退成 200。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Tomcat 在查询串按 UTF-8 解码失败时的异常消息前缀（稳定标识，不随语言环境变化） */
    private static final String CHARSET_DECODING_FAILED = "Character decoding failed";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        return badRequestWithCode(code, ex.getMessage());
    }

    /** JSON 体无法解析：语法错误、字段类型不对（如 price 传成字符串）、空请求体 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("请求体无法解析: {}", ex.getMessage());
        return badRequestWithCode(ErrorCode.BAD_REQUEST, "请求体不是合法的 JSON");
    }

    /**
     * 参数类型不匹配：查询参数 / 路径变量（如 /api/products/abc）。
     * 用父类 TypeMismatchException 捕获，覆盖底层实现可能抛出的不同子类。
     */
    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(TypeMismatchException ex) {
        String name = ex.getPropertyName() == null ? "参数" : ex.getPropertyName();
        return badRequestWithCode(ErrorCode.BAD_REQUEST, "字段 " + name + " 类型不合法，应为整数");
    }

    /**
     * 查询串编码非法：例如客户端把没做 URL 编码的 GBK 字节直接塞进 URL，
     * Tomcat 的 InvalidParameterException 会在取参数时抛出。
     * 该异常是 IllegalStateException → RuntimeException 的子类（不是 IllegalArgumentException），
     * 只能放在 RuntimeException 这一层拦截；这是客户端参数问题，按 40001 处理，而不是 500。
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException ex) {
        String message = String.valueOf(ex.getMessage());
        if (message.startsWith(CHARSET_DECODING_FAILED)) {
            log.debug("查询串无法按 UTF-8 解码: {}", message);
            return badRequestWithCode(ErrorCode.BAD_REQUEST, "请求参数无法解析，请对中文等字符做 UTF-8 URL 编码");
        }
        return internalError(ex);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        ErrorCode code = ErrorCode.METHOD_NOT_ALLOWED;
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.fail(code.getCode(), code.getDefaultMessage() + "：" + ex.getMethod()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return badRequestWithCode(ErrorCode.BAD_REQUEST, "请求头 Content-Type 需为 application/json");
    }

    /** 未匹配到任何接口（含静态资源），统一按 404 返回统一结构 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        ErrorCode code = ErrorCode.NOT_FOUND;
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.fail(code.getCode(), code.getDefaultMessage()));
    }

    /** 兜底：日志里保留堆栈供排查，响应体只给通用文案 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        return internalError(ex);
    }

    private ResponseEntity<ApiResponse<Void>> internalError(Throwable ex) {
        log.error("未预期的服务端异常", ex);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.fail(code.getCode(), code.getDefaultMessage()));
    }

    private ResponseEntity<ApiResponse<Void>> badRequestWithCode(ErrorCode code, String message) {
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.fail(code.getCode(), message));
    }
}
