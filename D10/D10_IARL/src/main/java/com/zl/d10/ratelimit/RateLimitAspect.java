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

import java.util.List;

/**
 * 限流切面：执行 Redis 令牌桶 Lua 脚本，按维度生成限流键，令牌不足抛出 {@link RateLimitException}。
 *
 * <p>「原子性」取令牌、补充令牌、写回均在一段 Lua 脚本中完成，Redis 单线程执行，
 * 高并发下不会出现超发。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private static final String KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate stringRedisTemplate;

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
     * 解析客户端 IP：优先 X-Forwarded-For（反向代理场景），其次 getRemoteAddr。
     */
    private String resolveClientIp(ServletRequestAttributes attrs) {
        if (attrs == null) {
            return "unknown";
        }
        String xff = attrs.getRequest().getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            // X-Forwarded-For 可能是逗号分隔的多级代理，取第一个（最初的客户端）
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return attrs.getRequest().getRemoteAddr();
    }
}
