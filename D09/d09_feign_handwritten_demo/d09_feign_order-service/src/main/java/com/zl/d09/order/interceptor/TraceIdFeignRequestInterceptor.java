package com.zl.d09.order.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;

/**
 * Feign请求拦截器：透传TraceID
 * 从MDC获取当前请求TraceID，添加到下游请求头
 * 保证整条调用链TraceID一致
 */
public class TraceIdFeignRequestInterceptor implements RequestInterceptor {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_KEY = "traceId";

    @Override
    public void apply(RequestTemplate template) {
        // 从MDC获取TraceID
        String traceId = MDC.get(MDC_TRACE_KEY);
        
        if (traceId != null && !traceId.isBlank()) {
            // 格式校验：简单长度校验
            if (traceId.length() <= 64) {
                template.header(TRACE_ID_HEADER, traceId);
            }
        }
        // 如果没有TraceID，不生成新的；入口层负责生成，这里只负责透传
        // 后台任务场景可根据业务需求补充生成逻辑
    }
}