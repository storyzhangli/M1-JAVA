package com.zl.d10.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zl.d10.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
