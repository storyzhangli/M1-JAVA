# D09-01 微服务三件套（Gateway + OpenFeign + 熔断降级）项目迭代全量文档

> 工程位置：`E:\AI\project\AI-Learning-one\M1-Java\D09`
> 技术基线：Java 21、Spring Boot 3.5.16、Spring Cloud 2025.0.0、Spring Cloud Alibaba 2025.0.0.0
> 中间件：Nacos 3.0.3（standalone，8848）、Redis 6.2.12（6379）
> 文档性质：本次迭代的全量记录（扫描 → 设计 → 实现 → 构建 → 验证 → 结论）

---

## 第一章 功能概述

### 1.1 本次实现的功能

在 `D09` 聚合工程下落地一套可运行的微服务三件套，并进行**真实启动与全链路验证**：

| 模块 | 服务名 | 端口 | 角色 |
| --- | --- | --- | --- |
| `D09_GATEWAY` | `d09-gateway` | 9000 | 反应式网关：路由转发 + Redis 限流 |
| `D09_SERVICE_PROVIDER` | `d09-provider` | 8081 | 服务提供者：提供 echo / slow / error 接口 |
| `D09_SERVICE_CONSUMER` | `d09-consumer` | 8082 | 服务消费者：OpenFeign 调用 + traceId 透传 + 熔断降级 |

三大能力：

- **A. Gateway 网关**：启动成功；配置路由把请求转发给 Provider；配置一种可实际触发的限流；验证正常转发与超限响应。
- **B. 服务间调用**：Consumer 使用 OpenFeign 按服务名调用 Provider；拦截器传递 traceId；Provider 日志可见相同标识；设置连接/读取超时并记录失败行为。
- **C. 熔断降级**：正常调用；受控让下游超时/不可用验证 Fallback；说明熔断、超时、重试的区别。
- 统一使用 **Nacos 服务发现**，实际验证服务注册与按服务名调用。

### 1.2 解决的问题

- 学会「服务注册发现 + 按服务名调用」的完整链路，而非写死 IP:端口。
- 掌握网关统一入口、路由转发与限流的落地方式。
- 掌握声明式调用 Feign 的超时控制、traceId 透传与熔断降级。
- 理解熔断 / 超时 / 重试三者的边界，避免叠加放大下游压力。

### 1.3 边界与未实现内容

- 本次为**本地学习环境**，认证、配置中心、网关鉴权、容器化部署不在本次范围。
- 未接入网关层的统一鉴权（Spring Security）。
- 未做集群多实例（单实例验证），但负载均衡依赖（`spring-cloud-starter-loadbalancer`）已就绪。
- 空模块 `d09_feign_handwritten_demo` 保持原样（仅骨架，无源码），不在本次范围。

---

## 第二章 扫描结论与基础骨架

### 2.1 扫描范围

按任务要求对 `D09` 下三个模块及父 POM 进行有针对性扫描：

- 聚合根：`D09\pom.xml`
- 网关：`D09\D09_GATEWAY`
- 消费者：`D09\D09_SERVICE_CONSUMER`
- 提供者：`D09\D09_SERVICE_PROVIDER`

### 2.2 扫描发现的关键事实

1. **D09_GATEWAY 是 D08 业务模块的完整副本**：
   - 含 `spring-boot-starter-web`、MyBatis-Plus、Redisson、MySQL、Knife4j 及大量 `common/config/controller/entity/lock/mapper/service` 源码（启动类名为 `D09App`）。
   - 这与 Spring Cloud Gateway 的**反应式 WebFlux 模型根本冲突**：Servlet（`spring-boot-starter-web`）与 WebFlux 不能共存。
2. **D09_SERVICE_CONSUMER / D09_SERVICE_PROVIDER 仅有空目录与 POM**（无源码、无依赖）。
3. 父 POM 已正确 import 两个 BOM：
   - `spring-cloud-dependencies:2025.0.0`
   - `spring-cloud-alibaba-dependencies:2025.0.0.0`
   - 已锁定 Java 21、Spring Boot 3.5.16。

### 2.3 设计决策

- **网关**：清理 D08 业务副本源码，转换为纯反应式 Spring Cloud Gateway。
- **Provider / Consumer**：基于空骨架从零搭建，遵循父 POM 已锁定的版本，不引入额外版本。
- 限流选型：采用 Spring Cloud Gateway 官方的 **RequestRateLimiter（Redis + Lua 令牌桶）**，Redis 已在本地运行，可实际触发。

