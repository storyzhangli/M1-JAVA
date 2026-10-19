package com.zl.d06.lock.impl;

import com.zl.d06.common.enums.ResultCodeEnum;
import com.zl.d06.common.exception.BusinessException;
import com.zl.d06.lock.DistributedLock;
import com.zl.d06.lock.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 基于 Redisson 的分布式锁门面实现
 *
 * 「原理说明」
 * 1. 加锁：RLock 底层基于 SET NX PX + Lua，锁值为随机 UUID（持有者身份），
 *    释放时 Lua 原子校验「持有者 == 自己」再删除，杜绝误删他人锁；
 * 2. 固定租约模式：tryLock(waitTime, leaseTime, unit) 显式指定租约后，
 *    Redisson Watchdog 自动续期关闭，锁到期强制释放；
 * 3. 看门狗模式：tryLock(waitTime, unit) 不传 leaseTime，Watchdog 启动，
 *    默认租约 30 秒、每 10 秒自动续期，直到主动 unlock；
 * 4. 释放锁：finally 中校验 isHeldByCurrentThread() 后 unlock，
 *    保证不会释放其他线程/其他实例持有的锁。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedissonDistributedLockServiceImpl implements DistributedLockService {

    private final RedissonClient redissonClient;

    @Override
    public DistributedLock getLock(String lockKey) {
        return new RedissonLockAdapter(redissonClient.getLock(lockKey));
    }

    @Override
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> action) {
        try (DistributedLock lock = getLock(lockKey)) {
            if (!lock.tryLock(waitTime, leaseTime, unit)) {
                log.warn("获取分布式锁超时，lockKey={}, waitTime={}{}", lockKey, waitTime, unit);
                throw new BusinessException(ResultCodeEnum.LOCK_ACQUIRE_FAILED);
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCodeEnum.LOCK_INTERRUPTED);
        }
    }

    @Override
    public <T> T executeWithLock(String lockKey, long waitTime, Supplier<T> action) {
        try (DistributedLock lock = getLock(lockKey)) {
            if (!lock.tryLockWithWatchdog(waitTime)) {
                log.warn("获取分布式锁超时（看门狗模式），lockKey={}, waitTime={}ms", lockKey, waitTime);
                throw new BusinessException(ResultCodeEnum.LOCK_ACQUIRE_FAILED);
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCodeEnum.LOCK_INTERRUPTED);
        }
    }

    @Override
    public void executeWithLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit, Runnable action) {
        executeWithLock(lockKey, waitTime, leaseTime, unit, () -> {
            action.run();
            return null;
        });
    }

    @Override
    public void executeWithLock(String lockKey, long waitTime, Runnable action) {
        executeWithLock(lockKey, waitTime, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Redisson RLock 适配器：将 Redisson 的 RLock 包装为统一的 DistributedLock。
     */
    @RequiredArgsConstructor
    private static class RedissonLockAdapter implements DistributedLock {

        private final RLock rLock;

        @Override
        public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
            return rLock.tryLock(waitTime, leaseTime, unit);
        }

        @Override
        public boolean tryLockWithWatchdog(long waitTime) throws InterruptedException {
            // 不传 leaseTime：Redisson 启动 Watchdog 自动续期（默认租约 30 秒）
            return rLock.tryLock(waitTime, TimeUnit.MILLISECONDS);
        }

        @Override
        public void unlock() {
            if (rLock.isHeldByCurrentThread()) {
                rLock.unlock();
            }
        }

        @Override
        public boolean isHeldByCurrentThread() {
            return rLock.isHeldByCurrentThread();
        }

        @Override
        public String getLockKey() {
            return rLock.getName();
        }

        @Override
        public void close() {
            unlock();
        }
    }
}
