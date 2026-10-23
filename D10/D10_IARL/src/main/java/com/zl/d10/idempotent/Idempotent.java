package com.zl.d10.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等注解：标注在 Controller 方法上，由 {@link IdempotentAspect} 切面拦截。
 *
 * <p>「幂等」即同一操作执行一次与执行多次，结果一致，不会重复产生业务效果。
 * 客户端通过请求头携带幂等键，服务端基于 Redis 保证「同一键只处理一次」。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** 幂等键所在的请求头名称。 */
    String header() default "Idempotency-Key";

    /** 幂等记录的过期时间（秒）；过期后同一键可作为新请求。 */
    long expireSeconds() default 300;

    /** 业务处理的最长等待（秒），仅用于记录 PROCESSING 状态的存活参考。 */
    long processingHintSeconds() default 30;
}
