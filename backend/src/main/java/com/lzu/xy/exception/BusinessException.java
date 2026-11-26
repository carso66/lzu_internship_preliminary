package com.lzu.xy.exception;

import com.lzu.xy.enums.ResultCodeEnum;
import lombok.Getter;

/**
 * 业务异常：携带业务码和 HTTP 状态，由全局异常处理器统一转换为响应。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final int httpStatus;

    public BusinessException(ResultCodeEnum resultCode, String message) {
        super(message);
        this.code = Integer.parseInt(resultCode.getCode());
        this.httpStatus = resultCode.getHttpStatus();
    }

    public BusinessException(ResultCodeEnum resultCode) {
        this(resultCode, resultCode.getMessage());
    }
}
