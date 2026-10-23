package com.zl.d10.idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zl.d10.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 幂等切面：基于 Redis 原子占位实现「同一幂等键只处理一次」。
 *
 * <p>「核心原理」</p>
 * <ol>
 *     <li>用 {@code SET key value NX PX ttl} 原子抢占：仅当键不存在时写入 PROCESSING，
 *     保证「两个并发请求最多一个进入业务」；</li>
 *     <li>抢占成功：执行业务，完成后把结果以 COMPLETED 写回；失败则删除键允许重试；</li>
 *     <li>抢占失败（键已存在）：
 *       <ul>
 *         <li>参数指纹不同 → 拒绝「同键不同参数」的非法复用；</li>
 *         <li>COMPLETED → 返回原结果（重复请求不重复执行）；</li>
 *         <li>PROCESSING → 并发重复，提示正在处理。</li>
 *       </ul>
 *     </li>
 * </ol>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    /** Redis Key 前缀，后接幂等键。 */
    public static final String IDEMPOTENT_PREFIX = "idempotent:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            // 非 Web 上下文，直接放行
            return pjp.proceed();
        }

        String idemKey = attrs.getRequest().getHeader(idempotent.header());
        if (!StringUtils.hasText(idemKey)) {
            throw new BusinessException(1001, "缺少幂等键请求头 " + idempotent.header());
        }

        String redisKey = IDEMPOTENT_PREFIX + idemKey;
        String paramsHash = hashParams(pjp.getArgs());

        // 1. 原子抢占：SET NX PX
        IdempotentRecord processing = newRecord(IdempotentRecord.PROCESSING, paramsHash, null);
        Boolean claimed = stringRedisTemplate.opsForValue().setIfAbsent(
                redisKey,
                objectMapper.writeValueAsString(processing),
                Duration.ofSeconds(idempotent.expireSeconds()));

        // 2. 抢占成功：首个请求进入业务
        if (Boolean.TRUE.equals(claimed)) {
            try {
                Object result = pjp.proceed();
                IdempotentRecord completed = newRecord(IdempotentRecord.COMPLETED, paramsHash,
                        objectMapper.writeValueAsString(result));
                stringRedisTemplate.opsForValue().set(
                        redisKey,
                        objectMapper.writeValueAsString(completed),
                        Duration.ofSeconds(idempotent.expireSeconds()));
                return result;
            } catch (Throwable t) {
                // 业务失败：删除键，允许后续重试
                stringRedisTemplate.delete(redisKey);
                throw t;
            }
        }

        // 3. 抢占失败：键已存在
        String raw = stringRedisTemplate.opsForValue().get(redisKey);
        if (raw == null) {
            // 极端情况：键刚好过期，可直接重试
            throw new BusinessException(2001, "幂等状态已过期，请重试");
        }
        IdempotentRecord existing = objectMapper.readValue(raw, IdempotentRecord.class);

        // 3.1 同键不同参数 → 拒绝
        if (!paramsHash.equals(existing.getParamsHash())) {
            throw new BusinessException(1001,
                    "幂等键 " + idemKey + " 已被不同参数复用，拒绝处理");
        }

        // 3.2 已完成 → 返回原结果
        if (IdempotentRecord.COMPLETED.equals(existing.getStatus())) {
            log.info("重复请求命中已完成记录，返回原结果，幂等键={}", idemKey);
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            Class<?> returnType = signature.getMethod().getReturnType();
            return objectMapper.readValue(existing.getResult(), returnType);
        }

        // 3.3 处理中 → 并发重复
        throw new BusinessException(2001, "请求正在处理中，请勿重复提交（幂等键 " + idemKey + "）");
    }

    /**
     * 构造记录并填充时间戳。
     */
    private IdempotentRecord newRecord(String status, String paramsHash, String result) {
        long now = System.currentTimeMillis();
        IdempotentRecord record = new IdempotentRecord();
        record.setStatus(status);
        record.setParamsHash(paramsHash);
        record.setResult(result);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    /**
     * 对方法参数做 SHA-256 指纹：参数序列化为 JSON 后摘要。
     */
    private String hashParams(Object[] args) throws Exception {
        String argsJson = objectMapper.writeValueAsString(args);
        return DigestUtils.md5DigestAsHex(argsJson.getBytes(StandardCharsets.UTF_8));
    }
}
