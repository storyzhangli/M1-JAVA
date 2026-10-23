package com.zl.d10.idempotent.common;

import com.zl.d10.idempotent.dto.OrderResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 服务层返回结果，携带目标 HTTP 状态码与响应体。
 */
@Getter
@AllArgsConstructor
public class OrderResult {

    private final int httpStatus;

    private final ApiResponse<OrderResponse> body;
}
