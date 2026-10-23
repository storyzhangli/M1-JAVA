package com.zl.d10.idempotent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zl.d10.idempotent.entity.OrderInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单 Mapper。
 */
@Mapper
public interface OrderInfoMapper extends BaseMapper<OrderInfo> {
}
