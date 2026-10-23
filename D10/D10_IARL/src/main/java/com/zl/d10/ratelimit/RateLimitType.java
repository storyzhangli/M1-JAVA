package com.zl.d10.ratelimit;

/**
 * 限流维度：决定限流键的生成方式。
 */
public enum RateLimitType {

    /** 按客户端 IP 维度限流（同一 IP 共享令牌桶）。 */
    IP,

    /** 按登录用户维度限流（同一用户共享令牌桶）。 */
    USER,

    /** 按业务维度限流（整个业务接口共享一个桶）。 */
    BUSINESS
}
