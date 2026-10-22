package com.zl.d09.service.impl;


//import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zl.d09.entity.User;
import com.zl.d09.mapper.UserMapper;
import com.zl.d09.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
