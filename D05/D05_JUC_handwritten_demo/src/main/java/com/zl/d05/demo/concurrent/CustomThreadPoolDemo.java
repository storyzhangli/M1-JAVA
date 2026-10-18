package com.zl.d05.demo.concurrent;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自定义线程池Demo
 * 「原理说明」基于ThreadPoolExecutor原生实现，规避Executors工具类的OOM风险
 * 「面试考点」线程池七大核心参数、执行流程、拒绝策略、优雅关闭
 */
public class CustomThreadPoolDemo {

    // ========== 线程池核心参数配置 ==========
    /**
     * 核心线程数：CPU密集型设为CPU核心数，IO密集型可设为2倍
     */
    private static final int CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    /**
     * 最大线程数：核心线程 + 救急线程上限
     */
    private static final int MAX_POOL_SIZE = CORE_POOL_SIZE * 2;
    /**
     * 救急线程空闲存活时间
     */
    private static final long KEEP_ALIVE_SECONDS = 60L;
    /**
     * 工作队列容量：有界队列，避免无限堆积OOM
     */
    private static final int QUEUE_CAPACITY = 100;

    /**
     * 自定义线程工厂：给线程命名，方便问题排查
     */
    private static class CustomThreadFactory implements ThreadFactory {
        private final AtomicInteger threadCount = new AtomicInteger(1);
        private final String threadNamePrefix;

        public CustomThreadFactory(String prefix) {
            this.threadNamePrefix = prefix;
        }

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, threadNamePrefix + "-" + threadCount.getAndIncrement());
            // 设置为非守护线程，确保任务执行完成再退出
            thread.setDaemon(false);
            // 全局线程异常捕获，避免任务异常吞掉日志
            thread.setUncaughtExceptionHandler((t, e) ->
                    System.err.printf("线程[%s]发生未捕获异常：%s%n", t.getName(), e.getMessage())
            );
            return thread;
        }
    }

    // 创建线程池实例
    private static final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            new CustomThreadFactory("biz-pool"),
            // 拒绝策略：调用者线程执行，既不丢弃任务也不抛出异常，起到背压效果
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public static void main(String[] args) throws InterruptedException {
        System.out.printf("CPU核心数：%d，核心线程数：%d，最大线程数：%d%n",
                Runtime.getRuntime().availableProcessors(), CORE_POOL_SIZE, MAX_POOL_SIZE);

        // 提交20个测试任务
        for (int i = 1; i <= 20; i++) {
            int taskId = i;
            threadPool.execute(() -> {
                System.out.printf("任务%d正在执行，执行线程：%s%n",
                        taskId, Thread.currentThread().getName());
                try {
                    // 模拟业务耗时
                    TimeUnit.MILLISECONDS.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // 优雅关闭线程池：不再接收新任务，等待已提交任务执行完成
        threadPool.shutdown();
        // 等待最多10秒，确保任务执行完成
        boolean terminated = threadPool.awaitTermination(10, TimeUnit.SECONDS);
        if (terminated) {
            System.out.println("线程池已优雅关闭，所有任务执行完成");
        } else {
            System.err.println("线程池关闭超时，强制关闭剩余任务");
            threadPool.shutdownNow();
        }
    }
}
