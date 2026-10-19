package com.zl.d06.demo.rdl;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 手写 Redis 分布式锁（基于 Jedis + SET NX PX + Lua 脚本）
 *
 * 核心设计：
 * 1. 加锁：SET key requestId NX PX leaseTime，原子完成「不存在才设置 + 带过期时间」；
 * 2. 锁值：每个锁实例一个 UUID，标识「持有者」，防止误删别人的锁；
 * 3. 释放锁：必须用 Lua 脚本原子执行「GET 校验持有者 == 自己 才 DEL」，
 *    禁止先 GET 再 DEL 的两步操作（两步之间锁可能过期被他人抢走，导致误删）；
 * 4. 续期：Lua 脚本原子执行「GET 校验持有者 == 自己 才 PEXPIRE」；
 * 5. 看门狗：可选的自动续期定时任务，每 leaseTime/3 续期一次，直到主动 unlock。
 *
 * 「业务超租约风险」
 * - 固定租约模式下业务执行超过 leaseTime，锁会自动过期，其他线程可获锁，
 *   此时两个线程同时进入临界区，分布式锁互斥性失效；
 * - 即使本线程最后 unlock，Lua 校验 value 不匹配也不会误删别人的锁，
 *   但业务的并发执行已经发生（数据脏写、重复扣减等），无法挽回；
 * - 看门狗模式可规避「业务时间不确定」场景，但无法应对长时间 GC/STW、网络分区，
 *   且服务宕机时锁仍会在 leaseTime 后释放（这是避免死锁的必要设计）。
 */
public class RedisDistributedLock implements AutoCloseable {

    // ========================= Lua 脚本 =========================

