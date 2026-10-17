我已经读取了 GitHub 仓库 `master` 分支当前的 D04 模块，并核对了 `application.yml`、`RedisConfig.java` 和 `pom.xml`。

本次报错的直接原因是：Redisson 在 Spring Boot 启动过程中，无法连接到配置的 Redis 服务 `127.0.0.1:6379`。 这不是 `RedisTemplate` 的序列化代码直接导致的异常。不过，D04 当前的 Redisson 配置还有一个需要修正的地方。

## 一、根据 D04 源码定位问题

直接故障：Redis 连接失败

异常中的关键信息：

`RedisConnectionException: Unable to connect to Redis server: 127.0.0.1:6379`

涉及代码：RedisConfig.java

[查看 D04 RedisConfig.java](https://github.com/storyzhangli/M1-JAVA/blob/master/D04/D04_Redis_core/src/main/java/com/zl/d04/config/RedisConfig.java?utm_source=chatgpt.com)

`redisTemplate()` 方法依赖 `RedisConnectionFactory`。启动时，实际创建的是 Redisson 提供的 `redissonConnectionFactory`，而其所依赖的 `RedissonClient` 创建失败，导致 `RedisTemplate` 也无法创建。

涉及配置：application.yml

[查看 D04 application.yml](https://github.com/storyzhangli/M1-JAVA/blob/master/D04/D04_Redis_core/src/main/resources/application.yml?utm_source=chatgpt.com)

当前 Redis 地址配置为 `127.0.0.1:6379`。如果该地址没有可访问的 Redis 服务，Redisson 初始化就可能失败。

### 1. 异常调用链说明

根据你提供的堆栈，依赖关系如下：

```
Spring Boot 启动
    │
    ▼
创建 redisTemplate
RedisConfig.redisTemplate(...)
    │
    ▼
需要 RedisConnectionFactory
    │
    ▼
创建 redissonConnectionFactory
RedissonAutoConfigurationV2
    │
    ▼
创建 RedissonClient
    │
    ▼
连接 Redis：127.0.0.1:6379
    │
    ▼
连接失败，抛出 RedisConnectionException
    │
    ▼
RedissonClient 创建失败
    │
    ▼
redissonConnectionFactory 创建失败
    │
    ▼
redisTemplate 创建失败
    │
    ▼
Spring Boot 启动失败
```

注意：`UnsatisfiedDependencyException` 是上层的依赖创建失败异常；真正需要排查的是最内层的 Redis 连接异常。

## 二、第一步：确认 Redis 服务是否正常

这是优先级最高的修复步骤。先不要修改 Java 代码，也不必先修改 `RedisConfig.java`。

### 1. Windows PowerShell 检查端口

执行：

```
Test-NetConnection 127.0.0.1 -Port 6379
```

检查输出中的：

```
TcpTestSucceeded : True
```

如何判断：

| 检查结果 | 判断                                                         |
| -------- | ------------------------------------------------------------ |
| `True`   | TCP 端口可以连接，继续检查 Redis 服务响应及配置              |
| `False`  | 当前地址和端口无法建立 TCP 连接，重点检查 Redis 是否启动、端口是否正确、Docker 端口映射是否正确 |

### 2. 如果 Redis 使用 Docker 部署

先检查已有容器：

```
docker ps -a
```

如果已经有 Redis 容器但没有运行，可以启动已有容器：

```
docker start <你的Redis容器名称>
```

如果尚未创建 Redis 容器，可以执行以下命令，使用 Redis 6.2.12：

```
docker run -d `
  --name m1-redis `
  -p 6379:6379 `
  redis:6.2.12 `
  redis-server --appendonly yes
```

然后验证 Redis 是否正常响应：

```
docker exec -it m1-redis redis-cli PING
```

预期结果：

```
PONG
```

Redis 官方文档同样使用 `redis-cli PING` 验证 Redis 是否正常响应。参见《Redis installation and PING | Redis 安装与连通性检查》：[官方文档](https://redis.io/docs/latest/install/install-redis/?utm_source=chatgpt.com)。

[image](https://www.google.com/s2/favicons?domain=https://redis.io\&sz=32)

Docs

+1



如果创建容器时报端口占用，不要反复创建新容器，应先检查 6379 端口是否已被其他 Redis 服务占用。

### 3. 根据 Redis 的实际部署位置选择地址

| 运行方式                                            | D04 中应该使用的地址                                     |
| --------------------------------------------------- | -------------------------------------------------------- |
| D04 在本机运行，Redis 也在本机                      | `127.0.0.1:6379`                                         |
| D04 在本机运行，Redis 在 Docker 中且映射到本机 6379 | `127.0.0.1:6379`                                         |
| D04 和 Redis 都在 Docker 中                         | 通常使用 Docker 网络内的 Redis 服务名，例如 `redis:6379` |
| Redis 运行在远程服务器                              | 使用远程 Redis 的实际地址和端口                          |

特别注意：如果 D04 本身运行在 Docker 容器中，`127.0.0.1` 指向的是 D04 容器自身，而不是另一个 Redis 容器。

## 三、第二步：修正 D04 的 Redisson 配置

我在 D04 当前的 `application.yml` 中发现了一个额外问题：

```
# 当前仓库中的 Redisson 配置
redisson:
  singleServerConfig:
    lockWatchdogTimeout: 30000
    connectionMinimumIdleSize: 2
```

这里存在两个配置问题：

1. Redisson Starter 的配置通常应通过 `spring.redis.redisson.config` 或 `spring.redis.redisson.file` 提供，而不是直接放在顶层的 `redisson` 下。
2. `lockWatchdogTimeout` 是 Redisson `Config` 级别的配置，不应放在 `singleServerConfig` 里面。

因此，当前这段配置并不能按照预期可靠地设置这两个参数。Redisson 官方文档给出了对应的 Spring Boot 配置方式。参见《Redisson Spring Boot Starter Configuration | Redisson Spring Boot Starter 配置》：[官方文档](https://redisson.pro/docs/integration-with-spring/?utm_source=chatgpt.com)。

[image](https://www.google.com/s2/favicons?domain=https://redisson.pro\&sz=32)

Redisson Reference Guide

+1



### 推荐配置

打开：

`D04/D04_Redis_core/src/main/resources/application.yml`

将现有 Redis 配置和顶层 `redisson:` 配置整理为下面的形式：

```
server:
  port: 8080

spring:
  # MySQL 配置保持现有内容
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://127.0.0.1:3306/ai_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456

  # Spring Boot 3.x Redis 连接配置
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password: ""
      database: 0
      timeout: 3s

  # Redisson 专用配置
  redis:
    redisson:
      config: |
        singleServerConfig:
          address: "redis://127.0.0.1:6379"
          database: 0
          connectionMinimumIdleSize: 2

        lockWatchdogTimeout: 30000

# MyBatis-Plus、Knife4j 等其他现有配置继续保留
```

注意： 这是适用于本地无密码 Redis 的配置示例。若 Redis 设置了密码，应在 Spring Data Redis 和 Redisson 的配置中分别正确设置密码；若使用其他地址或端口，也必须同步调整 Redisson 的 `address`。

这段配置解决的是 Redisson 参数的正确绑定问题。它不能代替启动 Redis 服务：如果 6379 端口仍然没有 Redis 监听，修改 YAML 后依然会连接失败。

另外，以上示例展示的是配置结构，不要直接用它覆盖整个 `application.yml`，否则可能丢失 D04 原有的 MyBatis-Plus、Knife4j 等配置。

## 四、第三步：重新验证项目启动

建议严格按以下顺序执行，避免一次改动多个地方，无法判断问题是否解决。

检查 1：Redis 本身可用

执行 `Test-NetConnection 127.0.0.1 -Port 6379`，确认 TCP 连接成功。

检查 2：Redis 能响应命令

执行 `redis-cli PING` 或对应 Docker 命令，预期返回 `PONG`。

检查 3：启动 D04

在项目根目录执行：

```
cd D04
mvn -pl D04_Redis_core -am clean package
```

构建成功后，通过 IDE 启动 `com.zl.d04.D04App`，或者使用已打包的应用启动。

检查 4：确认应用启动成功

检查日志是否还出现 `Unable to connect to Redis server`，并确认 Spring Boot 是否正常启动。

然后继续验证 D04 的用户缓存、缓存击穿防护和 Redisson 分布式锁业务。

## 五、如果还是报错，按异常类型继续定位

| 后续错误信息                                   | 重点排查                                                     |
| ---------------------------------------------- | ------------------------------------------------------------ |
| `Connection refused`                           | Redis 未监听目标端口、服务未启动或目标地址错误               |
| `WRONGPASS` / `NOAUTH`                         | Redis 密码或认证配置错误                                     |
| `Connection timed out`                         | 网络、容器网络、端口映射或防火墙                             |
| `Could not resolve` / DNS 解析错误             | Redis 主机名或 Docker 服务名错误                             |
| `NoSuchMethodError` / `ClassNotFoundException` | 再检查 Redisson 与 Spring Boot / Spring Data Redis 的依赖兼容性 |

这些是不同类型的故障线索，不能把所有 Redis 连接异常都归结为“Redis 没启动”。

## 最终结论

我对当前 D04 的判断是：

第一优先级：检查并启动 Redis 服务，确保 `127.0.0.1:6379` 确实可连接。第二优先级：修正 `application.yml` 中 Redisson 参数的位置和层级。

当前堆栈已经表明，`RedisConfig.redisTemplate()` 是因为 `RedissonClient` 创建失败而无法完成 Bean 初始化；没有证据表明 `RedisTemplate` 的序列化逻辑本身存在问题。

以上仅检查和分析了 GitHub 上当前公开的 D04 源码，未修改你的仓库，也无法直接观察你本机 Redis 的运行状态。