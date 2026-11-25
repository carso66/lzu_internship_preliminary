package com.example.demo.common;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.DatabindException;


@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * 从 Jackson 3 的异常链中提取发生类型错误的字段名。
     *
     * 找不到字段路径时返回 null，由调用方决定使用通用错误提示。
     */
    private String findJsonFieldName(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof DatabindException mappingException) {
                var path = mappingException.getPath();

                if (path != null && !path.isEmpty()) {
                    // 通常最后一个路径引用就是发生错误的字段。
                    // 从后向前查找，也可以兼容数组索引等无字段名的路径节点。
                    for (int i = path.size() - 1; i >= 0; i--) {
                        var reference = path.get(i);

                        if (reference == null) {
                            continue;
                        }

                        String fieldName = reference.getPropertyName();

                        if (fieldName != null && !fieldName.isBlank()) {
                            return fieldName;
                        }
                    }
                }
            }

            current = current.getCause();
        }

        return null;
    }


    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 处理业务代码主动抛出的异常
    @ExceptionHandler(com.example.demo.common.BizException.class)
    public ResponseEntity<com.example.demo.common.ApiResponse<Void>> handleBiz(com.example.demo.common.BizException e) {
        ErrorCode errorCode = e.getErrorCode();

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(com.example.demo.common.ApiResponse.fail(
                        errorCode.getCode(),
                        e.getMessage()
                ));
    }

    // 处理 JSON、路径参数、查询参数和请求体校验错误
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentNotValidException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        String message;

        if (e instanceof HttpMessageNotReadableException ex) {
            String fieldName = findJsonFieldName(ex);

            message = fieldName == null
                    ? "请求体格式或字段类型错误"
                    : fieldName + " 字段类型错误";

        } else if (e instanceof MethodArgumentTypeMismatchException ex) {
            message = "参数类型错误：" + ex.getName();

        } else if (e instanceof MissingServletRequestParameterException ex) {
            message = "缺少参数：" + ex.getParameterName();

        } else if (e instanceof MethodArgumentNotValidException ex) {
            var fieldError = ex.getBindingResult().getFieldError();

            message = fieldError == null
                    ? "参数校验失败"
                    : fieldError.getField() + "：" + fieldError.getDefaultMessage();

        } else {
            message = "请求体格式或字段类型错误";
        }

        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(40001, message));
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<com.example.demo.common.ApiResponse<Void>> handleOther(Exception e) {

        // 保留框架已有的 404、405、415 等客户端错误状态
        if (e instanceof ErrorResponse error
                && error.getStatusCode().is4xxClientError()) {

            int status = error.getStatusCode().value();

            String message = switch (status) {
                case 400 -> "请求参数错误";
                case 404 -> "请求地址不存在";
                case 405 -> "请求方法不支持";
                case 415 -> "请求内容类型不支持";
                default -> "请求不符合接口要求";
            };

            return ResponseEntity
                    .status(error.getStatusCode())
                    .headers(error.getHeaders())
                    .body(com.example.demo.common.ApiResponse.fail(status * 100 + 1, message));
        }

        // 未预期的服务端错误：记录日志，响应中不返回异常详情
        log.error("未处理的服务端异常", e);

        return ResponseEntity.internalServerError()
                .body(com.example.demo.common.ApiResponse.fail(50000, "服务内部错误"));
    }
}