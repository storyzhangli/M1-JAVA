package com.zl.d04.common.constants;

/**
 * 缓存全局常量
 * 「规范说明」所有缓存Key前缀、过期时间统一维护，禁止业务代码硬编码
 */
public interface CacheConstants {
    // 用户信息缓存
    String USER_CACHE_PREFIX = "sys:user:";
    String USER_LOCK_PREFIX = "lock:sys:user:";
    
    // 过期时间配置（秒）
    long USER_CACHE_BASE_TTL = 30 * 60;      // 基础过期30分钟
    long TTL_RANDOM_OFFSET_MAX = 5 * 60;     // 随机偏移上限5分钟，防雪崩
    long NULL_VALUE_TTL = 2 * 60;            // 空值过期2分钟，防穿透
}
