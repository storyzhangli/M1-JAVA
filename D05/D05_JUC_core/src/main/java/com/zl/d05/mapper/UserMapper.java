package com.zl.d05.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zl.d05.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
