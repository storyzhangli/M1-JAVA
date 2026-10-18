package com.zl.d05.common.constants;

/**
 * 缓存全局常量
 * 「规范说明」所有缓存 Key 前缀、过期时间、分布式锁参数统一维护，禁止业务代码硬编码
 */
public interface CacheConstants {

    // ========== 用户信息缓存 ==========
    /** 用户信息缓存 Key 前缀 */
    String USER_CACHE_PREFIX = "sys:user:";
    /** 用户缓存重建锁 Key 前缀 */
    String USER_LOCK_PREFIX = "lock:sys:user:";

    // ========== 过期时间配置（秒） ==========
    /** 正常用户缓存基础过期时间：30 分钟 */
    long USER_CACHE_BASE_TTL = 30 * 60L;
    /** TTL 随机偏移上限：5 分钟，避免大量 Key 同时过期引发雪崩 */
    long TTL_RANDOM_OFFSET_MAX = 5 * 60L;
    /** 空值标记过期时间：2 分钟，防缓存穿透 */
    long NULL_VALUE_TTL = 2 * 60L;

    // ========== 分布式锁参数（秒/毫秒） ==========
    /** 加锁失败最大重试次数，超过后快速失败抛出 CACHE_BUSY */
    int LOCK_MAX_ATTEMPTS = 3;
    /** 每次 tryLock 最长等待时间（秒），复用 Redisson 内部自旋等待 */
    long LOCK_WAIT_SECONDS = 1L;
    /**
     * 固定锁租约（秒）。
     * 「坑点提示」显式指定正数 leaseTime 后，Redisson Watchdog 自动续期机制关闭，
     * 锁到期强制释放。业务执行时长必须严格小于该值，否则会出现并发安全问题。
     */
    long LOCK_LEASE_SECONDS = 30L;
    /** 重试退避基准时间（毫秒），第 n 次退避 = BASE * n 毫秒，线性递增 */
    long LOCK_RETRY_BACKOFF_BASE_MILLIS = 100L;
}
