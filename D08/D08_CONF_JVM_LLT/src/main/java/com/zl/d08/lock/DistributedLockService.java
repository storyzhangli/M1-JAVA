package com.zl.d08.lock;

import com.zl.d08.common.exception.BusinessException;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁门面服务
 *
 * 「设计定位」向业务层提供两套能力：
 * 1. 获取锁实例（{@link #getLock}），适合需要跨方法持锁的复杂场景；
 * 2. 模板方法（{@link #executeWithLock}），自动完成 加锁-执行业务-释放 闭环，
 *    推荐用于绝大多数「临界区」场景，杜绝忘记释放锁的问题。
 */
public interface DistributedLockService {

    /**
     * 获取指定 Key 的锁实例（不自动加锁，需业务自行 tryLock + unlock）。
     */
    DistributedLock getLock(String lockKey);

    /**
     * 固定租约模式：加锁成功后执行业务，finally 自动释放。
     *
     * @param lockKey   锁 Key
     * @param waitTime  最多等待加锁时间
     * @param leaseTime 锁租约时长
     * @param unit      时间单位
     * @param action    临界区业务
     * @return 业务返回值
     * @throws BusinessException 等待超时未获取锁时抛出
     */
    <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> action);

    /**
     * 看门狗模式：加锁成功后执行业务，Watchdog 自动续期，finally 自动释放。
     *
     * @param lockKey  锁 Key
     * @param waitTime 最多等待加锁时间
     * @param action   临界区业务（适合执行时长不确定的长任务）
     * @return 业务返回值
     */
    <T> T executeWithLock(String lockKey, long waitTime, Supplier<T> action);

    /**
     * 无返回值版本的固定租约模式模板方法。
     */
    void executeWithLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit, Runnable action);

    /**
     * 无返回值版本的看门狗模式模板方法。
     */
    void executeWithLock(String lockKey, long waitTime, Runnable action);
}
