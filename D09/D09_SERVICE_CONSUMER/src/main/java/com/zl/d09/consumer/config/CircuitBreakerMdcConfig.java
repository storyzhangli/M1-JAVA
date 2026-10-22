package com.zl.d09.consumer.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED;

/**
 * 熔断器配置：统一解决「MDC 跨线程传递」与「默认熔断参数」。
 *
 * <p>「为什么需要」开启 Feign 熔断后：</p>
 * <ul>
 *     <li>Feign 调用（含请求拦截器）被提交到熔断器的独立线程池执行，默认线程池不传递 MDC，
 *     导致拦截器读不到 traceId 而重新生成，Provider 与 Consumer 标识不一致；</li>
 *     <li>Feign 为每个方法生成独立熔断器（id 形如 {@code ProviderFeignClientslowlong}），
 *     若只配置名为 d09-provider 的实例不会生效，需用 configureDefault 统一设置默认参数。</li>
 * </ul>
 */
@Configuration
public class CircuitBreakerMdcConfig {

    /**
     * 自定义熔断器工厂：MDC 线程池 + 默认超时/熔断参数。
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> resilience4jCustomizer() {
        return factory -> {
            // 1) 配置 MDC 感知线程池，让 traceId 随任务传递到工作线程
            factory.configureExecutorService(new MdcAwareExecutorService(buildPool()));

            // 2) 为所有 Feign 方法配置默认的 TimeLimiter 与 CircuitBreaker
            factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                    .timeLimiterConfig(TimeLimiterConfig.custom()
                            // 时间限制器 3s，略大于 Feign readTimeout(2s)，让 Feign 先判超时
                            .timeoutDuration(Duration.ofSeconds(3))
                            .cancelRunningFuture(true)
                            .build())
                    .circuitBreakerConfig(CircuitBreakerConfig.custom()
                            // 基于计数的滑动窗口
                            .slidingWindowType(COUNT_BASED)
                            .slidingWindowSize(6)
                            // 至少 6 次调用才开始计算失败率
                            .minimumNumberOfCalls(6)
                            // 失败率阈值 50%
                            .failureRateThreshold(50)
                            // 打开状态持续 10s 后进入半开
                            .waitDurationInOpenState(Duration.ofSeconds(10))
                            // 半开状态允许 2 次试探
                            .permittedNumberOfCallsInHalfOpenState(2)
                            // 记录所有 Exception 作为失败
                            .recordExceptions(Exception.class)
                            .build())
                    .build());
        };
    }

    /**
     * 学习演示用的有界线程池；生产应根据 QPS 合理设置核心/最大线程与队列。
     */
    private ExecutorService buildPool() {
        return new ThreadPoolExecutor(
                4, 8,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                r -> {
                    Thread t = new Thread(r, "feign-cb-mdc-" + System.nanoTime());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
