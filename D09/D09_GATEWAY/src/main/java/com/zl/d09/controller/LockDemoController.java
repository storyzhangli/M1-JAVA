package com.zl.d09.controller;

import com.zl.d09.common.Result;
import com.zl.d09.lock.DistributedLockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 分布式锁演示接口：模拟高并发扣库存
 *
 * 「使用方式」
 * 1. 先调用 /lock/init-stock 初始化库存；
 * 2. 并发调用 /lock/deduct-stock 扣减库存（锁保护临界区：读库存-扣减-回写）；
 * 3. 观察结果：总扣减数 == 初始化库存数，且无超卖。
 */
@Slf4j
@Tag(name = "D06-分布式锁", description = "Redisson 分布式锁接口演示")
@RestController
@RequestMapping("/lock")
@RequiredArgsConstructor
public class LockDemoController {

    private static final String STOCK_KEY_PREFIX = "stock:sku:";
    private static final String LOCK_KEY_PREFIX = "lock:stock:";

    private final DistributedLockService distributedLockService;
    private final StringRedisTemplate stringRedisTemplate;

    private static final AtomicLong DEDUCT_COUNTER = new AtomicLong(0);

    @Operation(summary = "初始化库存")
    @GetMapping("/init-stock")
    public Result<String> initStock(@RequestParam(defaultValue = "100") int stock) {
        stringRedisTemplate.opsForValue().set(STOCK_KEY_PREFIX + "1001", String.valueOf(stock));
        DEDUCT_COUNTER.set(0);
        return Result.success("库存已初始化为 " + stock);
    }

    @Operation(summary = "固定租约模式扣库存（业务耗时约200ms，租约5秒）")
    @GetMapping("/deduct-stock")
    public Result<String> deductStock(
            @RequestParam(defaultValue = "1001") String skuId,
            @RequestParam(defaultValue = "1") int quantity,
            @RequestParam(defaultValue = "fixed") String mode) throws InterruptedException {

        String lockKey = LOCK_KEY_PREFIX + skuId;

        if ("watchdog".equalsIgnoreCase(mode)) {
            // 看门狗模式：业务时长不确定时自动续期
            return distributedLockService.executeWithLock(lockKey, 3000, () -> doDeduct(skuId, quantity));
        }

        // 固定租约模式：租约 5 秒，业务必须在租约内完成
        return distributedLockService.executeWithLock(
                lockKey, 3000, 5, TimeUnit.SECONDS, () -> doDeduct(skuId, quantity));
    }

    @Operation(summary = "查看累计扣减次数")
    @GetMapping("/deduct-count")
    public Result<Long> deductCount() {
        return Result.success(DEDUCT_COUNTER.get());
    }

    /** 临界区业务：读库存 -> 校验 -> 扣减 -> 回写 */
    private Result<String> doDeduct(String skuId, int quantity) {
        String stockKey = STOCK_KEY_PREFIX + skuId;
        String stockValue = stringRedisTemplate.opsForValue().get(stockKey);
        if (stockValue == null) {
            return Result.fail(400, "库存未初始化，请先调用 /lock/init-stock");
        }

        long currentStock = Long.parseLong(stockValue);
        // 模拟业务耗时：制造并发窗口，验证锁的互斥性
        try {
            TimeUnit.MILLISECONDS.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (currentStock < quantity) {
            return Result.fail(400, "库存不足，当前库存=" + currentStock);
        }

        long newStock = currentStock - quantity;
        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(newStock));
        long count = DEDUCT_COUNTER.incrementAndGet();
        log.info("[扣库存] skuId={}, 扣减={}, 剩余={}, 累计扣减={}", skuId, quantity, newStock, count);
        return Result.success("扣减成功，剩余库存=" + newStock + "，累计扣减=" + count);
    }
}
