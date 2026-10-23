package com.zl.d10.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪过滤器：为每次请求生成（或从请求头接收）traceId，并写入 MDC。
 *
 * <p>「原理」MDC（Mapped Diagnostic Context，映射诊断上下文）是 SLF4J/Logback 提供的
 * 线程级日志上下文，底层基于 {@link ThreadLocal}。日志 pattern 中的 {@code %X{traceId}}
 * 会从 MDC 读取该值，从而把同一次请求的所有日志用同一个 traceId 串起来。</p>
 *
 * <p>「规则」</p>
 * <ul>
 *     <li>优先使用上游传入的请求头 {@code X-Trace-Id}（便于跨服务透传）；</li>
 *     <li>缺失时生成一个 32 位无横线 UUID；</li>
 *     <li>把 traceId 回写到响应头，方便调用方获取；</li>
 *     <li>请求结束（无论成功或异常）在 finally 中清理 MDC，避免线程复用串号。</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** MDC 中保存 traceId 的键名，需与 logback pattern 的 %X{traceId} 一致。 */
    public static final String TRACE_ID = "traceId";

    /** 链路追踪请求头名称。 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 1. 优先接收上游 traceId，否则生成
            String traceId = request.getHeader(TRACE_ID_HEADER);
            if (!StringUtils.hasText(traceId)) {
                traceId = generateTraceId();
            }
            // 2. 写入 MDC，供本次请求后续所有日志使用
            MDC.put(TRACE_ID, traceId);
            // 3. 回写响应头，调用方/网关可获取
            response.setHeader(TRACE_ID_HEADER, traceId);

            filterChain.doFilter(request, response);
        } finally {
            // 4. 清理：Tomcat 线程会被复用，不清理会导致下一个请求沿用上一个 traceId
            MDC.remove(TRACE_ID);
        }
    }

    /**
     * 生成 traceId：32 位无横线 UUID。
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
