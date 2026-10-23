package com.zl.d10.idempotent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 幂等记录实体，对应表 idempotency_record。
 */
@Data
@TableName("idempotency_record")
public class IdempotencyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantId;

    private String apiCode;

    private String idempotencyKey;

    private String requestDigest;

    /** PROCESSING / SUCCEEDED / FAILED。 */
    private String status;

    private String resultBody;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime expireAt;
}
