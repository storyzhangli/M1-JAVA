package com.zl.d03.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zl.d03.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统角色 Mapper，继承 {@link BaseMapper} 获得单表 CRUD 方法。
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
}
