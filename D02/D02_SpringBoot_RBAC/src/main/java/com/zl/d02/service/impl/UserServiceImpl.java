package com.zl.d02.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zl.d02.entity.User;
import com.zl.d02.mapper.UserMapper;
import com.zl.d02.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
