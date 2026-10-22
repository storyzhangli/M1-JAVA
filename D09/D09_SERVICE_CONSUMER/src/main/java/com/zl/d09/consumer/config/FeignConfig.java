package com.zl.d09.consumer.config;

import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Feign 配置：通过请求拦截器向出站调用透传 traceId。
 *
 * <p>「原理」Feign 的 {@link RequestInterceptor} 在「发起请求的调用线程」中执行，
 * 此时 MDC 中已由 TraceIdFilter 写入本次请求的 traceId，因此可直接读取并加入
 * 请求头 X-Trace-Id，Provider 收到后即可在日志中看到相同标识。</p>
 */
@Configuration
public class FeignConfig {

    /**
     * 出站请求透传 traceId；若 MDC 无（如非 Web 场景）则生成一个。
     */
    @Bean
    public RequestInterceptor traceIdRequestInterceptor() {
        return template -> {
            String traceId = MDC.get("traceId");
            if (!StringUtils.hasText(traceId)) {
                traceId = UUID.randomUUID().toString().replace("-", "");
            }
            // header(...) 会覆盖同名头，避免重复
            template.header("X-Trace-Id", traceId);
        };
    }
}
