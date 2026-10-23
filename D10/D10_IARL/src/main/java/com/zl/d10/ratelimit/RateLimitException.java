package com.zl.d10.ratelimit;

import lombok.Getter;

/**
 * 限流异常：令牌不足时抛出，由全局异常处理器映射为 HTTP 429。
 */
@Getter
public class RateLimitException extends RuntimeException {

    private final double retryAfterSeconds;

    public RateLimitException(String message, double retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
