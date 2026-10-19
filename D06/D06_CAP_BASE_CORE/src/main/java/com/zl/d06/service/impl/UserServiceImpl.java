package com.zl.d06.service.impl;


//import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zl.d06.entity.User;
import com.zl.d06.mapper.UserMapper;
import com.zl.d06.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
