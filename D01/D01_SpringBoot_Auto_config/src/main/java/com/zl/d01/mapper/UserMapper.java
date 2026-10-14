package com.zl.d01.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zl.d01.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
