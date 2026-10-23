package com.zl.d10.idempotent.exception;

import com.zl.d10.idempotent.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理器：统一 HTTP 状态码与响应体业务码。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 幂等业务异常。 */
    @ExceptionHandler(IdempotencyException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdempotency(IdempotencyException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    /** 请求体参数校验失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.put(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.of(40002, "参数校验失败", errors));
    }

    /** 兜底异常。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(50000, "系统异常: " + ex.getMessage()));
    }
}
