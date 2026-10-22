package com.zl.d09.consumer.feign.fallback;

import com.zl.d09.consumer.feign.ProviderFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Provider Feign 降级工厂：降级时可获取失败原因（超时/异常/熔断）。
 *
 * <p>相比直接用 {@code fallback}，{@code FallbackFactory} 能拿到触发降级的 Throwable，
 * 便于区分“读取超时”“连接拒绝”“熔断器打开”等不同情况。</p>
 */
@Slf4j
@Component
public class ProviderFeignFallbackFactory implements FallbackFactory<ProviderFeignClient> {

    @Override
    public ProviderFeignClient create(Throwable cause) {
        log.warn("Provider 调用触发降级，原因：{}", cause.getMessage());

        return new ProviderFeignClient() {
            @Override
            public Map<String, Object> echo(String name) {
                return buildFallback("echo", cause);
            }

            @Override
            public Map<String, Object> slow(long delay) {
                return buildFallback("slow", cause);
            }

            @Override
            public Map<String, Object> error() {
                return buildFallback("error", cause);
            }
        };
    }

    /**
     * 构造统一的降级返回体，标记 fallback=true 并给出原因。
     */
    private Map<String, Object> buildFallback(String method, Throwable cause) {
        Map<String, Object> result = new HashMap<>();
        result.put("fallback", true);
        result.put("method", method);
        result.put("service", "d09-consumer");
        result.put("reason", cause.getClass().getSimpleName());
        result.put("detail", cause.getMessage());
        return result;
    }
}
