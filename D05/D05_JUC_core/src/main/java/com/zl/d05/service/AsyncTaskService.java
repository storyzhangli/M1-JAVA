package com.zl.d05.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步任务服务
 * 「原理说明」@Async 标注的方法由 Spring AOP 代理，调用方提交后立即返回，方法体在 bizTaskExecutor 线程池中执行。
 * 「坑点提示」@Async 方法必须是 public，且不能被同类内部方法直接调用（代理失效）。
 */
@Slf4j
@Service
public class AsyncTaskService {

    /** 全局任务计数，用于并发度测试 */
    private static final AtomicLong COUNTER = new AtomicLong(0);

    /**
     * 无返回值异步任务：模拟一次耗时业务（DB / Redis / HTTP 调用）
     */
    @Async("bizTaskExecutor")
    public void executeAsyncTask(String taskName) {
        long count = COUNTER.incrementAndGet();
        long start = System.currentTimeMillis();
        log.info("[异步开始] task={}, count={}, thread={}",
                taskName, count, Thread.currentThread().getName());
        try {
            // 模拟耗时 300ms 的业务操作
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[异步中断] task={}", taskName);
            return;
        }
        long cost = System.currentTimeMillis() - start;
        log.info("[异步结束] task={}, count={}, cost={}ms, thread={}",
                taskName, count, cost, Thread.currentThread().getName());
    }

    /**
     * 有返回值异步任务：返回 CompletableFuture，调用方可组合多个任务并行执行
     */
    @Async("bizTaskExecutor")
    public CompletableFuture<String> executeAsyncTaskWithResult(String query) {
        long start = System.currentTimeMillis();
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.completedFuture("interrupted");
        }
        String result = "result-" + query + "-" + UUID.randomUUID().toString().substring(0, 6);
        long cost = System.currentTimeMillis() - start;
        log.info("[异步返回] query={}, cost={}ms, thread={}",
                query, cost, Thread.currentThread().getName());
        return CompletableFuture.completedFuture(result);
    }

    /**
     * 获取当前累计任务数（用于观察并发度）
     */
    public long getTaskCount() {
        return COUNTER.get();
    }
}
