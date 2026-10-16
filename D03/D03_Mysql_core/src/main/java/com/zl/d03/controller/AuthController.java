package com.zl.d03.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zl.d03.common.Result;
import com.zl.d03.entity.SysUser;
import com.zl.d03.entity.dto.LoginDTO;
import com.zl.d03.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final SysUserMapper sysUserMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PasswordEncoder passwordEncoder;

    //接收表单参数
    @PostMapping("/loginForm")
    public Result<String> login(@RequestParam String username, @RequestParam String password) {
        // 1. 查询用户
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getStatus, 1));
        if (user == null) {
            return Result.fail(400, "用户不存在或已禁用");
        }

        // 2. 校验密码（阶段1明文演示，后续可加BCrypt加密）
        if (!password.equals(user.getPassword())) {
            return Result.fail(400, "密码错误");
        }

        // 3. 生成token，存入Redis，有效期2小时
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set("auth:token:" + token, user.getId(), 2, TimeUnit.HOURS);

        return Result.success(token);
    }

    /**
     * ## 测试验证
     * ### 1.登录获取 token
     *
     * POST `http://127.0.0.1:8080/auth/login`
     * 参数：`username=admin&password=123456`
     * 综合：http://localhost:8080/auth/login?username=admin&password=123456
     * 返回：
     *
     * ```
     * {
     *     "code": 200,
     *     "msg": "success",
     *     "data": "xxxxxxxtokenxxxxxx"
     * }
     * ```
     *
     * ### 2. 携带 token 访问受保护接口
     *
     * GET `http://127.0.0.1:8080/user/1`
     * 请求头：`Authorization: 上面返回的token`
     *
     * - 正确：正常返回用户信息
     * - 不带 token /token 错误：返回 401 未登录
     */
    /**
     * ## 记忆简语
     *
     * RBAC 五张表，用户角色权限加关联；
     * Redis 存 token，拦截器校验头；
     * 用户上下文存 ThreadLocal，用完记得清。
     */
    // 支持JSON数据类型
    @PostMapping("/loginJson")
    public Result<String> login(@RequestBody LoginDTO loginDTO) {
        String username = loginDTO.getUsername();
        String password = loginDTO.getPassword();

        // 简单判空
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Result.fail(400, "账号密码不能为空");
        }

        // 1. 查询用户
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getStatus, 1));
        if (user == null) {
            return Result.fail(400, "用户不存在或已禁用");
        }

        // 2. 校验密码（阶段1明文演示，生产必须BCrypt加密）
        if (!password.equals(user.getPassword())) {
            return Result.fail(400, "密码错误");
        }

        // 3. 生成token，存入Redis，有效期2小时
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set("auth:token:" + token, user.getId(), 2, TimeUnit.HOURS);

        return Result.success(token);
    }

    @PostMapping("/loginCrypt")
    public Result<String> loginCrypt(@RequestBody LoginDTO loginDTO) {
        String username = loginDTO.getUsername();
        String password = loginDTO.getPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Result.fail(400, "账号密码不能为空");
        }

        // 查询用户
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getStatus, 1));
        if (user == null) {
            return Result.fail(400, "用户不存在或已禁用");
        }

        // ========= BCrypt校验重点 =========
        // 参数1：前端传入明文密码，参数2：数据库中存储的密文
        boolean match = passwordEncoder.matches(password, user.getPassword());
        if (!match) {
            return Result.fail(400, "密码错误");
        }

        // 登录成功生成token存入Redis
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set("auth:token:" + token, user.getId(), 2, TimeUnit.HOURS);
        return Result.success(token);
    }

    @PostMapping("/register")
    public Result<Void> register(@RequestBody LoginDTO loginDTO){
        String username = loginDTO.getUsername();
        String password = loginDTO.getPassword();
        // 判断用户名是否存在
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if(count >0){
            return Result.fail(400,"用户名已存在");
        }
        SysUser newUser = new SysUser();
        newUser.setUsername(username);
        // 加密明文密码再入库
        newUser.setPassword(passwordEncoder.encode(password));
        newUser.setStatus(1);
        sysUserMapper.insert(newUser);
        return Result.success();
    }

}
