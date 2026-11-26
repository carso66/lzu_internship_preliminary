package com.example.demo.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 把所有异常收敛成统一响应体，保证客户端永远拿到 code / message / data。
 * 500 只返回固定文案，不把堆栈或异常细节透给客户端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** 404 + 40400：路径不存在（也覆盖少写 /api 前缀这类拼错） */
	private static final int CODE_NO_HANDLER = 40400;

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
		return ResponseEntity.status(ex.getStatus())
			.body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
	}

	/** 请求体不是合法 JSON，或字段类型与 DTO 声明不兼容（如把对象塞给 int） */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
		return ResponseEntity.badRequest()
			.body(ApiResponse.fail(ApiResponse.CODE_INVALID_JSON, "请求体必须是合法 JSON"));
	}

	/** 查询参数 / 路径变量类型不对 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return ResponseEntity.badRequest()
			.body(ApiResponse.fail(ApiResponse.CODE_BAD_REQUEST, "参数 " + ex.getName() + " 类型不正确"));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(ApiResponse.fail(CODE_NO_HANDLER, "接口不存在"));
	}

	@ExceptionHandler({ HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class })
	public ResponseEntity<ApiResponse<Void>> handleUnsupported(Exception ex) {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
			.body(ApiResponse.fail(ApiResponse.CODE_BAD_REQUEST, "请求方法或 Content-Type 不支持"));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ApiResponse.fail(ApiResponse.CODE_SERVER_ERROR, "服务出错"));
	}
}
