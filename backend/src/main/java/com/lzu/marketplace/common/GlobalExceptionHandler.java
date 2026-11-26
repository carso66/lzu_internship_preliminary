package com.lzu.marketplace.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * 全局异常处理器：把各类异常统一收敛成 {@code {code, message, data}} 结构。
 *
 * <p>两类严格区分：
 * <ul>
 *   <li><b>可预期</b>（参数错、业务冲突、找不到）—— 映射到对应业务码，message 面向用户，</li>
 *   <li><b>不可预期</b>（NPE、SQL 异常等）—— 一律 50000 / HTTP 500，日志记录完整堆栈，
 *       但响应体<b>只给固定文案</b>，绝不回传异常消息、SQL、类名或堆栈。</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 500 的固定对外文案（题目 §3：不把堆栈返回给客户端）。 */
    private static final String GENERIC_ERROR_MESSAGE = "服务出错，请稍后重试";

    // ------------------------------------------------------------------
    // 业务异常
    // ------------------------------------------------------------------

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex, HttpServletRequest req) {
        ErrorCode ec = ex.errorCode();
        log.debug("业务异常 {} {} -> {} {}", req.getMethod(), req.getRequestURI(), ec.code(), ex.getMessage());
        return build(ec, ex.getMessage());
    }

    // ------------------------------------------------------------------
    // 请求体
    // ------------------------------------------------------------------

    /**
     * 请求体无法解析：非法 JSON、类型不匹配（如 "2" 给 int）、数值溢出、缺少请求体。
     *
     * <p>注意：Jackson 可能把内部异常信息放进 message，这里<b>不透传</b>原文，
     * 只给出「哪个位置/字段类型不对」这类粗粒度信息，避免泄漏实现细节。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex,
                                                              HttpServletRequest req) {
        Throwable root = rootCause(ex);
        // JSON 语法错误（MalformedJsonException 等）与类型错误（MismatchedInputException）分开描述
        boolean syntaxError = root instanceof com.fasterxml.jackson.core.JsonParseException
                || root instanceof com.fasterxml.jackson.core.io.JsonEOFException;

        String message = syntaxError
                ? ErrorCode.MALFORMED_JSON.defaultMessage()
                : "请求体字段类型不正确或超出允许范围";
        ErrorCode ec = syntaxError ? ErrorCode.MALFORMED_JSON : ErrorCode.BAD_REQUEST;

        log.debug("请求体解析失败 {} {}: {}", req.getMethod(), req.getRequestURI(), root.toString());
        return build(ec, message);
    }

    // ------------------------------------------------------------------
    // 参数校验（@Valid）：缺字段、@Min 越界、@NotBlank 等
    // ------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest req) {
        return build(ErrorCode.BAD_REQUEST, describeFieldErrors(ex.getBindingResult().getFieldErrors()));
    }

    /** 表单 / 查询对象绑定失败（非 @RequestBody 的 @Valid）。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException ex, HttpServletRequest req) {
        return build(ErrorCode.BAD_REQUEST, describeFieldErrors(ex.getBindingResult().getFieldErrors()));
    }

    // ------------------------------------------------------------------
    // 查询参数
    // ------------------------------------------------------------------

    /** ?page=abc 或 ?page=2.5 —— 类型转换失败。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                HttpServletRequest req) {
        String name = ex.getName();
        String message = "参数 " + name + " 类型不正确";
        return build(ErrorCode.BAD_REQUEST, message);
    }

    /** 缺少必填查询参数。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex,
                                                                HttpServletRequest req) {
        return build(ErrorCode.BAD_REQUEST, "缺少必填参数 " + ex.getParameterName());
    }

    // ------------------------------------------------------------------
    // 路由 / 方法
    // ------------------------------------------------------------------

    /** Spring Boot 3.2+ 静态资源未命中（关闭静态资源映射后也会走这里）。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex,
                                                              HttpServletRequest req) {
        return build(ErrorCode.API_NOT_FOUND, ErrorCode.API_NOT_FOUND.defaultMessage());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(NoHandlerFoundException ex,
                                                             HttpServletRequest req) {
        return build(ErrorCode.API_NOT_FOUND, ErrorCode.API_NOT_FOUND.defaultMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                    HttpServletRequest req) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.defaultMessage());
    }

    /**
     * Content-Type 不受支持（如向 JSON 接口发 text/plain）。
     *
     * <p>没有这个处理器时会落到 {@code Exception} 兜底，变成 50000 / HTTP 500，
     * 把客户端错误误报成服务端故障。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                                         HttpServletRequest req) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                ErrorCode.UNSUPPORTED_MEDIA_TYPE.defaultMessage());
    }

    /**
     * 无法产生客户端可接受的响应（如 {@code Accept: application/xml}）。
     *
     * <p>注意：这个异常在响应已经部分提交或内容协商阶段抛出时，
     * Spring 仍会尝试用本处理器序列化响应。<b>但它可能仍返回空体</b>——
     * 已通过 {@code WebConfig.configureContentNegotiation} 固定默认内容类型来降低触发概率。
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                                                          HttpServletRequest req) {
        return build(ErrorCode.NOT_ACCEPTABLE, ErrorCode.NOT_ACCEPTABLE.defaultMessage());
    }

    // ------------------------------------------------------------------
    // 兜底：任何未预料异常 → 50000，绝不泄漏内部信息
    // ------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest req) {
        // 完整堆栈只进服务端日志
        log.error("未处理异常 {} {}", req.getMethod(), req.getRequestURI(), ex);
        // 对外只给固定文案，不含 ex.getMessage()
        return build(ErrorCode.SERVER_ERROR, GENERIC_ERROR_MESSAGE);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode ec, String message) {
        return ResponseEntity.status(ec.httpStatus())
                .body(ApiResponse.fail(ec.code(), message));
    }

    /**
     * 把字段校验错误拼成一句话：优先单字段直接给出，多字段时用「；」连接。
     * 只暴露字段名与校验规则，不暴露内部对象路径以外的信息。
     */
    private String describeFieldErrors(List<FieldError> errors) {
        if (errors.isEmpty()) {
            return ErrorCode.BAD_REQUEST.defaultMessage();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < errors.size(); i++) {
            FieldError fe = errors.get(i);
            if (i > 0) {
                sb.append("；");
            }
            sb.append("参数 ").append(fe.getField()).append(" ");
            String msg = fe.getDefaultMessage();
            sb.append(msg == null ? "不合法" : msg);
        }
        return sb.toString();
    }

    private Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }
}
