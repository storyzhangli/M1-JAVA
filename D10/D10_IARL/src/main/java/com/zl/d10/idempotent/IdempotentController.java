package com.zl.d10.idempotent;

import com.zl.d10.common.Result;
import com.zl.d10.idempotent.dto.OrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 幂等演示接口。
 *
 * <p>「使用方式」请求头携带 {@code Idempotency-Key}，POST 提交订单：</p>
 * <ul>
 *     <li>首次 K1：业务执行一次；</li>
 *     <li>相同参数再次 K1：返回原结果，不重复执行；</li>
 *     <li>并发 K1：仅一个进入业务；</li>
 *     <li>不同参数复用 K1：拒绝；</li>
 *     <li>新键 K2：作为新请求处理。</li>
 * </ul>
 */
@Slf4j
@Tag(name = "D10-接口幂等", description = "基于 Redis 的幂等校验")
@RestController
@RequestMapping("/idempotent")
public class IdempotentController {

    /** 实际进入业务的次数（用于验证「业务只执行一次」）。 */
    private final AtomicLong businessExecuted = new AtomicLong(0);

    @Operation(summary = "下单（幂等），请求头需携带 Idempotency-Key")
    @PostMapping("/order")
    @Idempotent(expireSeconds = 300)
    public Result<Map<String, Object>> createOrder(
            @RequestBody OrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) throws InterruptedException {

        // 仅占位以展示幂等键（真实键由切面读取），不参与业务
        long execNo = businessExecuted.incrementAndGet();
        log.info("【实际执行业务】进入下单逻辑，幂等键={}, 第 {} 次执行, 参数={}", idemKey, execNo, request);

        // 拉宽并发窗口，便于演示并发请求仅一个进入业务
        Thread.sleep(500);

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("orderNo", "ORD-" + System.currentTimeMillis());
        order.put("userId", request.getUserId());
        order.put("goodsId", request.getGoodsId());
        order.put("quantity", request.getQuantity());
        order.put("execNo", execNo);
        return Result.success(order);
    }

    @Operation(summary = "查看业务实际执行次数")
    @GetMapping("/exec-count")
    public Result<Long> execCount() {
        return Result.success(businessExecuted.get());
    }
}
