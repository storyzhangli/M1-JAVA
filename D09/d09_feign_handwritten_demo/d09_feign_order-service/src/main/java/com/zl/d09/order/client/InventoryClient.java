package com.zl.d09.order.client;

import com.zl.d09.order.config.FeignInterceptorConfig;
import com.zl.d09.order.fallback.InventoryFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "inventory-service",
        url = "[http://localhost:8082](http://localhost:8082)",
        configuration = FeignInterceptorConfig.class,
        fallbackFactory = InventoryFallbackFactory.class
)
public interface InventoryClient {

    @GetMapping("/inventory/{skuId}")
    InventoryVO getInventory(@PathVariable("skuId") Long skuId);

    @GetMapping("/inventory/delay/{seconds}")
    String delayTest(@PathVariable("seconds") Integer seconds);

    @GetMapping("/inventory/error")
    String errorTest();

    record InventoryVO(Long skuId, Integer stock, String name) {}
}