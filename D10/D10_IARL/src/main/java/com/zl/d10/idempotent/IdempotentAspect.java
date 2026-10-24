package com.zl.d10.idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zl.d10.common.exception.BusinessException;
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
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * 幂等切面：基于 Redis 原子占位实现「同一作用域内同一幂等键只处理一次」。
 *
 * <p>「两项关键加固」</p>
 * <ol>
 *     <li><b>作用域隔离</b>：Redis 键结构为
 *     {@code idempotent:<租户>:<接口>:<幂等键>}，把租户（{@code X-Tenant-Id}，默认 default）
 *     与接口（类名#方法名）纳入键中，避免多租户、多接口之间的键冲突与结果错误复用；</li>
 *     <li><b>所有权校验</b>：每次抢占生成唯一 ownerToken，业务失败删除、成功更新都通过
 *     Lua 脚本比对 ownerToken 后原子执行。即使原记录在业务执行期间过期并被新请求重新占位，
 *     旧请求也无法删除或覆盖新记录。</li>
 * </ol>
 *
 * <p>「执行流程」</p>
 * <ol>
 *     <li>{@code SET key value NX PX ttl} 原子抢占，写入带 ownerToken 的 PROCESSING；</li>
 *     <li>抢占成功：执行业务，成功用 Lua 校验所有权后置 COMPLETED；失败用 Lua 校验所有权后删除；</li>
 *     <li>抢占失败：参数指纹不同则拒绝；COMPLETED 返回原结果；PROCESSING 提示处理中。</li>
 * </ol>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    /** Redis Key 前缀。 */
    public static final String IDEMPOTENT_PREFIX = "idempotent:";

    /** 租户请求头，缺省为 default。 */
    public static final String TENANT_HEADER = "X-Tenant-Id";
    public static final String DEFAULT_TENANT = "default";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private DefaultRedisScript<Long> releaseScript;
    private DefaultRedisScript<Long> completeScript;

    @PostConstruct
    public void init() {
        releaseScript = new DefaultRedisScript<>();
        releaseScript.setLocation(new ClassPathResource("lua/idempotent_release.lua"));
        releaseScript.setResultType(Long.class);

        completeScript = new DefaultRedisScript<>();
        completeScript.setLocation(new ClassPathResource("lua/idempotent_complete.lua"));
        completeScript.setResultType(Long.class);
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            // 非 Web 上下文，直接放行
            return pjp.proceed();
        }

        String idemKey = attrs.getRequest().getHeader(idempotent.header());
        if (!StringUtils.hasText(idemKey)) {
            throw new BusinessException(1001, "缺少幂等键请求头 " + idempotent.header());
        }

        // 作用域：租户 + 接口（类名#方法名）+ 幂等键
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String apiCode = signature.getDeclaringType().getSimpleName() + "#" + signature.getName();
        String tenant = resolveTenant(attrs);
        String redisKey = IDEMPOTENT_PREFIX + tenant + ":" + apiCode + ":" + idemKey;
        String paramsHash = hashParams(pjp.getArgs());

        // 本次请求的所有权标识
        String ownerToken = UUID.randomUUID().toString();
        long ttlMillis = Duration.ofSeconds(idempotent.expireSeconds()).toMillis();

        // 1. 原子抢占：SET NX PX，记录中携带 ownerToken
        IdempotencyRecord processing =
                newRecord(ownerToken, IdempotencyRecord.PROCESSING, paramsHash, null);
        Boolean claimed = stringRedisTemplate.opsForValue().setIfAbsent(
                redisKey,
                objectMapper.writeValueAsString(processing),
                Duration.ofSeconds(idempotent.expireSeconds()));

        // 2. 抢占成功：首个请求进入业务
        if (Boolean.TRUE.equals(claimed)) {
            try {
                Object result = pjp.proceed();
                IdempotencyRecord completed =
                        newRecord(ownerToken, IdempotencyRecord.COMPLETED, paramsHash,
                                objectMapper.writeValueAsString(result));
                // 仅当仍持有所有权时才允许覆盖，防止覆盖他人重新占位的新记录
                Long updated = stringRedisTemplate.execute(
                        completeScript,
                        java.util.List.of(redisKey),
                        ownerToken,
                        objectMapper.writeValueAsString(completed),
                        String.valueOf(ttlMillis));
                if (updated == null || updated == 0L) {
                    log.warn("记录已过期并被其他请求重新占位，放弃写入结果，幂等键={}, 接口={}",
                            idemKey, apiCode);
                }
                return result;
            } catch (Throwable t) {
                // 业务失败：仅当仍持有所有权时才删除，允许「本请求」后续重试
                Long deleted = stringRedisTemplate.execute(
                        releaseScript,
                        java.util.List.of(redisKey),
                        ownerToken);
                if (deleted == null || deleted == 0L) {
                    log.warn("记录所有权已转移，跳过删除，幂等键={}, 接口={}", idemKey, apiCode);
                }
                throw t;
            }
        }

        // 3. 抢占失败：键已存在
        String raw = stringRedisTemplate.opsForValue().get(redisKey);
        if (raw == null) {
            // 极端情况：键刚好过期，可直接重试
            throw new BusinessException(2001, "幂等状态已过期，请重试");
        }
        IdempotencyRecord existing =
                objectMapper.<IdempotencyRecord>readValue(raw, IdempotencyRecord.class);

        // 3.1 同键不同参数 → 拒绝
        if (!paramsHash.equals(existing.getParamsHash())) {
            throw new BusinessException(1001,
                    "幂等键 " + idemKey + " 已被不同参数复用，拒绝处理");
        }

        // 3.2 已完成 → 返回原结果
        if (IdempotencyRecord.COMPLETED.equals(existing.getStatus())) {
            log.info("重复请求命中已完成记录，返回原结果，作用域={}:{}, 幂等键={}",
                    tenant, apiCode, idemKey);
            Class<?> returnType = signature.getMethod().getReturnType();
            return objectMapper.readValue(existing.getResult(), returnType);
        }

        // 3.3 处理中 → 并发重复
        throw new BusinessException(2001, "请求正在处理中，请勿重复提交（幂等键 " + idemKey + "）");
    }

    /**
     * 解析租户标识：读取 X-Tenant-Id，缺省 default。
     */
    private String resolveTenant(ServletRequestAttributes attrs) {
        String tenant = attrs.getRequest().getHeader(TENANT_HEADER);
        return StringUtils.hasText(tenant) ? tenant.trim() : DEFAULT_TENANT;
    }

    /**
     * 构造记录并填充时间戳。
     */
    private IdempotencyRecord newRecord(String ownerToken, String status,
                                        String paramsHash, String result) {
        long now = System.currentTimeMillis();
        IdempotencyRecord record = new IdempotencyRecord();
        record.setOwnerToken(ownerToken);
        record.setStatus(status);
        record.setParamsHash(paramsHash);
        record.setResult(result);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    /**
     * 对方法参数做 MD5 指纹：参数序列化为 JSON 后摘要。
     */
    private String hashParams(Object[] args) throws Exception {
        String argsJson = objectMapper.writeValueAsString(args);
        return DigestUtils.md5DigestAsHex(argsJson.getBytes(StandardCharsets.UTF_8));
    }
}
