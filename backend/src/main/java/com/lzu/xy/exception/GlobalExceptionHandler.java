package com.lzu.xy.exception;

import com.lzu.xy.enums.ResultCodeEnum;
import com.lzu.xy.response.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;

import java.util.Objects;

/**
 * 全局异常处理：所有异常都转换为统一的 {code, message, data} 结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    /** 请求体校验失败：指出具体哪个字段不合法 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null
                ? ResultCodeEnum.INVALID_PARAMETER.getMessage()
                : fieldError.getField() + " " + fieldError.getDefaultMessage();
        return badRequest(message);
    }

    /** 查询参数 / 路径参数校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraint(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .orElse(ResultCodeEnum.INVALID_PARAMETER.getMessage());
        return badRequest(message);
    }

    /** 路径 / 查询参数类型不匹配，例如 id 不是数字 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return badRequest(e.getName() + " 类型不正确");
    }

    /** 缺失必填查询参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return badRequest(e.getParameterName() + " 不能为空");
    }

    /** 请求体不是合法 JSON 或字段类型错误 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        String field = extractFieldName(e);
        return badRequest(field == null
                ? "请求体格式不正确"
                : field + " 类型不正确或格式非法");
    }

    /**
     * 从 Jackson 的解析异常中取出生错的字段名，例如 price 传了小数时返回 "price"。
     */
    private String extractFieldName(HttpMessageNotReadableException e) {
        Throwable cause = e.getMostSpecificCause();
        if (!(cause instanceof JacksonException jacksonException)) {
            return null;
        }
        return jacksonException.getPath().stream()
                .map(JacksonException.Reference::getPropertyName)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    /** 兜底：服务出错，不把堆栈返回给客户端 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception e) {
        log.error("未处理的服务异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(Integer.parseInt(ResultCodeEnum.INTERNAL_SERVER_ERROR.getCode()),
                        ResultCodeEnum.INTERNAL_SERVER_ERROR.getMessage()));
    }

    private ResponseEntity<Result<Void>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(Integer.parseInt(ResultCodeEnum.INVALID_PARAMETER.getCode()), message));
    }
}
