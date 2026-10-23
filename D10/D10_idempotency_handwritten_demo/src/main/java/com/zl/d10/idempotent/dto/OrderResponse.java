package com.zl.d10.idempotent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 订单响应数据。
 */
@Data
@AllArgsConstructor
public class OrderResponse {

    /** 订单 ID。 */
    private Long orderId;

    /** 商品 ID。 */
    private Long productId;

    /** 数量。 */
    private Integer quantity;

    /** 订单状态。 */
    private String status;
}
