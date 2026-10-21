package com.zl.d08.lock;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁实例抽象接口
 *
 * 「设计定位」业务侧只面向该接口编程，不直接依赖 Redisson API，
 * 便于后续替换实现（如基于 Redis Lua 手写实现、ZooKeeper、Etcd 等）。
 * 「使用约定」锁实例通过 {@link DistributedLockService#getLock(String)} 获取；
 * 建议使用 try-with-resources（本接口继承 AutoCloseable）或 finally 中释放。
 */
public interface DistributedLock extends AutoCloseable {

    /**
     * 固定租约模式尝试加锁。
     *
     * @param waitTime  最多等待时间
     * @param leaseTime 锁租约时长（正数到期自动释放，Watchdog 不启动）
     * @param unit      时间单位
     * @return true 加锁成功；false 等待超时未获取
     * @throws InterruptedException 等待期间线程被中断
     */
    boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;

    /**
     * 看门狗模式尝试加锁（不指定 leaseTime，由 Watchdog 自动续期）。
     *
     * @param waitTime 最多等待时间
     * @return true 加锁成功；false 等待超时未获取
     * @throws InterruptedException 等待期间线程被中断
     */
    boolean tryLockWithWatchdog(long waitTime) throws InterruptedException;

    /**
     * 释放锁。仅锁持有者执行释放，避免误删其他线程持有的锁。
     */
    void unlock();

    /**
     * 当前线程是否仍持有该锁（用于 finally 释放前校验）。
     */
    boolean isHeldByCurrentThread();

    /**
     * 锁 Key，便于日志与监控。
     */
    String getLockKey();

    /**
     * 实现 AutoCloseable：try-with-resources 结束时自动释放锁。
     */
    @Override
    void close();
}