---

## 第三章 实现内容与文件清单

### 3.1 D09_GATEWAY（反应式网关）

**清理动作**：删除 D08 业务副本源码（`src/main/java/com/zl/d09` 下全部业务代码）与旧资源文件。

**POM 关键依赖**：

- `spring-cloud-starter-gateway-server-webflux`（Spring Cloud 2025.0.0 起正确的 starter，旧 `spring-cloud-starter-gateway` 已弃用）
- `spring-cloud-starter-alibaba-nacos-discovery`
- `spring-cloud-starter-loadbalancer`
- `spring-boot-starter-data-redis-reactive`（限流所需）
- `lombok`

**新增文件**：

| 文件 | 职责 |
| --- | --- |
| `src/main/java/com/zl/d09/GatewayApp.java` | 网关启动类 |
| `src/main/java/com/zl/d09/config/RateLimiterConfig.java` | 定义 `ipKeyResolver`，按客户端 IP 限流 |
| `src/main/java/com/zl/d09/filter/TraceIdGlobalFilter.java` | 反应式全局过滤器，透传/生成 `X-Trace-Id` |
| `src/main/resources/application.yml` | 端口、Nacos、路由、限流、Redis 配置 |

**保留的既有脚本**（按「脚本已存在不再生成」要求保留）：`bin/start.bat`、`stop.bat`、`start.sh`、`stop.sh`；`script/jvm.options`、`stop.ps1`。

### 3.2 D09_SERVICE_PROVIDER（服务提供者）

**POM 依赖**：`spring-boot-starter-web`、`spring-cloud-starter-alibaba-nacos-discovery`、`lombok`。

**新增文件**：

| 文件 | 职责 |
| --- | --- |
| `src/main/java/com/zl/d09/provider/ProviderApp.java` | 启动类 |
| `src/main/java/com/zl/d09/provider/trace/TraceIdFilter.java` | 读取 `X-Trace-Id` 写入 MDC，结束后清理 |
| `src/main/java/com/zl/d09/provider/controller/ProviderController.java` | 提供 `/provider/echo`、`/provider/slow`、`/provider/error` |
| `src/main/resources/application.yml` | 端口 8081、服务名、Nacos |
| `src/main/resources/logback-spring.xml` | 日志格式含 `traceId` |

接口说明：

- `GET /provider/echo?name=xxx`：正常快速回显。
- `GET /provider/slow?delay=5000`：休眠指定毫秒，用于触发超时与熔断。
- `GET /provider/error`：直接抛运行时异常，用于触发错误降级。

### 3.3 D09_SERVICE_CONSUMER（服务消费者）

**POM 依赖**：`spring-boot-starter-web`、`spring-cloud-starter-alibaba-nacos-discovery`、`spring-cloud-starter-loadbalancer`、`spring-cloud-starter-openfeign`、`spring-cloud-starter-circuitbreaker-resilience4j`、`lombok`。

**新增文件**：

| 文件 | 职责 |
| --- | --- |
| `src/main/java/com/zl/d09/consumer/ConsumerApp.java` | 启动类，`@EnableFeignClients` |
| `src/main/java/com/zl/d09/consumer/trace/TraceIdFilter.java` | 请求入口建立 traceId 写入 MDC |
| `src/main/java/com/zl/d09/consumer/config/FeignConfig.java` | Feign `RequestInterceptor`，从 MDC 读 traceId 写入出站请求头 |
| `src/main/java/com/zl/d09/consumer/config/MdcAwareExecutorService.java` | MDC 感知线程池包装器，跨线程传递 MDC |
| `src/main/java/com/zl/d09/consumer/config/CircuitBreakerMdcConfig.java` | 熔断器工厂定制：MDC 线程池 + 默认超时/熔断参数 |
| `src/main/java/com/zl/d09/consumer/feign/ProviderFeignClient.java` | `@FeignClient(name="d09-provider")`，带 `fallbackFactory` |
| `src/main/java/com/zl/d09/consumer/feign/fallback/ProviderFeignFallbackFactory.java` | 降级工厂，可获取失败原因 |
| `src/main/java/com/zl/d09/consumer/controller/ConsumerController.java` | 对外 `/consumer/echo`、`/consumer/slow`、`/consumer/error` |
| `src/main/resources/application.yml` | 端口 8082、Nacos、Feign 超时配置 |
| `src/main/resources/logback-spring.xml` | 日志格式含 `traceId` |

