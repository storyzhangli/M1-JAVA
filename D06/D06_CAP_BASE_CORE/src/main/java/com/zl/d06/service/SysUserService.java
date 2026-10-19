package com.zl.d06.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zl.d06.entity.SysUser;

public interface SysUserService extends IService<SysUser> {
    /**
     * 根据ID查询用户（带缓存+防击穿）
     */
    SysUser getUserById(Long userId);

    /**
     * 更新用户信息（同步失效缓存）
     */
    boolean updateUser(SysUser user);

    /**
     * 删除用户（同步失效缓存）
     */
    boolean deleteUser(Long userId);
}
