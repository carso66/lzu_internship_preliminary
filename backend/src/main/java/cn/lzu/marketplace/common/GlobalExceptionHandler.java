package cn.lzu.marketplace.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 * 作用：任何 Controller 抛出的异常都在这里收敛成统一结构，
 * 客户端拿到的永远是 {code, message, data}，不会突然收到一坨 HTML 错误页。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：库存不足、商品不存在、已支付…… */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Result<Void>> onApiException(ApiException e) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(Result.fail(e.getCode(), e.getMessage()));
    }

    /** @Valid 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> onValidationFailed(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.BAD_REQUEST, msg));
    }

    /** 访问了不存在的路径 → 404，别让它掉进下面的兜底变成 500 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> onNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(404)
                .body(Result.fail(ErrorCode.NOT_FOUND, "接口不存在: /" + e.getResourcePath()));
    }

    /**
     * 兜底：没预料到的异常。
     * 注意两点：堆栈只进日志，不返回给客户端；对外只说"服务器内部错误"。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> onOtherError(Exception e) {
        log.error("未处理的异常", e);
        return ResponseEntity.status(500).body(Result.fail(ErrorCode.SERVER_ERROR, "服务器内部错误"));
    }
}
