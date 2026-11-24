package com.lzu.marketplace.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;

import java.util.List;

/**
 * 全局异常处理：保证失败响应也是统一结构，并且绝不把堆栈返回给客户端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：按异常里带的业务码和 HTTP 状态返回。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(ApiResponse.fail(e.getCode(), e.getMessage()));
    }

    /** 请求体 JSON 解析失败：字段类型不对、JSON 语法错误、请求体缺失。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        String field = fieldName(e.getCause());
        String message = (field == null)
                ? "请求体不是合法的 JSON"
                : "字段 " + field + " 类型不正确或格式不合法";
        log.warn("请求体解析失败: {} | {}", message, e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.fail(40001, message));
    }

    /** 路径 / 查询参数类型不对，例如 page=abc、pageSize=x。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "参数 " + e.getName() + " 类型不正确";
        log.warn("参数类型不匹配: {} | {}", message, e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.fail(40001, message));
    }

    /** 兜底：未知异常统一 500，堆栈只记日志。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
        log.error("服务内部错误", e);
        return ResponseEntity.status(500).body(ApiResponse.fail(50000, "服务出错"));
    }

    /** 从 Jackson 异常的路径里取出出错字段名（取最深一层有名字的节点）。 */
    private static String fieldName(Throwable cause) {
        if (!(cause instanceof JacksonException jacksonException)) {
            return null;
        }
        List<JacksonException.Reference> path = jacksonException.getPath();
        if (path == null || path.isEmpty()) {
            return null;
        }
        for (int i = path.size() - 1; i >= 0; i--) {
            String name = path.get(i).getPropertyName();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        return null;
    }
}
