package com.zl.d09.order.controller;

import com.zl.d09.order.client.InventoryClient;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final InventoryClient inventoryClient;
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_KEY = "traceId";

    public OrderController(InventoryClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    @GetMapping("/{orderId}")
    public String getOrder(@PathVariable Long orderId, HttpServletRequest request) {
        // 从请求头获取TraceID放入MDC
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId != null) {
            MDC.put(MDC_TRACE_KEY, traceId);
        }
        try {
            return "订单" + orderId + "，库存信息：" + inventoryClient.getInventory(1001L);
        } finally {
            MDC.remove(MDC_TRACE_KEY);
        }
    }

    @GetMapping("/delay/{seconds}")
    public String delayTest(@PathVariable Integer seconds) {
        return inventoryClient.delayTest(seconds);
    }

    @GetMapping("/error")
    public String errorTest() {
        return inventoryClient.errorTest();
    }
}