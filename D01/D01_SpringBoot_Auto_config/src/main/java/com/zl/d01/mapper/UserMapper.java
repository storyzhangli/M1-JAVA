package com.zl.d01.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zl.d01.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 *
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper}，自动获得单表 CRUD 方法，无需编写 XML。</p>
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
