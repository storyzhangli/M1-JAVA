package com.zl.d10.idempotent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zl.d10.idempotent.entity.IdempotencyRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 幂等记录 Mapper。
 */
@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecord> {
}
