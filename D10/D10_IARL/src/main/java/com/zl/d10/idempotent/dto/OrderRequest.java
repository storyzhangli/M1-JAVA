package com.zl.d10.idempotent.dto;

import lombok.Data;

/**
 * 下单请求 DTO（Data Transfer Object，数据传输对象）。
 */
@Data
public class OrderRequest {

    /** 用户 ID。 */
    private Long userId;

    /** 商品 ID。 */
    private String goodsId;

    /** 购买数量。 */
    private Integer quantity;

    /** 备注（可选）。 */
    private String remark;
}
