package com.zl.d10.idempotent.controller;

import com.zl.d10.idempotent.common.ApiResponse;
import com.zl.d10.idempotent.common.OrderResult;
import com.zl.d10.idempotent.dto.OrderCreateRequest;
import com.zl.d10.idempotent.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口，演示基于数据库唯一约束的幂等创建。
 */
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 创建订单。
     *
     * @param idempotencyKey 幂等键（请求头 Idempotency-Key）
     * @param tenantId       租户标识（请求头 X-Tenant-Id，默认 default）
     * @param request        订单参数
     */
    @PostMapping("/api/orders")
    public ResponseEntity<ApiResponse<com.zl.d10.idempotent.dto.OrderResponse>> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId,
            @Valid @RequestBody OrderCreateRequest request) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(40001, "缺少幂等键"));
        }
        if (idempotencyKey.length() > 64) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(40001, "幂等键长度不能超过64位"));
        }

        OrderResult result = orderService.createOrder(tenantId, idempotencyKey, request);
        return ResponseEntity.status(result.getHttpStatus()).body(result.getBody());
    }
}
