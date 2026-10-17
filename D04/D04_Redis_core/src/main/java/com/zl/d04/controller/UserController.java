package com.zl.d04.controller;


import com.zl.d04.common.Result;
import com.zl.d04.entity.User;
import com.zl.d04.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final RedisTemplate<String,Object> redisTemplate;

    @GetMapping("/{id}")
    public Result<User> getUserById(@PathVariable Long id){
        String key = "d02:user:info:"+id;
        //1.查询redis缓存
        User cacheUser = (User) redisTemplate.opsForValue().get(key);
        if(cacheUser != null){
            return Result.success(cacheUser);
        }
        //2.缓存未命中，查数据库
        User dbUser = userService.getById(id);
        if(dbUser == null){
            return Result.fail(404,"用户不存在");
        }
        //3.写入redis，过期10分钟
        redisTemplate.opsForValue().set(key,dbUser,10, TimeUnit.MINUTES);
        return Result.success(dbUser);
    }
}
