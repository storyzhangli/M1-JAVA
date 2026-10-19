package com.zl.d06.demo.rdl;

import redis.clients.jedis.Jedis;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 手写 Redis 分布式锁演示
 *
 * 运行前请确保本地 Redis 已启动（127.0.0.1:6379，无密码）。
 *
 * 三个场景：
 * 1. 多线程互斥：5 个线程抢同一把锁，验证同一时刻只有一个线程进入临界区；
 * 2. 误删保护：固定租约 3 秒，业务睡 5 秒模拟「超租约」，锁过期后他人获锁，
 *    原持有者 unlock 时 Lua 校验 value 不匹配，不会删掉别人的锁；
 * 3. 看门狗续期：租约 9 秒，业务执行 20 秒，看门狗每 3 秒自动续期，锁不丢失。
 */
public class RedisLockDemo {

    private static final String LOCK_KEY = "lock:demo:order";

    public static void main(String[] args) throws InterruptedException {
        clearLock();

        System.out.println("========== 场景1：多线程互斥（固定租约模式） ==========");
        scenarioMutex();

        Thread.sleep(2000);
        clearLock();

        System.out.println("\n========== 场景2：超租约误删保护（Lua 原子释放） ==========");
        scenarioAvoidMistakenDelete();

        Thread.sleep(2000);
        clearLock();

        System.out.println("\n========== 场景3：看门狗自动续期 ==========");
        scenarioWatchdog();

        JedisPoolFactory.getPool().close();
        System.exit(0);
    }

    /** 场景1：5 个线程并发抢锁，临界区串行执行 */
    private static void scenarioMutex() throws InterruptedException {
        int threadCount = 5;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    // 每个线程创建独立的锁实例（各自 UUID）
                    try (RedisDistributedLock lock = new RedisDistributedLock(LOCK_KEY, 10_000)) {
                        boolean acquired = lock.tryLock(30_000, 10_000);
                        if (!acquired) {
                            System.out.printf("线程%d 等待超时，未获取锁%n", idx);
                            return;
                        }
                        System.out.printf("线程%d 获取锁成功，进入临界区%n", idx);
                        // 模拟临界区业务
                        Thread.sleep(1000);
                        System.out.printf("线程%d 临界区执行完毕%n", idx);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "biz-" + idx);
            t.start();
        }

        start.countDown();
        done.await();
    }

    /** 场景2：业务超租约，锁过期被他人持有，原持有者 unlock 不会误删 */
    private static void scenarioAvoidMistakenDelete() throws InterruptedException {
        // 持有者 A：租约 3 秒，业务故意执行 5 秒（超过租约）
        RedisDistributedLock lockA = new RedisDistributedLock(LOCK_KEY, 3_000);
        boolean acquired = lockA.tryLock(2_000, 3_000);
        System.out.println("A 获取锁成功，value=" + lockA.getLockValue());

        Thread.sleep(3_500); // 等待锁自动过期

        // 3 秒后锁已过期，B 成功获取锁
        RedisDistributedLock lockB = new RedisDistributedLock(LOCK_KEY, 3_000);
        boolean bAcquired = lockB.tryLock(2_000, 3_000);
        System.out.println("B 在 A 锁过期后获取锁成功=" + bAcquired + "，value=" + lockB.getLockValue());

        // A 业务执行完毕，释放锁：Lua 校验 value 不匹配，不会删除 B 的锁
        boolean aUnlock = lockA.unlock();
        System.out.println("A 执行 unlock（预期失败，不应删掉 B 的锁）=" + aUnlock);

        // 验证 B 的锁仍然存在
        try (Jedis jedis = JedisPoolFactory.getPool().getResource()) {
            System.out.println("此时 Redis 中锁的 value 仍是 B 的：" + jedis.get(LOCK_KEY)
                    + "，equals B value = " + lockB.getLockValue().equals(jedis.get(LOCK_KEY)));
        }
        lockB.unlock();
    }

    /** 场景3：看门狗模式，业务 20 秒超过初始租约 9 秒，靠自动续期保持锁 */
    private static void scenarioWatchdog() throws InterruptedException {
        try (RedisDistributedLock lock = new RedisDistributedLock(LOCK_KEY, 9_000)) {
            boolean acquired = lock.tryLockWithWatchdog(2_000);
            System.out.println("看门狗模式加锁成功=" + acquired + "，初始租约 9 秒，每 3 秒续期一次");

            // 后台打印 TTL，观察锁被持续续回
            Thread ttlWatcher = new Thread(() -> {
                try (Jedis jedis = JedisPoolFactory.getPool().getResource()) {
                    for (int i = 0; i < 6; i++) {
                        TimeUnit.SECONDS.sleep(3);
                        Long ttl = jedis.pttl(LOCK_KEY);
                        System.out.printf("[TTL 观察] 第%d 次，锁剩余 TTL = %dms%n", i + 1, ttl);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            ttlWatcher.setDaemon(true);
            ttlWatcher.start();

            // 模拟 20 秒长业务
            System.out.println("开始执行业务（20 秒）...");
            Thread.sleep(20_000);
            System.out.println("业务执行完毕，准备释放锁");
        }
    }

    private static void clearLock() {
        try (Jedis jedis = JedisPoolFactory.getPool().getResource()) {
            jedis.del(LOCK_KEY);
        }
    }
}
