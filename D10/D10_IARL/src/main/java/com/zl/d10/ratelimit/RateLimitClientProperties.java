package com.zl.d10.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 限流客户端 IP 解析配置。
 *
 * <p>「安全背景」{@code X-Forwarded-For} 是客户端可自行伪造的请求头。只有当请求的 TCP 对端
 * （{@code request.getRemoteAddr()}）确实位于可信反向代理链路上时，才能信任该头；
 * 服务若直接对公网暴露，默认应忽略它，一律使用无法在应用层伪造的 TCP 对端地址。</p>
 *
 * <ul>
 *     <li>{@code trustForwardHeaders}：总开关，默认 false（不信任，直接用 remoteAddr）；</li>
 *     <li>{@code trustedProxyIps}：可信代理 IP 白名单，仅对端命中才解析转发头；</li>
 *     <li>{@code trustedProxyCount}：可信代理跳数 N，真实客户端取 XFF 倒数第 N+1 个地址。</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitClientProperties {

    /** 是否信任转发头（X-Forwarded-For）。直接对公网开放时必须保持 false。 */
    private boolean trustForwardHeaders = false;

    /** 可信反向代理跳数；XFF 中最后 N 跳由可信代理写入。 */
    private int trustedProxyCount = 1;

    /** 可信代理 IP 白名单（TCP 对端命中才解析 XFF）。 */
    private List<String> trustedProxyIps =
            Arrays.asList("127.0.0.1", "0:0:0:0:0:0:0:1", "localhost");
}
