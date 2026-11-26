package com.example.demo;

import org.springframework.http.HttpStatus;

/**
 * 业务异常，同时携带业务码和 HTTP 状态码。
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 参数错误统一业务码。 */
    public static final int BAD_REQUEST_CODE = 40001;

    private final int code;
    private final HttpStatus httpStatus;

    public BizException(String message) {
        this(BAD_REQUEST_CODE, message, HttpStatus.BAD_REQUEST);
    }

    public BizException(int code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static BizException badRequest(String message) {
        return new BizException(BAD_REQUEST_CODE, message, HttpStatus.BAD_REQUEST);
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}