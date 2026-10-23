package com.zl.d10.controller;

import com.zl.d10.common.Result;
import com.zl.d10.service.AsyncTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * 并发测试接口
 * 「使用方式」
 * 1. 启动应用，调用 GET /concurrent/sync-slow 观察同步阻塞耗时；
 * 2. 调用 GET /concurrent/async-fire-and-forget?n=20 观察异步接口立即返回，日志中线程名前缀为 biz-async-；
 * 3. 调用 GET /concurrent/async-parallel?n=5 观察 CompletableFuture 并行聚合；
 * 4. 用 JMeter / wrk / ab 压测 /concurrent/async-fire-and-forget，观察线程池并发度。
 */
@Slf4j
@Tag(name = "D05-并发测试", description = "异步线程池与接口并发度验证")
@RestController
@RequestMapping("/concurrent")
@RequiredArgsConstructor
public class ConcurrentTestController {

    private final AsyncTaskService asyncTaskService;

    @Operation(summary = "同步慢接口（基线对比，耗时约300ms）")
    @GetMapping("/sync-slow")
    public Result<String> syncSlow() throws InterruptedException {
        long start = System.currentTimeMillis();
        Thread.sleep(300);
        long cost = System.currentTimeMillis() - start;
        return Result.success("同步完成，耗时=" + cost + "ms, thread=" + Thread.currentThread().getName());
    }

    @Operation(summary = "异步 fire-and-forget：立即返回，后台并行执行 n 个任务")
    @GetMapping("/async-fire-and-forget")
    public Result<String> asyncFireAndForget(@RequestParam(defaultValue = "10") int n) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            asyncTaskService.executeAsyncTask("task-" + i);
        }
        long cost = System.currentTimeMillis() - start;
        return Result.success("已提交 " + n + " 个异步任务，接口立即返回，耗时=" + cost + "ms");
    }

    @Operation(summary = "异步并行聚合：并行调用 n 个任务，等待全部完成后返回")
    @GetMapping("/async-parallel")
    public Result<List<String>> asyncParallel(@RequestParam(defaultValue = "5") int n)
            throws ExecutionException, InterruptedException {
        long start = System.currentTimeMillis();

        List<CompletableFuture<String>> futures = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            futures.add(asyncTaskService.executeAsyncTaskWithResult("q" + i));
        }

        // 等待全部完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<String> results = new ArrayList<>(n);
        for (CompletableFuture<String> f : futures) {
            results.add(f.get());
        }

        long cost = System.currentTimeMillis() - start;
        return Result.success(results);
    }

    @Operation(summary = "查看累计任务数（观察并发度）")
    @GetMapping("/task-count")
    public Result<Long> taskCount() {
        return Result.success(asyncTaskService.getTaskCount());
    }
}
