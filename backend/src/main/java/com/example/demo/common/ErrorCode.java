package com.example.demo.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    PARAM_INVALID(40001, HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40101, HttpStatus.UNAUTHORIZED),
    NOT_FOUND(40401, HttpStatus.NOT_FOUND),
    STOCK_NOT_ENOUGH(40901, HttpStatus.CONFLICT),
    PRODUCT_OFF(40902, HttpStatus.CONFLICT),
    ALREADY_PAID(40903, HttpStatus.CONFLICT),
    INTERNAL_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final HttpStatus httpStatus;

    ErrorCode(int code, HttpStatus httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
