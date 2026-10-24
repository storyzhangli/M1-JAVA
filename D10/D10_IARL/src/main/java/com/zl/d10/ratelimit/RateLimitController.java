package com.zl.d10.ratelimit;

import com.zl.d10.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 限流演示接口。
 *
 * <p>「验证方式」</p>
 * <ul>
 *     <li>IP 维度：对 /ratelimit/ip 突发 10 次，约 2 个 200、其余 429。
 *     默认不信任 X-Forwarded-For，所有请求按 TCP 对端 remoteAddr 共用一桶，伪造该头无法获得独立桶；
 *     仅当 app.rate-limit.trust-forward-headers=true 且 TCP 对端命中可信代理白名单时，才按跳数解析转发头；</li>
 *     <li>用户维度：/ratelimit/user 用不同 X-User-Id，桶彼此隔离；</li>
 *     <li>业务维度：/ratelimit/business 所有调用方共享一个桶；</li>
 *     <li>多令牌：/ratelimit/heavy 每次消耗 2 个令牌。</li>
 * </ul>
 */
@Slf4j
@Tag(name = "D10-限流", description = "Redis 令牌桶限流")
@RestController
@RequestMapping("/ratelimit")
public class RateLimitController {

    @Operation(summary = "IP 维度限流：1 令牌/秒，桶容量 2")
    @GetMapping("/ip")
    @RateLimit(replenishRate = 1, burstCapacity = 2, requestedTokens = 1, type = RateLimitType.IP)
    public Result<Map<String, Object>> byIp() {
        return Result.success(payload("ip"));
    }

    @Operation(summary = "用户维度限流：1 令牌/秒，桶容量 2（请求头 X-User-Id 模拟用户）")
    @GetMapping("/user")
    @RateLimit(replenishRate = 1, burstCapacity = 2, requestedTokens = 1, type = RateLimitType.USER)
    public Result<Map<String, Object>> byUser() {
        return Result.success(payload("user"));
    }

    @Operation(summary = "业务维度限流：1 令牌/秒，桶容量 2，所有调用方共享")
    @GetMapping("/business")
    @RateLimit(replenishRate = 1, burstCapacity = 2, requestedTokens = 1,
            type = RateLimitType.BUSINESS, businessKey = "order-query")
    public Result<Map<String, Object>> byBusiness() {
        return Result.success(payload("business"));
    }

    @Operation(summary = "重接口：每次消耗 2 令牌，桶容量 4，1 令牌/秒")
    @GetMapping("/heavy")
    @RateLimit(replenishRate = 1, burstCapacity = 4, requestedTokens = 2, type = RateLimitType.IP)
    public Result<Map<String, Object>> heavy() {
        return Result.success(payload("heavy-cost-2"));
    }

    private Map<String, Object> payload(String dimension) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("dimension", dimension);
        map.put("timestamp", System.currentTimeMillis());
        map.put("thread", Thread.currentThread().getName());
        return map;
    }
}
