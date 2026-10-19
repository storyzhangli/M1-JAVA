package com.zl.d06.demo.rdl;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Jedis 连接池工厂（单例）
 * 「原理说明」Jedis 实例非线程安全，多线程环境下必须通过 JedisPool 获取连接。
 */
public final class JedisPoolFactory {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 6379;
    private static final int TIMEOUT_MS = 3000;

    private static volatile JedisPool pool;

    private JedisPoolFactory() {
    }

    public static JedisPool getPool() {
        if (pool == null) {
            synchronized (JedisPoolFactory.class) {
                if (pool == null) {
                    JedisPoolConfig config = new JedisPoolConfig();
                    config.setMaxTotal(16);
                    config.setMaxIdle(8);
                    config.setMinIdle(2);
                    // 无密码本地环境
                    pool = new JedisPool(config, HOST, PORT, TIMEOUT_MS, null);
                }
            }
        }
        return pool;
    }
}
