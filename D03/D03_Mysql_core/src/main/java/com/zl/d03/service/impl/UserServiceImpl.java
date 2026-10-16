package com.zl.d03.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zl.d03.entity.User;
import com.zl.d03.mapper.UserMapper;
import com.zl.d03.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
