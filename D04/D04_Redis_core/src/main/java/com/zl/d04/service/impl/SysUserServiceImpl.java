package com.zl.d04.service.impl;

import com.zl.d04.common.constants.CacheConstants;
import com.zl.d04.common.exception.BusinessException;
import com.zl.d04.entity.SysUser;
import com.zl.d04.mapper.SysUserMapper;
import com.zl.d04.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现类
 * 「设计模式」Cache Aside旁路缓存：读命中直接返回，未命中查库后回写；写操作先更库再删缓存
 * 「防击穿方案」Redisson分布式锁 + 双重缓存检查，热点Key过期仅放行1个请求打库
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final SysUserMapper sysUserMapper;

    @Override
    public SysUser getUserById(Long userId) {
        String cacheKey = CacheConstants.USER_CACHE_PREFIX + userId;

        // 1. 优先查询缓存
        Object cacheValue = redisTemplate.opsForValue().get(cacheKey);
        if (cacheValue != null) {
            // 空值标记：直接返回null，避免缓存穿透
            if (cacheValue instanceof String && "NULL_MARK".equals(cacheValue)) {
                return null;
            }
            return (SysUser) cacheValue;
        }

        // 2. 缓存未命中，加分布式锁防击穿
        String lockKey = CacheConstants.USER_LOCK_PREFIX + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 尝试加锁：最大等待3秒，锁持有超时30秒（看门狗自动续期）
            boolean lockAcquired = lock.tryLock(3, 30, TimeUnit.SECONDS);
            if (!lockAcquired) {
                // 加锁失败：短暂休眠后重试，也可根据业务降级返回旧数据
                Thread.sleep(200);
                return getUserById(userId);
            }

            // 「原理说明」双重检查缓存：等待锁期间，可能已有其他线程完成缓存回写
            cacheValue = redisTemplate.opsForValue().get(cacheKey);
            if (cacheValue != null) {
                if (cacheValue instanceof String && "NULL_MARK".equals(cacheValue)) {
                    return null;
                }
                return (SysUser) cacheValue;
            }

            // 3. 执行数据库查询
            SysUser user = sysUserMapper.selectById(userId);

            // 4. 回写缓存，区分空值与正常值
            if (user == null) {
                // 空值短过期：防止恶意请求穿透数据库
                redisTemplate.opsForValue().set(cacheKey, "NULL_MARK", 
                        CacheConstants.NULL_VALUE_TTL, TimeUnit.SECONDS);
            } else {
                // 「坑点提示」基础TTL叠加随机偏移，避免大量Key同时过期引发缓存雪崩
                long ttl = CacheConstants.USER_CACHE_BASE_TTL 
                        + ThreadLocalRandom.current().nextLong(CacheConstants.TTL_RANDOM_OFFSET_MAX);
                redisTemplate.opsForValue().set(cacheKey, user, ttl, TimeUnit.SECONDS);
            }
            return user;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("用户查询缓存锁中断");
        } finally {
            // 仅持有锁的线程释放锁，避免误释放
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public boolean updateUser(SysUser user) {
        // Cache Aside标准流程：先更新数据库，再删除缓存
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
