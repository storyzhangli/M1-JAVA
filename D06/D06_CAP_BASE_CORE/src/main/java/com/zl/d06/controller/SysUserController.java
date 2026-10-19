package com.zl.d06.controller;

import com.zl.d06.common.Result;
import com.zl.d06.entity.SysUser;
import com.zl.d06.service.SysUserService;
import com.zl.d06.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sys/user")
@RequiredArgsConstructor
public class SysUserController {
    private final UserService userService;
    private final SysUserService sysUserService;
    private final RedisTemplate<String,Object> redisTemplate;

    @GetMapping("/{id}")
    public Result<SysUser> getUserById(@PathVariable Long id){
        //1.查询redis缓存
        SysUser sysUser = sysUserService.getUserById(id);
        if(sysUser == null){
            return Result.fail(404,"用户不存在");
        }
        return Result.success(sysUser);
    }
}
