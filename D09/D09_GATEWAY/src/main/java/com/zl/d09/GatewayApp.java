package com.zl.d09;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Cloud Gateway 网关启动类。
 *
 * <p>网关基于 WebFlux/Reactor 反应式模型，启动后：</p>
 * <ul>
 *     <li>向 Nacos 注册并发现 Provider/Consumer；</li>
 *     <li>按 {@code application.yml} 中的路由，将请求以 {@code lb://服务名} 转发；</li>
 *     <li>通过 RequestRateLimiter 基于 Redis 令牌桶限流。</li>
 * </ul>
 */
@SpringBootApplication
public class GatewayApp {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApp.class, args);
    }
}