---

## 第四章 构建

### 4.1 构建命令

在 `D09` 聚合根执行：

```bash
# 仅构建三个目标模块（空模块 d09_feign_handwritten_demo 无主类，会导致 repackage 失败，故用 -pl 排除）
mvn clean package -pl D09_GATEWAY,D09_SERVICE_PROVIDER,D09_SERVICE_CONSUMER -DskipTests
```

### 4.2 构建结果

- **结果：成功（exit code 0）**，三个模块均生成可执行 JAR：
  - `D09_GATEWAY/target/D09_GATEWAY-1.0-SNAPSHOT.jar`
  - `D09_SERVICE_PROVIDER/target/D09_SERVICE_PROVIDER-1.0-SNAPSHOT.jar`
  - `D09_SERVICE_CONSUMER/target/D09_SERVICE_CONSUMER-1.0-SNAPSHOT.jar`
- **已知现象**：对整个 `D09` 直接 `package` 会因空模块 `d09_feign_handwritten_demo` 无主类报
  `Unable to find main class`（spring-boot-maven-plugin:repackage）。该模块无源码，属正常现象，使用 `-pl` 构建目标模块即可。

---

## 第五章 启动与全链路验证（真实执行）

> 启动顺序：Provider → Consumer → Gateway。本地 Nacos、Redis 已运行。

### 5.1 服务注册（Nacos）

各服务启动日志均出现注册成功：

```
nacos registry, DEFAULT_GROUP d09-provider 192.168.6.1:8081 register finished
nacos registry, DEFAULT_GROUP d09-consumer 192.168.6.1:8082 register finished
nacos registry, DEFAULT_GROUP d09-gateway 192.168.6.1:9000 register finished
```

通过 Nacos API 实际查询：

```
GET http://127.0.0.1:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=50
=> {"count":3,"doms":["d09-consumer","d09-provider","d09-gateway"]}
```

**结论：3 个服务均注册成功。**

### 5.2 A. 网关路由转发与限流

**（1）正常转发**：

```
GET http://127.0.0.1:9000/provider/echo?name=viagw
=> 200
{"thread":"http-nio-8081-exec-9","name":"viagw","service":"d09-provider","timestamp":...}
```

**（2）限流**（配置：`replenishRate=1`、`burstCapacity=2`、`requestedTokens=1`）：

清空旧限流键后，对网关并发 10 个请求，状态码统计：

```
STATUS 200 : 2
STATUS 429 : 8
```

**结论：前 2 个请求（桶容量）成功，其余 8 个返回 429，限流可实际触发。**

### 5.3 B. Feign 按服务名调用与 traceId 透传

**（1）正常调用**：

```
GET http://127.0.0.1:8082/consumer/echo?name=tracecheck5
=> 200，X-Trace-Id: 4edf6c30de3b45c8a5b10cac26533e94
BODY={"thread":"http-nio-8081-exec-1","name":"tracecheck5","service":"d09-provider",...}
```

**（2）Provider 日志核对**：

```
INFO [http-nio-8081-exec-1] [traceId=4edf6c30de3b45c8a5b10cac26533e94] ProviderController
     - Provider 收到 echo 请求, name=tracecheck5
```

**结论：Consumer 与 Provider 日志的 traceId 完全一致（4edf6c30...），链路追踪成立。**

### 5.4 C. 超时降级、错误降级与熔断

**（1）受控慢接口（读取超时）**：

```
GET http://127.0.0.1:8082/consumer/slow?delay=5000
=> 200（降级体），ELAPSED ≈ 2477ms
{"reason":"RetryableException","detail":"Read timed out executing GET http://d09-provider/provider/slow?delay=5000",
 "fallback":true,"method":"slow"}
```

**（2）受控错误接口**：

```
GET http://127.0.0.1:8082/consumer/error
=> 200（降级体）
{"reason":"InternalServerError","detail":"[500] ... [ProviderFeignClient#error()] ...","fallback":true}
```

**（3）熔断器打开、快速失败**：连续 6 次 `error` 调用后，第 7 次：