    /**
     * 释放锁脚本：只有持有者才能删除。
     * KEYS[1] = 锁key；ARGV[1] = 本实例的 UUID
     * 返回 1 表示删除成功，0 表示锁已不属于自己（已过期或被别人持有）。
     */
    private static final String UNLOCK_LUA = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    /**
     * 续期脚本：只有持有者才能续租。
     * KEYS[1] = 锁key；ARGV[1] = UUID；ARGV[2] = 新的租约毫秒数
     * 返回 1 表示续期成功，0 表示锁已不属于自己（不能续期）。
     */
    private static final String RENEW_LUA = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('pexpire', KEYS[1], ARGV[2])
            else
                return 0
            end
            """;

    // ========================= 看门狗调度器 =========================
    // 守护线程池，JVM 退出时不会阻止关闭
    private static final ScheduledExecutorService WATCHDOG_SCHEDULER =
            Executors.newScheduledThreadPool(
                    Math.max(2, Runtime.getRuntime().availableProcessors()),
                    r -> {
                        Thread t = new Thread(r, "redis-lock-watchdog");
                        t.setDaemon(true);
                        return t;
                    });

    // ========================= 锁状态 =========================
    private final String lockKey;
    /** 本锁实例的唯一标识，作为 value，用于持有者校验 */
    private final String lockValue;
    private final long leaseTimeMillis;
    private final AtomicBoolean locked = new AtomicBoolean(false);

    /** 看门狗续期任务的句柄，用于 unlock 时取消 */
    private ScheduledFuture<?> watchdogFuture;

    public RedisDistributedLock(String lockKey, long leaseTimeMillis) {
        this.lockKey = lockKey;
        this.lockValue = UUID.randomUUID().toString();
        this.leaseTimeMillis = leaseTimeMillis;
    }

    // ========================= 加锁 =========================

    /**
     * 固定租约模式：自旋等待 waitTime 毫秒，加锁成功后租约固定，不自动续期。
     *
     * @param waitTimeMillis 最大等待时间
     * @return true 加锁成功；false 等待超时
     */
    public boolean tryLock(long waitTimeMillis, long leaseTimeMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + waitTimeMillis;
        SetParams params = SetParams.setParams().nx().px(leaseTimeMillis);
        try (Jedis jedis = JedisPoolFactory.getPool().getResource()) {
            for (;;) {
                // SET key value NX PX leaseTime：加锁与过期设置一步原子完成
                String result = jedis.set(lockKey, lockValue, params);
                if ("OK".equals(result)) {
                    locked.set(true);
                    return true;
                }
                if (System.currentTimeMillis() >= deadline) {
                    return false;
                }
                // 避免无脑自旋打满 Redis
                Thread.sleep(50);
            }
        }
    }

    /**
     * 看门狗模式：加锁成功后自动续期。
     * - 默认租约 30 秒，每 10 秒（租约 1/3）续期一次；
     * - 只要持有锁的线程存活且调度正常，锁不会过期；
     * - 主动 unlock 时取消续期任务；服务宕机时看门狗停止，30 秒后锁自动释放（防死锁）。
     *
     * @param waitTimeMillis 最大等待时间
     */
    public boolean tryLockWithWatchdog(long waitTimeMillis) throws InterruptedException {
        boolean acquired = tryLock(waitTimeMillis, leaseTimeMillis);
        if (!acquired) {
            return false;
        }
        startWatchdog();
        return true;
    }

    // ========================= 看门狗续期 =========================

    /**
     * 持有者如何续期：
     * 1. 锁持有者在业务执行期间，由看门狗定时任务调用本方法；
     * 2. 续期走 RENEW_LUA，先校验 value 仍是自己，再执行 PEXPIRE，保证「只续自己的锁」；
     * 3. 续期失败（返回 0）说明锁已过期或被他人持有，看门狗自动停止，后续 unlock 也会失败。
     */
    private void startWatchdog() {
        long renewInterval = Math.max(1, leaseTimeMillis / 3);
        watchdogFuture = WATCHDOG_SCHEDULER.scheduleAtFixedRate(() -> {
            boolean renewed = renew(leaseTimeMillis);
            if (renewed) {
                System.out.printf("[看门狗] 续期成功 key=%s 续为=%dms thread=%s%n",
                        lockKey, leaseTimeMillis, Thread.currentThread().getName());
            } else {
                System.out.printf("[看门狗] 续期失败，锁已不属于本实例 key=%s，停止看门狗%n", lockKey);
                stopWatchdog();
            }
        }, renewInterval, renewInterval, TimeUnit.MILLISECONDS);
    }

    private void stopWatchdog() {
        if (watchdogFuture != null) {
            watchdogFuture.cancel(false);
            watchdogFuture = null;
        }
    }

    /**
     * 手动续期（Lua 原子操作）。固定租约模式下也可由业务主动调用。
     */
    public boolean renew(long newLeaseTimeMillis) {
        try (Jedis jedis = JedisPoolFactory.getPool().getResource()) {
            Object result = jedis.eval(
                    RENEW_LUA,
                    Collections.singletonList(lockKey),
                    java.util.List.of(lockValue, String.valueOf(newLeaseTimeMillis))
            );
            return Long.valueOf(1L).equals(result);
        }
    }

    // ========================= 释放锁 =========================

    /**
     * 释放锁：Lua 原子化「校验持有者 + 删除」。
     * 绝对不能直接 jedis.del(lockKey)——那会把别人的锁删掉。
     */
    public boolean unlock() {
        if (!locked.compareAndSet(true, false)) {
            // 本实例从未加锁成功或已释放，直接返回
            return false;
        }
        stopWatchdog();
        try (Jedis jedis = JedisPoolFactory.getPool().getResource()) {
            Object result = jedis.eval(
                    UNLOCK_LUA,
                    Collections.singletonList(lockKey),
                    Collections.singletonList(lockValue)
            );
            boolean success = Long.valueOf(1L).equals(result);
            System.out.printf("[释放锁] key=%s result=%s%n", lockKey,
                    success ? "成功删除" : "锁已过期或不属于本实例，未删除");
            return success;
        }
    }

    public String getLockKey() {
        return lockKey;
    }

    public String getLockValue() {
        return lockValue;
    }

    /** 支持 try-with-resources，保证业务结束自动释放 */
    @Override
    public void close() {
        unlock();
    }
}
