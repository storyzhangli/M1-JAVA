package com.zl.d10.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redis 令牌桶限流注解：标注在 Controller 方法上，由 {@link RateLimitAspect} 执行。
 *
 * <p>「令牌桶（Token Bucket）」以固定速率往桶里放令牌，桶容量有上限；
 * 请求需取到令牌才能通过，否则被限流（HTTP 429）。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * replenishRate：令牌补充速率（每秒补充多少个令牌），即长期平均允许的 QPS。
     */
    double replenishRate() default 1;

    /**
     * burstCapacity：桶容量（令牌上限），决定可承受的突发请求量。
     */
    int burstCapacity() default 2;

    /**
     * requestedTokens：本次请求需要消耗的令牌数（可用于给重接口设更高成本）。
     */
    int requestedTokens() default 1;

    /**
     * 限流维度：IP / USER / BUSINESS。
     */
    RateLimitType type() default RateLimitType.IP;

    /**
     * 业务标识：type=BUSINESS 时作为桶的一部分；为空则使用方法名。
     */
    String businessKey() default "";
}
