package com.zl.d09.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * 网关限流配置。
 *
 * <p>{@link KeyResolver} 决定“按什么维度限流”。这里按客户端 IP 限流：
 * 同一 IP 的请求共享一个令牌桶，不同 IP 互不影响。</p>
 */
@Configuration
public class RateLimiterConfig {

    /**
     * 按客户端 IP 解析限流键。
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = "unknown";
            if (exchange.getRequest().getRemoteAddress() != null
                    && exchange.getRequest().getRemoteAddress().getAddress() != null) {
                ip = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            }
            return Mono.just(ip);
        };
    }
}