```
call#7 ELAPSED = 5ms
{"reason":"CallNotPermittedException",
 "detail":"CircuitBreaker 'ProviderFeignClienterror' is OPEN and does not permit further calls",
 "fallback":true}
```

**结论：**

- 慢接口在约 2s（Feign 读超时）触发降级，而非 1s（旧默认 TimeLimiter）。
- 错误接口返回错误降级。
- 连续失败后熔断器打开，第 7 次在 5ms 内快速失败，不再打到下游。

---

## 第六章 迭代过程中的关键问题与修复

### 6.1 问题一：限流未生效

- **现象**：初版网关并发请求全部返回 200，Redis 中无 `request_rate_limiter*` 键。
- **根因**：Spring Cloud 2025.0.0 已将网关配置迁移到 `spring.cloud.gateway.server.webflux.*`，旧的 `default-filters` 键未被映射，导致限流过滤器未加载。
- **修复**：
  1. 依赖改为 `spring-cloud-starter-gateway-server-webflux`；
  2. 配置统一收敛到 `spring.cloud.gateway.server.webflux` 下。
- **结果**：并发 10 个请求 → 2 成功、8 个 429。

### 6.2 问题二：Feign 调用 traceId 不一致

- **现象**：Provider 日志的 traceId 是新生成的，与 Consumer 不一致。
- **根因**：开启 Feign 熔断后，Feign 调用（含拦截器）被提交到熔断器的**独立线程池**执行，MDC（ThreadLocal）不传递，拦截器读不到 traceId 而重新生成。
- **修复**：用 `MdcAwareExecutorService` 包装线程池，在提交时快照 MDC、工作线程执行前恢复、执行后清理。
- **结果**：两端 traceId 一致。

### 6.3 问题三：超时由 1s 的 TimeLimiter 先触发

- **现象**：慢接口在约 1.1s 触发 `TimeLimiter ... recorded a timeout exception`，而非 Feign 读超时（2s）。
- **根因**：Feign 为**每个方法**生成独立熔断器（id 形如 `ProviderFeignClientslowlong`），仅配置名为 `d09-provider` 的实例不生效，回落到默认 TimeLimiter（1s）。
- **修复**：通过 `factory.configureDefault(...)` 统一设置默认 TimeLimiter（3s）与 CircuitBreaker 参数。
- **结果**：慢接口在约 2s 由 Feign 读超时先触发，符合「Feign 超时 < TimeLimiter」的分层。

---

## 第七章 本次迭代总结

### 7.1 已完成

- 网关由 D08 业务副本正确转换为反应式 Spring Cloud Gateway，路由转发与 Redis 限流可实际触发。
- Provider 提供正常 / 慢 / 错误三类接口并注册到 Nacos。
- Consumer 通过 OpenFeign 按服务名调用，traceId 透传一致，超时/错误/熔断降级均验证通过。
- 代码与本全量文档、技术实操指南（D09-02）同步交付。

### 7.2 仍存在的限制

- 单实例验证，未做多实例负载均衡的实际流量验证。
- 未接入网关鉴权、配置中心、链路追踪后端（如 SkyWalking）。
- 学习环境的线程池/限流参数为「便于演示」设置，**不可直接用于生产**。

### 7.3 后续建议（可选）

- 多实例启动 Provider，验证负载均衡与 Nacos 实例上下线。
- 接入 Nacos 配置中心，实现配置动态刷新。
- 接入网关层鉴权与统一异常响应。
- 引入 Micrometer Tracing + 后端（Tempo/Zipkin），实现全链路埋点。

---

## 第八章 参考资料

- 《Spring Cloud Gateway Reference | Spring Cloud Gateway 参考文档》：https://docs.spring.io/spring-cloud-gateway/reference/
- 《Spring Cloud OpenFeign Reference | Spring Cloud OpenFeign 参考文档》：https://docs.spring.io/spring-cloud-openfeign/reference/
- 《Spring Cloud Alibaba Reference | Spring Cloud Alibaba 参考文档》：https://sca.aliyun.com/docs/2025.x/overview/what-is-sca/
- 《Resilience4j Documentation | Resilience4j 官方文档》：https://resilience4j.readme.io/docs
- 《Nacos Documentation | Nacos 官方文档》：https://nacos.io/docs/latest/overview/
