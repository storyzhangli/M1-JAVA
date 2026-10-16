# D02模块增强：数据库索引优化 + Knife4j接口文档集成

>
> 归属阶段：阶段1 Java工程恢复 / D02模块
> 技术基线：SpringBoot 3.5.16 + MyBatis-Plus 3.5.12 + Knife4j 4.5.0 + Redis 6.2.12
> 代码可直接复制进现有项目，零冲突兼容已有RBAC、BCrypt、全局异常体系

---

## 一、数据库索引优化

### 优化原则

针对RBAC五张表的**高频查询场景、关联字段**加索引，遵循最左前缀原则，避免过度索引；只加业务必需的索引，不冗余。

### 1. 索引优化执行SQL

在`ai_db`中执行，已有的唯一索引、关联索引保留，新增高频场景索引：

```
-- ========== 已有索引保留 ==========
-- sys_user: uk_username(username)
-- sys_role: uk_role_code(role_code)
-- sys_user_role: idx_user_id(user_id)、idx_role_id(role_id)
-- sys_role_permission: idx_role_id(role_id)、idx_permission_id(permission_id)

-- ========== 新增业务索引 ==========
-- 1. 用户表：按状态查询用户列表（后台管理高频）
CREATE INDEX idx_user_status ON sys_user(status);

-- 2. 权限表：按父级ID查询子权限（菜单树高频）
CREATE INDEX idx_perm_parent ON sys_permission(parent_id);

-- 3. 权限表：按类型查询权限（菜单/按钮分离高频）
CREATE INDEX idx_perm_type ON sys_permission(perm_type);

-- 4. 用户角色关联：用户ID+角色ID组合查询（权限校验高频）
CREATE INDEX idx_user_role_combo ON sys_user_role(user_id, role_id);
```

### 2. MyBatis-Plus查询性能优化（代码层）

#### 新增分页插件配置

新建 `com.aijava.config.MybatisPlusConfig.java`

```
package com.aijava.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    /**
     * 分页插件 + 优化大表count查询
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // 溢出总页数时返回第一页
        paginationInterceptor.setOverflow(true);
        // 单页最大条数限制，防止恶意拉取全表
        paginationInterceptor.setMaxLimit(500L);
        interceptor.addInnerInterceptor(paginationInterceptor);
        return interceptor;
    }
}
```

#### 查询写法规范（索引命中技巧）

1. 优先使用`LambdaQueryWrapper`，避免字符串字段名写错
2. 避免`select *`，只查需要的字段，触发覆盖索引
3. 避免在索引字段上用函数、模糊查询左通配`%xxx`，会导致索引失效
4. 关联查询优先用MyBatis-Plus的`chain`查询，避免N+1问题

### 3. 慢查询排查工具

#### 开启MySQL慢查询日志（临时调试用）

```
-- 开启慢查询，阈值2秒
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 2;
SET GLOBAL log_output = 'TABLE';

-- 查询慢查询记录
SELECT * FROM mysql.slow_log ORDER BY start_time DESC;
```

#### 索引命中验证

对业务SQL执行`explain`，重点看：

- `type`：至少达到`range`级别，最优`ref`
- `key`：是否命中预期的索引
- `rows`：扫描行数是否远小于总行数

---

## 二、集成Knife4j接口文档（OpenAPI3 + Jakarta版）

>
> 重要：SpringBoot3使用Jakarta体系，必须用`knife4j-openapi3-jakarta`，用旧版javax会直接报错

### 1. pom.xml添加依赖

```
<!-- Knife4j 接口文档 OpenAPI3 适配SpringBoot3 -->
<dependency>
    <groupId>com.github.xiaoymin</groupId>
    <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
    <version>4.5.0</version>
</dependency>
```

>
> Maven Reload 刷新依赖

### 2. 接口文档配置类

新建 `com.aijava.config.Knife4jConfig.java`

```
package com.aijava.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI智能知识助手 API文档")
                        .version("1.0.0")
                        .description("Java AI项目 - 阶段1工程骨架接口文档")
                        .contact(new Contact()
                                .name("开发团队")
                                .email("dev@aijava.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("[https://www.apache.org/licenses/LICENSE-2.0](https://www.apache.org/licenses/LICENSE-2.0)")));
    }
}
```

### 3. application.yml 配置

```
# Knife4j 配置
knife4j:
  enable: true
  openapi:
    title: AI智能知识助手
    description: 阶段1工程骨架接口
    version: 1.0.0
  # 生产环境可设置为false关闭文档
  production: false
```

### 4. 拦截器放行文档静态资源

修改 `WebConfig.java`，在排除路径中添加Knife4j相关路径，避免被鉴权拦截：

```
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(authInterceptor)
            .addPathPatterns("/**")
            .excludePathPatterns(
                    "/auth/login",
                    "/auth/register",
                    "/error",
                    // Knife4j 静态资源放行
                    "/doc.html",
                    "/webjars/**",
                    "/v3/api-docs/**",
                    "/swagger-resources/**",
                    "/favicon.ico"
            );
}
```

### 5. 接口加注解示例

给Controller添加接口说明注解，以`AuthController`为例：

```
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "认证接口", description = "登录注册、Token管理")
public class AuthController {

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户名密码登录，返回Token")
    public Result<String> login(@RequestBody LoginDTO loginDTO) {
        // ...原有逻辑
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "新用户注册，密码BCrypt加密存储")
    public Result<Void> register(@RequestBody LoginDTO loginDTO) {
        // ...原有逻辑
    }
}
```

### 6. 访问验证

启动项目后访问：

- 文档主页：`[http://localhost:8080/doc.html](http://localhost:8080/doc.html)`
- OpenAPI原始JSON：`[http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)`

无需登录即可查看文档；在线调试接口时，可在文档页面的「授权」中填入登录返回的Token，即可调用鉴权接口。

---

## 三、整体验证步骤

1. 执行索引优化SQL，查询`sys_user`表验证索引生效
2. 启动项目，访问`/doc.html`确认接口文档正常展示
3. 在线调试登录接口，验证Token获取正常
4. 携带Token调用用户查询接口，验证分页、索引命中正常

---

## 四、项目目录结构更新

```
ai-knowledge-base
├── pom.xml
└── src/main/java/com/aijava
    ├── AiKnowledgeBaseApplication.java
    ├── common/
    ├── config/
    │   ├── RedisConfig.java
    │   ├── WebConfig.java
    │   ├── PasswordConfig.java
    │   ├── MybatisPlusConfig.java    ✅ 新增
    │   └── Knife4jConfig.java        ✅ 新增
    ├── dto/
    ├── entity/
    ├── mapper/
    ├── service/
    ├── interceptor/
    └── controller/
```

---

## 📝 记忆简语

索引加在高频场，关联字段优先上；
分页插件配MP，慢查explain来排查；
Knife4j用Jakarta版，静态资源要放行；
接口加Tag加Operation，在线调试省Postman。

>
> 对应仓库文档同步更新至 `docs/D2-数据库索引优化-Knife4j集成.md`，踩坑记录存入 `docs/err_docs/`

需要继续做**D02模块收尾：@RequiresPerm注解式权限控制**，还是直接进入**D03 Spring AI 1.1.x 入门模块**？