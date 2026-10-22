package com.zl.d09.consumer.feign;

import com.zl.d09.consumer.feign.fallback.ProviderFeignFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Provider 的 OpenFeign 客户端。
 *
 * <p>「按服务名调用」{@code name = "d09-provider"} 即 Nacos 中注册的服务名，
 * Feign 结合负载均衡器从 Nacos 实例列表选择节点，无需写死 IP:端口。</p>
 *
 * <p>「熔断降级」{@code fallbackFactory} 可在降级时拿到失败原因（异常），
 * 适用于超时、错误、服务不可用等场景。</p>
 */
@FeignClient(
        name = "d09-provider",
        contextId = "providerFeignClient",
        path = "/provider",
        fallbackFactory = ProviderFeignFallbackFactory.class
)
public interface ProviderFeignClient {

    /** 正常回显。 */
    @GetMapping("/echo")
    Map<String, Object> echo(@RequestParam("name") String name);

    /** 受控慢接口，用于触发读取超时。 */
    @GetMapping("/slow")
    Map<String, Object> slow(@RequestParam("delay") long delay);

    /** 受控错误接口，用于触发错误降级。 */
    @GetMapping("/error")
    Map<String, Object> error();
}
