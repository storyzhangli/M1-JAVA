package com.zl.d10.idempotent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单实体，对应表 order_info。
 */
@Data
@TableName("order_info")
public class OrderInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    private Integer quantity;

    /** CREATED / PAID / CANCELLED。 */
    private String status;

    private String idempotencyKey;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
