package com.zl.d10.idempotent.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 订单创建请求。
 */
@Data
public class OrderCreateRequest {

    @NotNull(message = "productId不能为空")
    @Positive(message = "productId必须大于0")
    private Long productId;

    @NotNull(message = "quantity不能为空")
    @Positive(message = "quantity必须大于0")
    private Integer quantity;
}
