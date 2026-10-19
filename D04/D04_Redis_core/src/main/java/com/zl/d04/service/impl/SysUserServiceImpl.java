package com.zl.d04.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zl.d04.common.constants.CacheConstants;
import com.zl.d04.common.enums.ResultCodeEnum;
import com.zl.d04.common.exception.BusinessException;
import com.zl.d04.entity.SysUser;
import com.zl.d04.mapper.SysUserMapper;
import com.zl.d04.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 用户缓存服务：Cache-Aside + Redisson 分布式锁防击穿。
 *
 * 关键约定：
 * 1. 加锁失败时最多尝试 {@link CacheConstants#LOCK_MAX_ATTEMPTS} 次，用 for 循环而非递归，避免栈溢出；
 * 2. 每轮尝试前先读缓存；成功加锁后再次读缓存（DCL 双重检查），避免重复回源；
 * 3. 显式传入 leaseTime={@link CacheConstants#LOCK_LEASE_SECONDS} 秒，采用「固定租约」模式：
 *    - 正数 leaseTime 会关闭 Redisson Watchdog 自动续期，锁到期强制释放；
 *    - 业务（DB 查询 + 序列化 + 回写）必须在租约内完成，慢 SQL 需监控；
 * 4. 重试耗尽后快速失败抛出 {@link ResultCodeEnum#CACHE_BUSY}，不让请求无锁回源打垮 DB；
 * 5. 仅锁持有者执行 unlock，避免释放其他线程的锁。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    /** 空值缓存标记：用于防穿透，缓存不存在的用户 */
    private static final String NULL_MARK = "NULL_MARK";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final SysUserMapper sysUserMapper;

    @Override
    public SysUser getUserById(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR);
        }

        String cacheKey = CacheConstants.USER_CACHE_PREFIX + userId;
        String lockKey = CacheConstants.USER_LOCK_PREFIX + userId;
        RLock lock = redissonClient.getLock(lockKey);

        for (int attempt = 1; attempt <= CacheConstants.LOCK_MAX_ATTEMPTS; attempt++) {
            // 每轮尝试前先读缓存：等待锁期间，可能已有其他线程完成缓存回写。
            Object cacheValue = redisTemplate.opsForValue().get(cacheKey);
            if (cacheValue != null) {
                return decodeCacheValue(cacheValue);
            }

            boolean lockAcquired;
            try {
                /*
                 * 固定租约模式：
                 * - waitTime=1s：最多等待 1 秒获取锁，内部由 Redisson 自旋实现；
                 * - leaseTime=30s：锁 30 秒后自动释放，Watchdog 不启动、不续期；
                 * - 若业务执行超过 30 秒，锁会提前释放，存在并发风险，需通过慢查询监控兜底。
                 */
                lockAcquired = lock.tryLock(
                        CacheConstants.LOCK_WAIT_SECONDS,
                        CacheConstants.LOCK_LEASE_SECONDS,
                        TimeUnit.SECONDS
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ResultCodeEnum.LOCK_INTERRUPTED);
            }

            if (lockAcquired) {
                try {
                    // DCL 双重检查：本线程等待锁期间，前一个持锁线程可能已经回写缓存。
                    cacheValue = redisTemplate.opsForValue().get(cacheKey);
                    if (cacheValue != null) {
                        return decodeCacheValue(cacheValue);
                    }

                    // 缓存仍未命中，当前持锁线程负责回源数据库。
                    SysUser user = sysUserMapper.selectById(userId);
                    writeCache(cacheKey, user);
                    return user;
                } finally {
                    // 固定租约模式下，业务结束即主动释放锁；即使漏释放，30 秒后也会自动过期。
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }

            // 加锁失败：不递归，按线性退避后进入下一轮（下一轮先重读缓存）。
            if (attempt < CacheConstants.LOCK_MAX_ATTEMPTS) {
                sleepBeforeRetry(attempt);
            }
        }

        log.warn("获取用户缓存重建锁失败，达到最大尝试次数。userId={}, attempts={}",
                userId, CacheConstants.LOCK_MAX_ATTEMPTS);
        throw new BusinessException(ResultCodeEnum.CACHE_BUSY);
    }

    /**
     * 解码缓存值。调用前已确认 cacheValue != null，因此：
     * - 命中 NULL_MARK 表示「用户不存在」，返回 null；
     * - 其他情况按 SysUser 反序列化。
     */
    private SysUser decodeCacheValue(Object cacheValue) {
        if (cacheValue instanceof String && NULL_MARK.equals(cacheValue)) {
            return null;
        }
        return (SysUser) cacheValue;
    }

    /** 写入缓存：正常用户带随机 TTL，空值短 TTL 防穿透。 */
    private void writeCache(String cacheKey, SysUser user) {
        if (user == null) {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    NULL_MARK,
                    CacheConstants.NULL_VALUE_TTL,
                    TimeUnit.SECONDS
            );
            return;
        }

        // nextLong(bound) 返回 [0, bound)，叠加到基础 TTL 上，打散过期时间防雪崩。
        long ttl = CacheConstants.USER_CACHE_BASE_TTL
                + ThreadLocalRandom.current().nextLong(CacheConstants.TTL_RANDOM_OFFSET_MAX);
        redisTemplate.opsForValue().set(cacheKey, user, ttl, TimeUnit.SECONDS);
    }

    /** 线性退避：第 n 次等待 BASE * n 毫秒，保持可中断。 */
    private void sleepBeforeRetry(int attempt) {
        long backoffMillis = CacheConstants.LOCK_RETRY_BACKOFF_BASE_MILLIS * attempt;
        try {
            TimeUnit.MILLISECONDS.sleep(backoffMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCodeEnum.LOCK_INTERRUPTED);
        }
    }

    @Override
    public boolean updateUser(SysUser user) {
        // Cache-Aside：先更新数据库，再删除缓存。
        int rows = sysUserMapper.updateById(user);
        if (rows > 0) {
            redisTemplate.delete(CacheConstants.USER_CACHE_PREFIX + user.getId());
        }
        return rows > 0;
    }

    @Override
    public boolean deleteUser(Long userId) {
        int rows = sysUserMapper.deleteById(userId);
        if (rows > 0) {
            redisTemplate.delete(CacheConstants.USER_CACHE_PREFIX + userId);
        }
        return rows > 0;
    }
}
