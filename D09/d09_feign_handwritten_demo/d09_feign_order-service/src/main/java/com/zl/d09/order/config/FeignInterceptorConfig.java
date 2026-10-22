package com.zl.d09.order.config;

import com.zl.d09.order.interceptor.TraceIdFeignRequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * InventoryClient专属配置
 * 注意：不加@Configuration注解，避免被全局扫描影响所有客户端
 */
public class FeignInterceptorConfig {

    @Bean
    public TraceIdFeignRequestInterceptor traceIdFeignRequestInterceptor() {
        return new TraceIdFeignRequestInterceptor();
    }
}