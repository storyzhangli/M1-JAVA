package com.zl.d09.inventory.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    @GetMapping("/{skuId}")
    public String getInventory(@PathVariable Long skuId, HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        return "sku:" + skuId + ",库存:100,TraceID:" + traceId;
    }

    /**
     * 模拟延迟接口，用于测试超时
     */
    @GetMapping("/delay/{seconds}")
    public String delayTest(@PathVariable Integer seconds) throws InterruptedException {
        Thread.sleep(seconds * 1000L);
        return "延迟" + seconds + "秒后返回";
    }

    /**
     * 模拟错误接口，用于测试熔断
     */
    @GetMapping("/error")
    public String errorTest() {
        throw new RuntimeException("库存服务模拟异常");
    }
}