# RBAC 权限模型 + 接口鉴权完整实现

>
> 适配技术栈：SpringBoot3.5 + MyBatis-Plus3.5.12 + Redis
> 模型说明：标准 RBAC 采用「3张主体表 + 2张关联表」：用户表、角色表、权限表 + 用户-角色关联表、角色-权限关联表；多对多关系，可灵活分配权限
> 鉴权方案：轻量拦截器 + Redis Token，阶段1够用，不引入重框架，后续可无缝升级 Spring Security

---

## 一、完整建表 SQL

执行数据库：`ai_db`

```
-- 1. 用户表
CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码',
    nickname VARCHAR(50) COMMENT '昵称',
    phone VARCHAR(20) COMMENT '手机号',
    status TINYINT DEFAULT 1 COMMENT '状态 1启用 0禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 2. 角色表
CREATE TABLE sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    role_code VARCHAR(50) NOT NULL COMMENT '角色编码',
    remark VARCHAR(200) COMMENT '角色描述',
    status TINYINT DEFAULT 1 COMMENT '状态 1启用 0禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- 3. 权限表
CREATE TABLE sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    perm_name VARCHAR(50) NOT NULL COMMENT '权限名称',
    perm_code VARCHAR(100) NOT NULL COMMENT '权限标识',
    perm_type TINYINT DEFAULT 1 COMMENT '类型 1菜单 2按钮',
    parent_id BIGINT DEFAULT 0 COMMENT '父级ID',
    url VARCHAR(200) COMMENT '接口路径',
    remark VARCHAR(200) COMMENT '备注',
    UNIQUE KEY uk_perm_code (perm_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统权限表';

-- 4. 用户-角色关联表
CREATE TABLE sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    KEY idx_user_id (user_id),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 5. 角色-权限关联表
CREATE TABLE sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    KEY idx_role_id (role_id),
    KEY idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- 初始化测试数据
INSERT INTO sys_user (username, password, nickname) VALUES ('admin', '123456', '管理员');
INSERT INTO sys_role (role_name, role_code) VALUES ('超级管理员', 'admin');
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
INSERT INTO sys_permission (perm_name, perm_code, url) VALUES ('用户查询', 'user:list', '/user/*');
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (1, 1);
```

---

## 二、核心实体类

### 1. SysUser 用户

```
package com.aijava.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String phone;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

### 2. SysRole 角色

```
package com.aijava.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class SysRole {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleName;
    private String roleCode;
    private String remark;
    private Integer status;
    private LocalDateTime createTime;
}
```

### 3. SysPermission 权限

```
package com.aijava.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_permission")
public class SysPermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String permName;
    private String permCode;
    private Integer permType;
    private Long parentId;
    private String url;
    private String remark;
}
```

---

## 三、Mapper 接口

```
// SysUserMapper.java
package com.aijava.mapper;

import com.aijava.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
```

```
// SysRoleMapper.java
package com.aijava.mapper;

import com.aijava.entity.SysRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
}
```

```
// SysPermissionMapper.java
package com.aijava.mapper;

import com.aijava.entity.SysPermission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {
}
```

---

## 四、接口鉴权实现（轻量 Redis Token 版）

### 1. 用户上下文工具（ThreadLocal）

`com.aijava.common.UserContext`

```
package com.aijava.common;

public class UserContext {
    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        CURRENT_USER.set(userId);
    }

    public static Long getUserId() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
```

### 2. 鉴权拦截器

`com.aijava.interceptor.AuthInterceptor`

```
package com.aijava.interceptor;

import com.aijava.common.Result;
import com.aijava.common.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 从请求头取token
        String token = request.getHeader("Authorization");
        if (token == null || token.isBlank()) {
            writeError(response, 401, "未登录，缺少token");
            return false;
        }

        // 2. 校验Redis中是否存在
        String key = "auth:token:" + token;
        Object userId = redisTemplate.opsForValue().get(key);
        if (userId == null) {
            writeError(response, 401, "token无效或已过期");
            return false;
        }

        // 3. 存入用户上下文，续期2小时
        UserContext.setUserId(Long.valueOf(userId.toString()));
        redisTemplate.expire(key, 2, TimeUnit.HOURS);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private void writeError(HttpServletResponse response, int code, String msg) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, msg)));
    }
}
```

### 3. 拦截器配置

`com.aijava.config.WebConfig`

```
package com.aijava.config;

import com.aijava.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/error"
                );
    }
}
```

### 4. 登录接口

`com.aijava.controller.AuthController`

```
package com.aijava.controller;

import com.aijava.common.Result;
import com.aijava.entity.SysUser;
import com.aijava.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final SysUserMapper sysUserMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @PostMapping("/login")
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
}
```

---

## 五、测试验证

### 1. 登录获取 token

POST `[http://127.0.0.1:8080/auth/login](http://127.0.0.1:8080/auth/login)`
参数：`username=admin&password=123456`
返回：

```
{
    "code": 200,
    "msg": "success",
    "data": "xxxxxxxtokenxxxxxx"
}
```

### 2. 携带 token 访问受保护接口

GET `[http://127.0.0.1:8080/user/1](http://127.0.0.1:8080/user/1)`
请求头：`Authorization: 上面返回的token`

- 正确：正常返回用户信息
- 不带token / token错误：返回401未登录

---

## 📝 记忆简语

RBAC五张表，用户角色权限加关联；
Redis存token，拦截器校验头；
用户上下文存ThreadLocal，用完记得清。
