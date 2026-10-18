# M1-JAVA 项目代码优化技术文档

> - 仓库地址：https://github.com/storyzhangli/M1-JAVA
> - 扫描分支：master（commit `87b6d39`，另存在 `main`、`feature/d03-redis-user-cache` 两个分支）
> - 扫描方式：GitHub API 全量只读扫描（未对仓库做任何修改）
> - 扫描日期：2026-10-18
> - 评审维度：功能正确性、工程规范、性能风险、安全漏洞、版本兼容性

---

## 目录

- [一、项目总览](#一项目总览)
- [二、总体评估（五维评分）](#二总体评估五维评分)
- [三、P0 阻塞级问题（编译错误）](#三p0-阻塞级问题编译错误)
- [四、安全专项问题](#四安全专项问题)
- [五、架构与工程规范问题](#五架构与工程规范问题)
- [六、性能风险问题](#六性能风险问题)
- [七、依赖与构建问题](#七依赖与构建问题)
- [八、配置管理问题](#八配置管理问题)
- [九、测试 / CI / 部署缺口](#九测试--ci--部署缺口)
- [十、关键修复代码示例](#十关键修复代码示例)
- [十一、优化路线图（P0 / P1 / P2）](#十一优化路线图p0--p1--p2)
- [十二、面试考点附录](#十二面试考点附录)

---

## 一、项目总览

### 1.1 仓库结构

```
M1-JAVA/
├── .gitignore
├── README.md
├── D01/                          # 阶段1：SpringBoot 自动装配 + Maven 骨架
│   ├── pom.xml                   # 聚合父 POM（packaging=pom）
│   ├── mvnw / mvnw.cmd / .mvn/   # Maven Wrapper（仅 D01 有）
│   └── D01_SpringBoot_Auto_config/
│       ├── pom.xml               # MP 3.5.12
│       └── src/main/.../com/zl/d01/
│           ├── D01App.java
│           ├── common/    (Result, GlobalExceptionHandler)
│           ├── config/    (RedisConfig)
│           ├── controller/(HelloController, UserController)
│           ├── entity/    (User → t_user)
│           ├── mapper/    (UserMapper)
│           └── service/   (UserService + impl)
├── D02/                          # 阶段2：RBAC 五张表 + BCrypt + Token 拦截器
│   ├── pom.xml
│   ├── sql/ai_db.sql             # RBAC 五表建库脚本
│   └── D02_SpringBoot_RBAC/      # MP 3.5.12
│       └── src/main/.../com/zl/d02/
│           ├── common/    (+UserContext)
│           ├── config/    (+PasswordConfig, WebConfig)
│           ├── controller/(+AuthController)
│           ├── entity/    (SysUser, SysRole, SysPermission, User, dto/LoginDTO)
│           ├── interceptor/(AuthInterceptor)
│           ├── mapper/    (Sys*Mapper, UserMapper)
│           └── service/
├── D03/                          # 阶段3：MySQL 核心 + Knife4j 接口文档
│   ├── pom.xml
│   ├── sql/ (ai_db.sql, ai_db_msql_slow.sql)
│   └── D03_Mysql_core/           # MP 升级 3.5.17，新增 Knife4j/springdoc/MP 代码生成器
│       └── src/main/.../com/zl/d03/   (+Knife4jConfig, Knife4jOpenApiCustomizer, MybatisPlusConfig)
└── D04/                          # 阶段4：Redis 核心（缓存三防 + Redisson 分布式锁）
    ├── pom.xml
    └── D04_Redis_core/           # MP 3.5.17 + redisson-spring-boot-starter 3.30.0
        └── src/main/.../com/zl/d04/
            ├── common/    (+constants/CacheConstants, +enums/ResultCodeEnum, +exception/BusinessException)
            ├── service/   (+SysUserService/SysUserServiceImpl 旁路缓存+防击穿)
            └── 其余同 D03
```

### 1.2 模块演进关系

D01 → D02 → D03 → D04 为**整体复制式演进**：每个阶段完整拷贝上一阶段工程再叠加新特性。这导致同一份基础代码（`Result`、`GlobalExceptionHandler`、`RedisConfig`、`AuthInterceptor`、`UserController` 等）在仓库中存在 4 份近乎相同的副本，问题也同步复制了 4 份（详见第五章）。

### 1.3 技术栈基线核对

| 项目 | 指令设定基线 | 仓库实际 | 结论 |
|---|---|---|---|
| JDK | 21 LTS | 21（父 POM 锁定 release 21） | ✅ 一致 |
| Spring Boot | 3.5.16 | 3.5.16 | ✅ 一致 |
| Maven | 3.9.8 | Wrapper 仅 D01 提供 | ⚠️ 不一致（D02~D04 无 Wrapper） |
| MyBatis-Plus | 3.5.5+（兼容 SpringBoot3） | D01/D02 = 3.5.12，D03/D04 = 3.5.17 | ⚠️ 版本漂移 |
| Redis | 8.8 | spring-data-redis（版本由 Boot BOM 托管）+ Redisson 3.30.0 | ⚠️ Redisson 偏旧 |
| MySQL | 8.0 | mysql-connector-java 8.0.33（旧坐标） | ⚠️ 坐标已迁移 |
| Knife4j | — | 4.5.0 + 手动锁定 springdoc 2.8.13 | ✅ 可用但写法脆弱 |
| JUnit 5 | JUnit 5 | spring-boot-starter-test 已引入 | ❌ 但全仓库 0 个测试类 |
| Docker Compose | Docker Compose | 无 Dockerfile / docker-compose.yml | ❌ 缺失 |
| Spring AI 1.0.x | 阶段2起接入 | 未开始 | ⏳ 未开始（正常） |

---

## 二、总体评估（五维评分）

| 维度 | 评分（10 分制） | 简评 |
|---|---|---|
| 功能正确性 | 5 | D04 存在 2 处明确的 import 编译错误；登录三套接口并存逻辑混乱；RBAC 只有表结构没有鉴权实现 |
| 工程规范 | 4.5 | 分层结构清晰、注释规范良好；但四模块整体复制、双实体模型（User/SysUser）、缓存 Key 规范不一致、死代码残留 |
| 性能风险 | 6 | D04 已落地缓存三防（穿透/击穿/雪崩）是亮点；但存在递归重试、stdout SQL 日志、缓存双写等问题 |
| 安全漏洞 | 3.5 | 明文密码登录接口残留、异常信息外泄、数据库口令硬编码入库、无登录限流、无注销接口、无越权校验 |
| 版本兼容性 | 6 | MP 版本在两个模块间漂移、IService 新旧包路径混用、MySQL 驱动旧坐标、Redisson 版本偏旧 |

**总评**：作为分阶段学习仓库，目录演进脉络清晰、注释习惯优秀（「原理说明/坑点提示/面试考点」三段式值得保留）。核心短板集中在三点：**D04 编译无法通过的 import 错误**、**安全闭环缺失（明文登录残留+无鉴权+凭据入库）**、**四份复制代码导致问题放大四倍**。按本文档 P0→P1→P2 顺序推进即可收敛。

---

## 三、P0 阻塞级问题（编译错误）

### 3.1 D04 `SysPermissionMapper.java` 导包错误 —— 编译失败

**文件**：`D04/D04_Redis_core/src/main/java/com/zl/d04/mapper/SysPermissionMapper.java`

```java
// 现状（错误）
import com.zl.SysPermission;   // ❌ 不存在 com.zl.SysPermission 这个类

// 修复
import com.zl.d04.entity.SysPermission;   // ✅
```

**根因**：从 D02/D03 复制代码时（D02/D03 中均为正确的 `com.zl.d0x.entity.SysPermission`），IDE 自动导包选错了候选类或手动误删了包路径中段。

### 3.2 D04 `UserServiceImpl.java` 导包错误 —— 编译失败

**文件**：`D04/D04_Redis_core/src/main/java/com/zl/d04/service/impl/UserServiceImpl.java`

```java
// 现状（错误）
import com.zl.service.UserService;   // ❌ 包路径缺了 .d04

// 修复
import com.zl.d04.service.UserService;   // ✅
```

**影响**：以上两处导致 D04 模块 `mvn clean package` 直接失败，违反「代码必须通过一键编译」的阶段验收红线，必须最先修复。

**预防措施（坑点提示）**：复制模块后第一件事是全局重命名包名（IDEA：`Refactor → Rename Package`），再执行一次 `mvn clean compile` 验证，不要依赖肉眼检查 import。建议在仓库根配一个聚合 `pom.xml`，一次构建跑全部 D 模块，让 CI 替你把关（见第九章）。

---

## 四、安全专项问题

### 4.1【高】明文密码登录接口与 BCrypt 接口并存

**文件**：`D02~D04 的 AuthController.java`

`loginForm`、`loginJson` 两个接口仍然使用 `password.equals(user.getPassword())` 明文比对，与 `loginCrypt`（BCrypt）并存。同时 `D02/sql/ai_db.sql` 的初始化数据是明文密码：

```sql
INSERT INTO sys_user (username, password, nickname) VALUES ('admin', '123456', '管理员');
```

由此产生一个隐性 Bug：注册接口写入的是 BCrypt 密文，而 `loginForm/loginJson` 用明文比对 —— **通过 `/auth/register` 注册的用户用这两个接口永远登录失败**，只有 SQL 里明文初始化的 admin 能登上。这不是"演示代码"而是自相矛盾的功能缺陷。

**建议**：删除 `loginForm`/`loginJson`，只保留一个 `/auth/login`（JSON + BCrypt）；初始化 SQL 改为预生成的 BCrypt 密文，例如：

```sql
-- 123456 的 BCrypt 密文（strength=10 预生成）
INSERT INTO sys_user (username, password, nickname)
VALUES ('admin', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5Q9qH3s3T1y6o3o2YI4h0t3lQ0f0y', '管理员');
```

### 4.2【高】异常信息直接外泄给客户端

**文件**：所有模块的 `GlobalExceptionHandler.java`

```java
return Result.fail(500, "服务器内部异常：" + e.getMessage());
```

`e.getMessage()` 可能携带 SQL 语句、表名、堆栈关键信息，生产环境属于信息泄露（OWASP A05）。修复：对外只返回固定文案 + traceId，详细堆栈只落日志（修复代码见 10.1）。

### 4.3【高】凭据硬编码并提交进 Git

`application.yml` 中 `spring.datasource.password: 123456` 明文入库进 Git 历史。即使后续删除，Git 历史中仍可检索。建议：

1. 立即将真实密码改用占位符 `${DB_PASSWORD}`，通过环境变量 / `.env`（加入 .gitignore）注入；
2. 学习仓库虽用本地口令，也应养成习惯：`application.yml` 只提交模板，真实值走 `application-local.yml`（gitignore）。

### 4.4【中】RBAC 只有"五张表"，没有任何鉴权执行逻辑

`sys_role`、`sys_permission`、两张关联表和三个 Mapper 都已建好，但 `AuthInterceptor` 只校验"是否登录"，**从未校验"有无权限"**。当前任何登录用户都能访问全部接口（如 `GET /user/{id}` 可查任意用户）——属于典型的越权（IDOR）风险。

建议最小落地：拦截器第二层增加权限校验——登录时把用户权限码集合缓存进 Redis（`auth:perm:{userId}`），拦截器按 `request.getRequestURI()` 或注解 `@RequiresPerm("user:list")` 比对。这也正好把 docs 里的 RBAC 八股落成代码，是简历上的硬亮点。

### 4.5【中】登录接口无防爆破措施

`/auth/loginCrypt` 无失败次数限制、无验证码、无登录限流。建议最小实现：Redis 计数 `login:fail:{username}`，5 次失败锁定 10 分钟（代码见 10.4）。

### 4.6【中】Token 体系不完整

- **无注销接口**：token 只能等 2 小时自然过期，应提供 `/auth/logout` 主动删除 `auth:token:{token}`；
- **无单点登录控制**：同一用户可无限并发登录，如需"顶号"可用 `auth:uid:{userId} → token` 反查后剔除旧 token；
- **Token 未按 `Bearer ` 规范解析**：约定俗成是 `Authorization: Bearer <token>`，现在直接取整个 Header，后续对接 Knife4j 全局参数和前端拦截器时统一按 Bearer 解析更顺；
- **续期写放大**：`AuthInterceptor` 每次请求都 `expire` 续期 2 小时，高并发下是对 Redis 的额外写压力。可改为"剩余 TTL < 30 分钟才续期"（`getExpire` 先判断）。

### 4.7【低】用户敏感字段外泄隐患

`SysUser` 实体直接参与缓存与潜在的接口返回，其中包含 `password` 字段。若未来 `SysUserService.getUserById` 的结果被某个 Controller 直接返回，密文将外泄。建议：

1. `SysUser.password` 加 `@JsonIgnore`（序列化不输出，但 MyBatis 反序列化不受影响）；
2. 对外返回一律用 `UserVO`（见 5.4）。

---

## 五、架构与工程规范问题

### 5.1【高】四模块整体复制，问题放大四倍

`Result`、`GlobalExceptionHandler`、`RedisConfig`、`UserContext`、`AuthInterceptor`、`UserController` 在 D01~D04 各存一份。带来的实际后果：

- D01 修复过的问题（如果有）不会回流到 D02~D04；
- D04 新增 `BusinessException`/`ResultCodeEnum`，但 D01~D03 的 `GlobalExceptionHandler` 不认识它，行为不一致；
- 缓存 Key 在 D04 内部已经分裂（见 5.2）。

**建议**（学习仓库的推荐做法，二选一）：

- **方案 A（推荐，改动小）**：保留分阶段目录，但把公共类收敛为每个 D 模块内的 `common` 不再回拷旧版——即明确"D04 的 common 是当前最新基线"，README 中声明，后续阶段（Spring AI 起）只基于最新基线扩展；
- **方案 B（工程化更彻底）**：仓库根建聚合父 POM + 抽 `m1-common` 模块，D05 起的业务模块依赖 `m1-common`，同时把 D01~D04 标记为「历史学习快照」冻结。

### 5.2【高】D04 内部缓存体系分裂：两套 Key、两套 TTL、两处回写

| 位置 | Key 前缀 | TTL | 防护 |
|---|---|---|---|
| `SysUserServiceImpl`（新） | `sys:user:`（CacheConstants） | 30min + 随机 5min | 空值缓存 + Redisson 锁，✅ 规范 |
| `UserController.getUserById`（残留） | `d02:user:info:` 硬编码 | 10min 固定 | ❌ 无任何防护 |

问题点：

1. `UserController` 里 `"d02:user:info:"` 是 D02 时代的残留 Key，与 `CacheConstants.USER_CACHE_PREFIX` 并存，同一个用户可能被以两套 Key 各缓存一份；
2. `UserController` 的缓存逻辑绕过了 `SysUserServiceImpl` 的全部防护（无空值标记、无锁、固定 TTL 有雪崩风险）；
3. `UserController` 查的是 `t_user`（User 实体），`SysUserServiceImpl` 查的是 `sys_user`（SysUser 实体）——**两张表、两个"用户"模型并存**，新人根本无法判断该用哪个；
4. `UserController` 回写缓存后，`updateUser/deleteUser` 只删 `sys:user:` 前缀，不会失效 `d02:user:info:` —— 脏数据最长可残留 10 分钟。

**修复方向**：删除 `UserController` 内的手写缓存，直接注入 `SysUserService`；并决策 `User(t_user)` 与 `SysUser(sys_user)` 二选一保留（建议保留 `sys_user`，删除 `User`/`UserMapper`/`UserService`/`UserServiceImpl` 整条链路）。

### 5.3【中】`ResultCodeEnum` 定义了却没有接入 `Result`

D04 已经新建了规范的 `ResultCodeEnum`，但 `Result.success()/fail()` 仍然硬编码 `200` / 散落的 `400/401/404/500` 魔法数字，枚举形同虚设。修复见 10.1（`Result` 提供 `of(ResultCodeEnum)` 静态方法，Controller 全部改用枚举）。

### 5.4【中】分层模型不完整：Entity 直达 Controller

当前 `Entity(DB) == 出参` 混用，缺 VO 层。按指令设定的分层规范应补齐：

```
controller → dto(入参, @Validated) → service → entity(DB) → vo(出参, 脱敏)
```

最小动作：`LoginDTO` 加校验注解（见 10.3），用户查询返回 `UserVO`（不含 password）。

### 5.5【中】`UserContext` 的 ThreadLocal 风险

`UserContext` 本身实现正确（`afterCompletion` 里 `clear()` 了，值得肯定）。两个隐患需要写进「坑点提示」：

1. **JDK21 虚拟线程场景**：如果阶段7引入 `spring.threads.virtual.enabled=true`，ThreadLocal 仍可工作，但任何 `@Async`/自建线程池/`CompletableFuture` 场景都会导致上下文丢失——需要在文档中明确"UserContext 仅在请求线程内有效"；
2. **异步链路**：后续 SSE / WebFlux / 消息消费线程里调用 `UserContext.getUserId()` 会拿到 null，届时应显式传参。

### 5.6【低】死代码与遗留残留

| 位置 | 问题 |
|---|---|
| `D04App.java` | 启动类同时挂 `@RestController` + `@RequestMapping home()`，返回 `"Hello World \| hello D01"` —— D01 的残留文案，且启动类不应承担 Controller 职责 |
| `HelloController.java`（各模块） | `@RestController` 类内 `sayHello()` 无任何 `@XxxMapping` 注解，是永远无法访问的死代码 |
| `UserController`（D04） | 见 5.2，整体应重构 |
| `Knife4jConfig` | 注释残留 `// todo 路径不对，无数据`——todo 不应留在入库代码里，要么修复要么删掉 |
| `application.yml`（D03/D04） | 同样残留 `# todo 重命名包名，此处需要手动修改` |

### 5.7【低】README 中的文档链接使用了 Windows 反斜杠

README 中多处链接形如 `[...](docs\D2-...md)`，反斜杠在 GitHub/Typora 的 Web 渲染下是**失效链接**，应全部改为正斜杠 `/`。

---

## 六、性能风险问题

### 6.1【中】`SysUserServiceImpl` 加锁失败后的递归重试

```java
if (!lockAcquired) {
    Thread.sleep(200);
    return getUserById(userId);   // ❌ 递归
}
```

风险：

1. 热点 Key 失效 + 高并发时，大量线程递归自调用，极端情况栈深度持续增长（StackOverflowError 风险）；
2. `Thread.sleep(200)` 阻塞的是 Tomcat 工作线程，降低整体吞吐；
3. 每次递归都重新走一遍完整流程（含第一次缓存查询），逻辑重复。

**修复**：改为「有限次数循环 + 兜底降级」，见 10.2。

### 6.2【中】生产 SQL 日志使用 StdOutImpl

所有模块 `application.yml`：`log-impl: org.apache.ibatis.logging.stdout.StdOutImpl`——stdout 同步打印在高并发下是明显瓶颈。建议：

```yaml
mybatis-plus:
  configuration:
    # 开发期排错用 stdout；默认环境改为 slf4j，由 logback 异步输出
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
```

配合 `logging.level.com.zl.d04.mapper: debug` 按需开启 SQL 打印。

### 6.3【中】分页插件配置了但全仓库无一个分页查询

`MybatisPlusConfig` 配了分页插件 + `maxLimit=500`（配置本身是规范的），但代码中没有任何 `Page` 查询用例，`mapper-locations: classpath:mapper/**/*.xml` 配置了但没有任何 XML 文件。建议：在 D05 或 D04 补一个「用户分页列表」接口作为模板（`Page<SysUser>` + `LambdaQueryWrapper`），否则配置是死配置，面试被问"你项目里分页怎么用的"会答空。

### 6.4【低】`AuthInterceptor` 每次请求强制续期

见 4.6，改为条件续期可减少约 50% 的 Redis 写操作。

### 6.5【低】`HikariCP` 未做任何参数调整

当前连接池全部默认（maximumPoolSize=10）。学习阶段够用，但文档基线既然写了"生产级"，建议在 yml 中显式声明并注释含义（`maximum-pool-size`、`connection-timeout`、`max-lifetime`），这本身也是面试高频点。

---

## 七、依赖与构建问题

### 7.1【高】MyBatis-Plus 版本漂移 + IService 新旧包混用

- D01/D02：`mybatis-plus.version = 3.5.12`，代码用 `com.baomidou.mybatisplus.extension.service.IService`
- D03/D04：`mybatis-plus.version = 3.5.17`，代码用 `com.baomidou.mybatisplus.spring.service.IService`

事实核查：MP 自 3.5.9 起将 Spring 耦合类拆分到 `mybatis-plus-spring` 模块，`IService` 的新规范路径为 `com.baomidou.mybatisplus.spring.service.IService`（在 MP 官方仓库中当前仅存此一份）；官方 3.5.9 变更说明同时建议业务层逐步以 `CrudRepository` 替代 `IService`。D03/D04 的写法是**正确的新路径**，D01/D02 的注释把新路径标注为待替换，认知需要反过来。

**建议**：

1. 全仓库统一 `mybatis-plus.version = 3.5.17`；
2. D01/D02 的 import 统一改为 `com.baomidou.mybatisplus.spring.service.*`；
3. 在 D05 新代码中评估 `CrudRepository` 替代 `IService`（官方推荐方向）。

### 7.2【中】MySQL 驱动使用旧坐标

`mysql:mysql-connector-java:8.0.33` 官方已迁移至 `com.mysql:mysql-connector-j`。新坐标由 Spring Boot BOM 托管，无需手写版本：

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

### 7.3【中】代码生成器不应进业务运行时依赖

D03/D04 的 POM 把 `mybatis-plus-generator` + `velocity-engine-core` 作为默认（compile）依赖打进业务包，白白增加几十 MB 传递依赖。建议：`<scope>test</scope>` 或抽到独立的 `m1-generator` 模块。

### 7.4【中】版本管理分散，无统一 dependencyManagement

各 D 模块各自手写版本（MP、Redisson、springdoc、Knife4j 散在各处）。建议仓库根建聚合父 POM，用 `<dependencyManagement>` + `<properties>` 统一管理，与指令设定的「版本红线」一一对应（见 10.5）。

### 7.5【低】Redisson 3.30.0 偏旧且配置双份

- Redisson 3.30.0 发布于 2024 年底，建议升级到与 Spring Boot 3.5 匹配的 3.4x+ 版本；
- Redis 连接参数在 `spring.data.redis.*` 和 `spring.redis.redisson.config` 里各写一份，双源配置易不一致。redisson-spring-boot-starter 支持直接复用 `spring.data.redis`，建议删除 Redisson 专属段，只保留 `lockWatchdogTimeout` 等必要项。

### 7.6【低】Maven Wrapper 只有 D01 有

D02~D04 缺 `mvnw`，使用者本机 Maven 版本不可控。统一在仓库根提供一份 Wrapper 即可。

---

## 八、配置管理问题

| 问题 | 现状 | 建议 |
|---|---|---|
| 环境未隔离 | 仅单一 `application.yml`，生产/开发混写 | 拆 `application.yml`（公共）+ `application-dev.yml` + `application-prod.yml`，`spring.profiles.active` 切换 |
| Knife4j 生产暴露 | `production: false` 被注释，等于生产也开文档 | prod profile 中 `knife4j.production: true` |
| 密码明文 | 见 4.3 | 环境变量注入 |
| 日志配置缺失 | 无 logback 配置、无日志文件输出 | 增加 `logback-spring.xml`：按天滚动 + 异步 appender + 错误单独归档 |
| server.port 硬编码 8080 | 四模块同端口 | 不影响（分模块单独启动），但 README 应注明 |

---

## 九、测试 / CI / 部署缺口

1. **测试全空**：全仓库没有任何 `src/test` 代码。基线声明了 JUnit 5 + Spring Boot Test，建议最小闭环：`AuthController` 的 `@WebMvcTest`（登录成功/密码错误/参数为空三用例）+ `SysUserServiceImpl` 的缓存三防单测（Mock RedisTemplate/RedissonClient）。这是阶段验收的硬指标，也是面试"你项目测试覆盖率多少"的素材。
2. **无 CI**：GitHub 仓库可直接启用 Actions——根聚合 POM + `mvn -B verify`，每次 push 自动编译四个模块，3.1/3.2 这类编译错误今后在提交时就会被拦下。
3. **无 Docker Compose**：基线声明了 Docker Compose 但仓库无此文件。建议根目录补一份 `docker-compose.yml`（mysql:8.0 + redis:7 + 初始化 SQL 挂载），一条命令拉起依赖环境。
4. **分支管理混乱**：`master` 与 `main` 并存（内容不同），`feature/d03-redis-user-cache` 疑似未合并。建议：确定唯一主干（保留 `master` 或迁 `main`），删除冗余分支，后续每阶段走 `feature/dxx-*` 分支 + PR 合并，这正是 Git 工程化八股的实战素材。

---

## 十、关键修复代码示例

> 以下代码均按仓库现有分层、命名、注释风格输出，可直接放入对应模块。仅作优化建议，未推送到仓库。

### 10.1 `Result` + `ResultCodeEnum` 打通 + 全局异常脱敏

```java
package com.zl.d04.common;

import com.zl.d04.common.enums.ResultCodeEnum;
import lombok.Data;

@Data
public class Result<T> {
    private Integer code;
    private String msg;
    private T data;

    public static <T> Result<T> success(T data) {
        return of(ResultCodeEnum.SUCCESS, data);
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> fail(ResultCodeEnum codeEnum) {
        return of(codeEnum, null);
    }

    public static <T> Result<T> fail(ResultCodeEnum codeEnum, String msg) {
        Result<T> r = new Result<>();
        r.setCode(codeEnum.getCode());
        r.setMsg(msg);
        return r;
    }

    private static <T> Result<T> of(ResultCodeEnum codeEnum, T data) {
        Result<T> r = new Result<>();
        r.setCode(codeEnum.getCode());
        r.setMsg(codeEnum.getMessage());
        r.setData(data);
        return r;
    }
}
```

```java
package com.zl.d04.common;

import com.zl.d04.common.enums.ResultCodeEnum;
import com.zl.d04.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：错误码与文案受控，可原样返回 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验异常：返回第一条校验失败信息 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse(ResultCodeEnum.PARAM_ERROR.getMessage());
        return Result.fail(ResultCodeEnum.PARAM_ERROR, msg);
    }

    /** 未知异常：对外脱敏，堆栈只落日志 「坑点提示」e.getMessage() 不得返回给前端 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常：", e);
        return Result.fail(ResultCodeEnum.SYSTEM_ERROR);
    }
}
```

### 10.2 `SysUserServiceImpl` 防击穿：递归改有限重试循环

```java
@Override
public SysUser getUserById(Long userId) {
    String cacheKey = CacheConstants.USER_CACHE_PREFIX + userId;

    // 「原理说明」最多重试3次：避免递归导致的栈增长与线程长时间阻塞
    for (int attempt = 0; attempt < 3; attempt++) {
        Object cacheValue = redisTemplate.opsForValue().get(cacheKey);
        if (cacheValue != null) {
            return isNullMark(cacheValue) ? null : (SysUser) cacheValue;
        }

        RLock lock = redissonClient.getLock(CacheConstants.USER_LOCK_PREFIX + userId);
        try {
            if (lock.tryLock(3, 30, TimeUnit.SECONDS)) {
                try {
                    // 双重检查
                    cacheValue = redisTemplate.opsForValue().get(cacheKey);
                    if (cacheValue != null) {
                        return isNullMark(cacheValue) ? null : (SysUser) cacheValue;
                    }
                    SysUser user = sysUserMapper.selectById(userId);
                    if (user == null) {
                        redisTemplate.opsForValue().set(cacheKey, NULL_MARK,
                                CacheConstants.NULL_VALUE_TTL, TimeUnit.SECONDS);
                    } else {
                        long ttl = CacheConstants.USER_CACHE_BASE_TTL
                                + ThreadLocalRandom.current().nextLong(CacheConstants.TTL_RANDOM_OFFSET_MAX);
                        redisTemplate.opsForValue().set(cacheKey, user, ttl, TimeUnit.SECONDS);
                    }
                    return user;
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
            // 「坑点提示」拿不到锁不休眠硬等：短暂退避后下一轮循环重新读缓存
            Thread.sleep(100L * (attempt + 1));   // 100ms / 200ms 退避
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCodeEnum.LOCK_INTERRUPTED);
        }
    }
    // 兜底降级：3次仍未命中，直接查库但不回写（宁可打库一次，不可无限堆积线程）
    log.warn("缓存锁重试耗尽，降级直查数据库, userId={}", userId);
    return sysUserMapper.selectById(userId);
}

private boolean isNullMark(Object value) {
    return value instanceof String s && NULL_MARK.equals(s);
}
```

> 「面试考点」缓存击穿标准答案四要素：**互斥锁 + 双重检查 + 空值缓存（防穿透）+ TTL 随机偏移（防雪崩）**，再加一条**兜底降级**就是生产级答案。

### 10.3 `LoginDTO` 参数校验

```java
package com.zl.d04.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过50")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度须在6~64位之间")
    private String password;
}
```

Controller 入参加 `@Validated`：

```java
@PostMapping("/login")
public Result<String> login(@Validated @RequestBody LoginDTO loginDTO) { ... }
```

POM 需补依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

### 10.4 登录防爆破（Redis 计数锁定）

```java
private static final String LOGIN_FAIL_PREFIX = "auth:login:fail:";
private static final int MAX_FAIL_TIMES = 5;
private static final long LOCK_MINUTES = 10;

private void checkLoginLocked(String username) {
    String key = LOGIN_FAIL_PREFIX + username;
    Long fails = redisTemplate.opsForValue().increment(key, 0); // 仅读取
    if (fails != null && fails >= MAX_FAIL_TIMES) {
        throw new BusinessException(400, "失败次数过多，账号已锁定10分钟");
    }
}

private void recordLoginFail(String username) {
    String key = LOGIN_FAIL_PREFIX + username;
    Long fails = redisTemplate.opsForValue().increment(key);
    if (fails != null && fails == 1) {
        redisTemplate.expire(key, LOCK_MINUTES, TimeUnit.MINUTES);
    }
}
// 登录成功后：redisTemplate.delete(LOGIN_FAIL_PREFIX + username);
```

### 10.5 根聚合 POM 骨架（统一版本红线）

```xml
<!-- 仓库根目录 pom.xml -->
<project ...>
    <groupId>org.ai.m1</groupId>
    <artifactId>m1-java</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <module>D01</module>
        <module>D02</module>
        <module>D03</module>
        <module>D04</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <spring-boot.version>3.5.16</spring-boot.version>
        <mybatis-plus.version>3.5.17</mybatis-plus.version>
        <redisson.version>3.30.0</redisson.version>
        <springdoc.version>2.8.13</springdoc.version>
        <knife4j.version>4.5.0</knife4j.version>
    </properties>
    <!-- 各 D 模块 parent 改为引用本 POM，dependencyManagement 在此统一锁定版本 -->
</project>
```

---

## 十一、优化路线图（P0 / P1 / P2）

### P0 —— 立即修复（阻塞编译 / 高危安全）

| # | 事项 | 涉及文件 |
|---|---|---|
| 1 | 修复 D04 两处 import 编译错误 | `SysPermissionMapper.java`、`UserServiceImpl.java` |
| 2 | 删除明文登录接口，统一 `/auth/login`（BCrypt） | `AuthController.java`（D02~D04） |
| 3 | 初始化 SQL 改 BCrypt 密文 | `D02/sql/ai_db.sql` |
| 4 | 全局异常对外脱敏 | `GlobalExceptionHandler.java`（D01~D04） |
| 5 | yml 口令改环境变量占位 | 全部 `application.yml` |

### P1 —— 一周内完成（架构收敛 / 安全闭环）

| # | 事项 | 说明 |
|---|---|---|
| 1 | 缓存体系统一：`UserController` 复用 `SysUserService`，删除硬编码 Key | 5.2 |
| 2 | 决策 User/SysUser 双模型去留，删冗余链路 | 5.2 |
| 3 | `Result` 接入 `ResultCodeEnum`，消灭魔法数字 | 10.1 |
| 4 | 拦截器补权限校验（RBAC 闭环）+ `/auth/logout` | 4.4 / 4.6 |
| 5 | 统一 MP 3.5.17 + IService 新包路径 | 7.1 |
| 6 | 仓库根聚合 POM + 根级 Maven Wrapper | 7.4 / 7.6 |
| 7 | 补 `src/test` 最小测试集（登录 + 缓存三防） | 九 |
| 8 | `SysUserServiceImpl` 递归改有限重试 | 10.2 |

### P2 —— 进入阶段5（Spring AI）前完成

| # | 事项 | 说明 |
|---|---|---|
| 1 | GitHub Actions CI（`mvn -B verify`） | 九 |
| 2 | `docker-compose.yml`（MySQL 8.0 + Redis） | 九 |
| 3 | profile 环境隔离 + Knife4j 生产关闭 + logback 配置 | 八 |
| 4 | 分支治理：单一主干 + feature 分支流 | 九 |
| 5 | 分页查询模板接口（盘活 MybatisPlusConfig） | 6.3 |
| 6 | 登录防爆破 + Token 条件续期 | 10.4 / 4.6 |
| 7 | MySQL 驱动新坐标、Redisson 升级、generator 移出运行时 | 七 |
| 8 | 清理死代码（HelloController、D04App.home、todo 注释、README 反斜杠链接） | 5.6 / 5.7 |

---

## 十二、面试考点附录

| 问题来源 | 对应面试题 | 标准答案要点 |
|---|---|---|
| 缓存三防（D04） | 缓存穿透/击穿/雪崩的区别与方案？ | 穿透=查不存在数据→空值缓存/布隆过滤器；击穿=热点Key失效→互斥锁+双重检查；雪崩=大面积同时失效→TTL随机偏移+多级缓存 |
| Redisson 锁 | tryLock(3,30) 两个参数含义？看门狗机制？ | waitTime=3s 等待、leaseTime=30s 持有；传 leaseTime 后看门狗不生效，不传才自动 1/3 周期续期 |
| ThreadLocal | UserContext 为什么要 clear？ | Tomcat 线程池复用线程，不清除会导致内存泄漏+串号 |
| BCrypt | 为什么每次加密结果不同还能校验？ | 密文中内嵌盐值，matches 时取出盐重新计算比对 |
| MP 分页 | PaginationInnerInterceptor 原理？ | SQL 拦截器改写 LIMIT + 自动 count；maxLimit 防恶意全表拉取 |
| Cache Aside | 为什么先更库再删缓存，而不是先删缓存？ | 先删缓存的并发窗口内，读线程会把旧值回写；先更库再删缓存将脏数据窗口压缩到毫秒级 |
| 全局异常 | 为什么不能把 e.getMessage() 返回前端？ | 信息泄露（表结构/SQL/堆栈），对外固定文案+traceId，堆栈只落日志 |

**记忆简语**：编译先看 import，复制必跑 mvn；密码一律 BCrypt，异常绝不回原文；缓存四件宝（锁、双检、空值、随机 TTL），Key 要进常量表；五表 RBAC 不算完，拦截器里见真章。

---

> 本文档基于 2026-10-18 对 `storyzhangli/M1-JAVA` 仓库 master 分支（commit `87b6d39`）的只读扫描生成，未对仓库代码做任何修改。
