package com.zl.d08.config;

import com.zl.d08.trace.MdcTaskDecorator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置
 * 「原理说明」@EnableAsync 开启 Spring 方法级异步调用；默认 Spring 会用 SimpleAsyncTaskExecutor（每次新建线程，不复用），
 * 生产环境必须替换为自定义 ThreadPoolTaskExecutor，避免线程爆炸。
 * 「面试考点」七大参数：corePoolSize / maxPoolSize / queueCapacity / keepAliveSeconds /
 *          threadNamePrefix / rejectedExecutionHandler / waitForTasksToCompleteOnShutdown
 */
@Slf4j
@EnableAsync
@Configuration
public class AsyncThreadPoolConfig {

    /**
     * 业务通用异步线程池
     * - CPU 密集型：核心数 = CPU 核数
     * - IO 密集型：核心数 = 2 × CPU 核数（DB/Redis/HTTP 调用都算 IO）
     * 这里按混合负载取 CPU 核数 × 2，兼顾 IO 与 CPU。
     */
    @Bean("bizTaskExecutor")
    public Executor bizTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cpuCores = Runtime.getRuntime().availableProcessors();

        // 核心线程数：默认预热，提交第一个任务即创建
        executor.setCorePoolSize(cpuCores * 2);
        // 最大线程数：队列满后才会扩容到该值
        executor.setMaxPoolSize(cpuCores * 4);
        // 有界队列：避免无界队列堆积任务导致 OOM
        executor.setQueueCapacity(200);
        // 非核心线程空闲回收时间
        executor.setKeepAliveSeconds(60);
        // 线程名前缀：jstack / 日志中可直接定位业务
        executor.setThreadNamePrefix("biz-async-");
        // MDC 上下文传递：把请求线程的 traceId 带入异步任务，任务结束后清理，防止线程池复用串号
        executor.setTaskDecorator(new MdcTaskDecorator());
        // 拒绝策略：由调用线程执行，起到背压效果，不丢任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅关闭：应用停止时等待已提交任务执行完
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        log.info("bizTaskExecutor 初始化完成: core={}, max={}, queue={}",
                cpuCores * 2, cpuCores * 4, 200);
        return executor;
    }
}
