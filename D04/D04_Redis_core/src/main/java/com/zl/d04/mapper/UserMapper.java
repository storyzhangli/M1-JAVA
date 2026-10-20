package com.zl.d04.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zl.d04.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper，继承 {@link BaseMapper} 获得单表 CRUD 方法。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
