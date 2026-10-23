package com.zl.d10.common;

import com.zl.d10.common.exception.BusinessException;
import com.zl.d10.ratelimit.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 捕获限流异常：返回 HTTP 429 Too Many Requests，并提示重试间隔。
     */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Result<Void>> handleRateLimit(RateLimitException e) {
        log.warn("限流：{}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf((long) Math.ceil(e.getRetryAfterSeconds())))
                .body(Result.fail(429, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e){
        log.error("全局异常：",e);
        return Result.fail(500, "服务器内部异常："+e.getMessage());
    }
}
