# BCrypt 密码加密改造（SpringBoot3.5 + JDK21）

>
> 核心要点：**数据库存加密后的密文；登录时：前端传明文密码，后端用BCrypt匹配，不解密**。

## 1. 引入依赖（pom.xml，Spring Security内置BCrypt，无需额外第三方包）

>
> spring-boot-starter-web 自带security密码工具类，但推荐显式引入security-core，避免版本缺失：

```
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
</dependency>
```

>
> SpringBoot父pom会自动管理版本，不用写version。
> Maven reload 刷新依赖。

## 2. 配置BCryptPasswordEncoder Bean

新建 `com.aijava.config.PasswordConfig.java`

```
package com.aijava.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    /**
     * BCrypt密码编码器
     * 工作特点：自动加盐；每次加密结果不一样，比对用matches方法
     */
    @Bean
    public PasswordEncoder passwordEncoder(){
        // 参数strength 4~31，默认10；越大加密越耗CPU，项目默认10足够
        return new BCryptPasswordEncoder(10);
    }
}
```

## 3. 修改 AuthController 登录逻辑

注入 `PasswordEncoder`，**去掉明文equals对比，使用encoder.matches()**

```
package com.aijava.controller;

import com.aijava.common.Result;
import com.aijava.dto.LoginDTO;
import com.aijava.entity.SysUser;
import com.aijava.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final SysUserMapper sysUserMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginDTO loginDTO) {
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
}
```

## 4. 数据库更新：把admin用户密码改成BCrypt密文

>
> BCrypt加密字符串示例，明文`123456`加密后（每次生成不一样，下面这个可以直接先用）
> `$2a$10$NVM0n8ElaRgg7zWO1CxUdei7vWoPg91LE3Qap5AqJn0zCotd1oe1a`

执行SQL：

```
UPDATE sys_user SET password='$2a$10$NVM0n8ElaRgg7zWO1CxUdei7vWoPg91LE3Qap5AqJn0zCotd1oe1a' WHERE username='admin';
```

>
> 测试登录：
> POST /auth/login JSON参数：

```
{
    "username":"admin",
    "password":"123456"
}
```

## 5. 【可选】新增注册接口（用于新增用户，加密密码入库）

加到AuthController：

```
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
```

## ⚠️ 重要原理说明 & 避坑

1. BCrypt **不能解密**，只能比对；每次encode相同明文得到不同密文，因为内置随机盐，不要用 `==` 比较
2. 数据库password字段长度**至少100位**，当前`varchar(100)`足够；不要设置太短，否则密文截断直接校验失败
3. 不要自己写加盐逻辑，BCrypt内部自动完成盐生成与存储
4. 不要用MD5/SHA256，没有加盐很容易彩虹表破解，企业项目统一BCrypt

## 📝记忆简语

BCrypt自动带盐，密文不可反向解；
encode注册存库，matches登录比对；
明文传前端，数据库只存密文字串。