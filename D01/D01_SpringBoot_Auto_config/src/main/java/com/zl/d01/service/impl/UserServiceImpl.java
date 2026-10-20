package com.zl.d01.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zl.d01.entity.User;
import com.zl.d01.mapper.UserMapper;
import com.zl.d01.service.UserService;
import org.springframework.stereotype.Service;

/**
 * 用户业务实现。
 *
 * <p>继承 MyBatis-Plus 的 {@link ServiceImpl}，自动实现 {@link UserService} 的常用 CRUD 方法。</p>
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
