package com.zl.d10.idempotent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zl.d10.idempotent.entity.IdempotencyRecord;
import com.zl.d10.idempotent.mapper.IdempotencyRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 幂等记录服务：负责幂等记录的写入、查询与状态变更。
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    public static final String PROCESSING = "PROCESSING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";

    private final IdempotencyRecordMapper recordMapper;

    /**
     * 插入 PROCESSING 记录。
     * 命中唯一约束时抛出 DuplicateKeyException，事务回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public void insertProcessing(IdempotencyRecord record) {
        recordMapper.insert(record);
    }

    /**
     * 按 (租户, 接口, 幂等键) 查询记录。
     */
    @Transactional(readOnly = true)
    public IdempotencyRecord find(String tenantId, String apiCode, String key) {
        return recordMapper.selectOne(Wrappers.<IdempotencyRecord>lambdaQuery()
                .eq(IdempotencyRecord::getTenantId, tenantId)
                .eq(IdempotencyRecord::getApiCode, apiCode)
                .eq(IdempotencyRecord::getIdempotencyKey, key));
    }

    /**
     * 将记录标记为 FAILED，用于业务失败后允许重试。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(String tenantId, String apiCode, String key) {
        IdempotencyRecord rec = find(tenantId, apiCode, key);
        if (rec != null) {
            rec.setStatus(FAILED);
            rec.setUpdatedAt(LocalDateTime.now());
            recordMapper.updateById(rec);
        }
    }
}
