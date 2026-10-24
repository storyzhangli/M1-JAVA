package com.zl.d10.ratelimit;

import com.zl.d10.common.UserContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.List;

/**
 * 限流切面：执行 Redis 令牌桶 Lua 脚本，按维度生成限流键，令牌不足抛出 {@link RateLimitException}。
 *
 * <p>「原子性」取令牌、补充令牌、写回均在一段 Lua 脚本中完成，Redis 单线程执行，
 * 高并发下不会出现超发。</p>
 *
 * <p>「IP 可信性」客户端 IP 不再无条件信任 X-Forwarded-For，而是由
 * {@link RateLimitClientProperties} 控制：仅当 TCP 对端是可信代理且开关打开时才解析转发头，
 * 否则使用无法在应用层伪造的 remoteAddr，防止伪造头绕过 IP 限流。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private static final String KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate stringRedisTemplate;
    private final RateLimitClientProperties clientProperties;

    private DefaultRedisScript<List> script;

    @PostConstruct
    @SuppressWarnings("unchecked")
    public void init() {
        script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/rate_limiter.lua"));
        script.setResultType(List.class);
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String dimensionId = resolveDimensionId(pjp, rateLimit);
        String typeName = rateLimit.type().name().toLowerCase();
        String tokensKey = KEY_PREFIX + typeName + ":" + dimensionId + ":tokens";
        String timestampKey = KEY_PREFIX + typeName + ":" + dimensionId + ":timestamp";

        long now = System.currentTimeMillis();
        List<?> result = stringRedisTemplate.execute(
                script,
                List.of(tokensKey, timestampKey),
                String.valueOf(rateLimit.replenishRate()),
                String.valueOf(rateLimit.burstCapacity()),
                String.valueOf(now),
                String.valueOf(rateLimit.requestedTokens()));

        long allowed = result == null ? 0 : ((Number) result.get(0)).longValue();
        double remaining = result == null ? 0 : ((Number) result.get(1)).doubleValue();

        if (allowed == 1) {
            log.info("限流放行，维度={}, id={}, 剩余令牌={}", rateLimit.type(), dimensionId, remaining);
            return pjp.proceed();
        }

        // 估算需要等待多久才有足够令牌
        double waitSeconds = rateLimit.requestedTokens() / rateLimit.replenishRate();
        log.warn("限流拦截，维度={}, id={}, 剩余令牌={}, 建议等待={}s",
                rateLimit.type(), dimensionId, remaining, String.format("%.2f", waitSeconds));
        throw new RateLimitException(
                "请求过于频繁，已触发限流（维度=" + rateLimit.type() + ", id=" + dimensionId + "）",
                waitSeconds);
    }

    /**
     * 按维度解析限流键标识。
     */
    private String resolveDimensionId(ProceedingJoinPoint pjp, RateLimit rateLimit) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        switch (rateLimit.type()) {
            case USER:
                Long userId = UserContext.getUserId();
                if (userId != null) {
                    return "u" + userId;
                }
                // 演示环境未登录时，允许通过 X-User-Id 模拟用户
                if (attrs != null) {
                    String header = attrs.getRequest().getHeader("X-User-Id");
                    if (StringUtils.hasText(header)) {
                        return "u" + header;
                    }
                }
                return "anonymous";
            case BUSINESS:
                if (StringUtils.hasText(rateLimit.businessKey())) {
                    return rateLimit.businessKey();
                }
                // 未指定业务标识时，用类名#方法名
                MethodSignature signature = (MethodSignature) pjp.getSignature();
                return signature.getDeclaringType().getSimpleName() + "#" + signature.getName();
            case IP:
            default:
                return resolveClientIp(attrs);
        }
    }

    /**
     * 解析真实客户端 IP。
     *
     * <p>默认（{@code trustForwardHeaders=false}）忽略 X-Forwarded-For，直接返回 TCP 对端
     * remoteAddr；只有当开关打开且 TCP 对端命中可信代理白名单时，才按可信代理跳数从
     * X-Forwarded-For 中取「可信代理链之前的那一跳」作为真实客户端。</p>
     *
     * <p>XFF 形如 {@code client, proxy1, proxy2}，最右侧 N 个地址由 N 个可信代理写入，
     * 因此真实客户端下标 = 长度 - (N+1) = hops.length - trustedProxyCount - 1。</p>
     */
    private String resolveClientIp(ServletRequestAttributes attrs) {
        if (attrs == null) {
            return "unknown";
        }
        String remoteAddr = attrs.getRequest().getRemoteAddr();

        // 总开关关闭，或 TCP 对端本身不是可信代理：一律使用 remoteAddr，忽略可伪造的 XFF
        if (!clientProperties.isTrustForwardHeaders() || !isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String xff = attrs.getRequest().getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(xff)) {
            return remoteAddr;
        }

        String[] hops = Arrays.stream(xff.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        if (hops.length == 0) {
            return remoteAddr;
        }

        // 去掉最后 N 个可信代理地址，剩余的最右一个即首个可信代理观察到的真实客户端
        int index = hops.length - clientProperties.getTrustedProxyCount() - 1;
        if (index < 0) {
            // 代理跳数不足，无法判定，回退到 TCP 对端，避免被左侧伪造值欺骗
            return remoteAddr;
        }
        return hops[index];
    }

    /**
     * TCP 对端是否属于可信代理白名单。
     */
    private boolean isTrustedProxy(String remoteAddr) {
        return remoteAddr != null && clientProperties.getTrustedProxyIps().contains(remoteAddr);
    }
}
