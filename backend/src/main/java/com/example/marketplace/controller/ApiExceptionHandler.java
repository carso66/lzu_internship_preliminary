package com.example.marketplace.controller;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining("; "));
        return badRequest(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException exception) {
        String field = findJsonField(exception);
        String message = field == null
                ? "request body is invalid"
                : field + " has an invalid type or format";
        return badRequest(message);
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(40001, message, null));
    }

    private String findJsonField(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof JsonMappingException mappingException
                    && !mappingException.getPath().isEmpty()) {
                String fieldName = mappingException.getPath().get(0).getFieldName();
                if (fieldName != null) {
                    return fieldName;
                }
            }
            current = current.getCause();
        }
        return null;
    }
}