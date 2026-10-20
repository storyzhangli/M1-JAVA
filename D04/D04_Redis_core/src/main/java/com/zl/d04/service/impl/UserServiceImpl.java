package com.zl.d04.service.impl;


import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zl.d04.entity.User;
import com.zl.d04.mapper.UserMapper;
import com.zl.d04.service.UserService;
import org.springframework.stereotype.Service;

/**
 * 用户业务实现，继承 {@link ServiceImpl} 自动实现常用 CRUD 方法。
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
