package com.zl.d08.oom;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 场景四：无法创建本地线程（{@code java.lang.OutOfMemoryError: unable to create native thread}）。
 *
 * <p>原理：每个 Java 线程都要向操作系统申请一个本地线程（占用栈空间与内核资源）。
 * 当创建线程数量超过操作系统/进程限制（或内存不足）时抛出该错误。
 * 常见根因：手动 {@code new Thread} 且不回收、线程池无界扩张。</p>
 *
 * <p>运行：{@code java NativeThreadOomDemo bad} 触发；{@code java NativeThreadOomDemo good} 演示正确写法。</p>
 */
public class NativeThreadOomDemo {

    /**
     * 【错误写法】无限创建并启动线程，线程一直存活（阻塞在闭锁上），
     * 超过系统限制后抛出 unable to create native thread。
     */
    public void triggerOom() {
        // 闭锁不计数到 0，所有线程将长期存活
        CountDownLatch keepAlive = new CountDownLatch(1);
        int count = 0;
        try {
            while (true) {
                Thread t = new Thread(() -> {
                    try {
                        keepAlive.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, "oom-thread-" + (++count));
                t.start();
                if (count % 100 == 0) {
                    System.out.println("已创建线程：" + count);
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("触发 OOM，累计创建线程数：" + count);
            throw e;
        }
    }

    /**
     * 【正确写法】使用有界线程池复用线程，任务结束后关闭线程池。
     *
     * <p>要点：线程数受控、线程复用、finally 中 {@code shutdown}，避免线程无界增长。</p>
     */
    public void safePractice() {
        int poolSize = 4;
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        try {
            for (int i = 1; i <= 10; i++) {
                final int taskId = i;
                pool.submit(() -> System.out.println("任务 " + taskId
                        + " 由 " + Thread.currentThread().getName() + " 执行"));
            }
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("安全写法执行完毕，线程池大小固定为 " + poolSize + "。");
    }

    public static void main(String[] args) {
        NativeThreadOomDemo demo = new NativeThreadOomDemo();
        if (args.length > 0 && "good".equalsIgnoreCase(args[0])) {
            demo.safePractice();
        } else {
            demo.triggerOom();
        }
    }
}
