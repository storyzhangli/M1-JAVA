package com.zl.d09.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 反应式 traceId 全局过滤器：保证转发到下游的请求携带统一的 X-Trace-Id。
 *
 * <p>「原理」网关是反应式的，MDC（ThreadLocal）不适用，因此在请求头层面处理：
 * 上游若带 X-Trace-Id 则沿用，否则生成；写入转发请求头并回写响应头，
 * 使「客户端 → 网关 → Provider」链路可通过同一标识关联。</p>
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        // 用 set 覆盖（避免上游重复头导致出现多个值），写入转发请求头
        final String finalTraceId = traceId;
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> headers.set(TRACE_ID_HEADER, finalTraceId))
                .build();

        // 回写响应头，调用方可见
        exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, finalTraceId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        // 尽量先执行，保证后续路由/限流都能看到 traceId
        return Ordered.HIGHEST_PRECEDENCE;
    }
}