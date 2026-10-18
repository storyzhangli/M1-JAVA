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
 * 用户缓存服务：Cache-Aside + Redisson 分布式锁。
 *
 * 关键约定：
 * 1. 加锁失败时最多尝试 MAX_LOCK_ATTEMPTS 次，不递归调用自身；
 * 2. 每次加锁前检查缓存，成功加锁后再次检查缓存；
 * 3. tryLock(waitTime, unit) 未指定固定 leaseTime，由 Redisson Watchdog 续期；
 * 4. 锁竞争达到上限后快速失败，不让全部请求无锁回源；
 * 5. 仅锁持有者执行 unlock，避免释放其他线程的锁。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper,SysUser> implements SysUserService {

    private static final String NULL_MARK = "NULL_MARK";

    /** 最大尝试次数，防止请求无限重试。 */
    private static final int MAX_LOCK_ATTEMPTS = 3;

    /** 每次尝试获取锁最多等待 1 秒。 */
    private static final long LOCK_WAIT_SECONDS = 1;

    /** 固定租约 30 秒。 */
    private static final long LOCK_LEASE_SECONDS = 30;

    /** 递增退避基准时间；第 n 次退避为 BASE * n 毫秒。 */
    private static final long RETRY_BACKOFF_BASE_MILLIS = 100;

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

        for (int attempt = 1; attempt <= MAX_LOCK_ATTEMPTS; attempt++) {
            // 每轮尝试前再次读缓存；等待期间缓存可能已被其他请求填充。
            Object cacheValue = redisTemplate.opsForValue().get(cacheKey);
            if (cacheValue != null) {
                return decodeCacheValue(cacheValue);
            }

            boolean lockAcquired;
            try {
                /*
                 * 最多等待 1 秒获取锁；成功获取后使用固定 30 秒租约。
                 * 正数 leaseTime 到期后锁会自动释放，不能依赖 watchdog 自动续期。
                 * 必须确保数据库查询、缓存序列化与回写在租约内完成，并监控慢查询。
                 */
                lockAcquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS,TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ResultCodeEnum.LOCK_INTERRUPTED);
            }

            if (lockAcquired) {
                try {
                    // 双重检查：避免前一个线程在本线程等待锁时已经完成缓存回写。
                    cacheValue = redisTemplate.opsForValue().get(cacheKey);
                    if (cacheValue != null) {
                        return decodeCacheValue(cacheValue);
                    }

                    // 缓存仍未命中，当前持锁线程负责查询数据库。
                    SysUser user = sysUserMapper.selectById(userId);
                    writeCache(cacheKey, user);
                    return user;
                } finally {
                    // 看门狗方案下通常会持续续期，仍需在业务结束时主动释放锁。
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }

            // 获取锁失败：不递归。下轮会先检查缓存，再决定是否继续争抢锁。
            if (attempt < MAX_LOCK_ATTEMPTS) {
                sleepBeforeRetry(attempt);
            }
        }

        log.warn("获取用户缓存重建锁失败，达到最大尝试次数。userId={}, attempts={}",
                userId, MAX_LOCK_ATTEMPTS);
        throw new BusinessException(ResultCodeEnum.CACHE_BUSY);
    }

    /**
     * 空值标记返回 null，但调用前已经通过 cacheValue != null 确认这是缓存命中；
     * 因而“缓存中记录用户不存在”与“缓存未命中”不会混淆。
     */
    private SysUser decodeCacheValue(Object cacheValue) {
        if (cacheValue instanceof String && NULL_MARK.equals(cacheValue)) {
            return null;
        }
        return (SysUser) cacheValue;
    }

    /** 写入正常用户或短 TTL 空值标记，延续现有缓存策略。 */
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

        // nextLong(bound) 不包含 bound，本实现将 TTL 增加 0 到 299 秒。
        long ttl = CacheConstants.USER_CACHE_BASE_TTL
                + ThreadLocalRandom.current().nextLong(
                CacheConstants.TTL_RANDOM_OFFSET_MAX
        );

        redisTemplate.opsForValue().set(cacheKey, user, ttl, TimeUnit.SECONDS);
    }

    /**
     * 短暂退避，且保持可中断性。
     * Thread.sleep 不是锁机制，只用于缓解失败请求的立即重试竞争。
     */
    private void sleepBeforeRetry(int attempt) {
        long backoffMillis = RETRY_BACKOFF_BASE_MILLIS * attempt;
        try {
            TimeUnit.MILLISECONDS.sleep(backoffMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCodeEnum.LOCK_INTERRUPTED);
        }
    }

    @Override
    public boolean updateUser(SysUser user) {
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