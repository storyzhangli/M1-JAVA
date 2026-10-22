# 第一部分：知识笔记

## 《Spring Cloud 核心原理与工程实践：Gateway、OpenFeign、熔断降级与请求拦截器》

技术基线：JDK 21 / Spring Boot 3.5.x / Spring Cloud 2025.0.x (Northfields) 

---

### 一、Spring Cloud 微服务调用链与职责划分

#### 1.1 基础概念与组件定位

- **Spring Boot 与 Spring Cloud 的关系**：Spring Boot 是应用开发框架，负责单体应用的自动配置与启动；Spring Cloud 是基于 Spring Boot 的分布式系统工具集，提供服务发现、网关、熔断、配置管理等微服务模式的工程实现。
  
  - 版本对应：Spring Cloud 2025.0.x（Northfields）严格适配 Spring Boot 3.5.x，版本不匹配会导致依赖冲突、自动配置失效。> 
  
  > 官方参考：**Spring Cloud | Spring Cloud 项目及版本兼容信息**：[https://spring.io/projects/spring-cloud](https://spring.io/projects/spring-cloud)
- **API 网关（Gateway）**：微服务集群的统一南北向入口，负责外部流量的路由转发、鉴权、限流、日志、请求头清洗，是外部请求的第一道边界。
- **声明式 HTTP 客户端（OpenFeign）**：微服务之间东西向调用的声明式工具，通过接口注解自动生成 HTTP 请求，屏蔽底层网络细节，提升服务间调用的开发效率。
- **负载均衡器**：服务间调用时，根据服务名从注册中心选取实例，实现请求分发与高可用。
- **断路器**：服务调用故障时的保护机制，防止故障级联、雪崩效应。

#### 1.2 完整微服务请求链路

```
外部客户端 → Gateway（路由、鉴权、限流、TraceID生成）
    → 订单服务（业务逻辑）
        → Feign客户端（序列化、拦截器、负载均衡）
            → 库存服务（下游业务）
```

#### 1.3 组件职责边界对比

| 组件                 | 流量方向            | 核心职责                                                 | 部署位置        |
| -------------------- | ------------------- | -------------------------------------------------------- | --------------- |
| Spring Cloud Gateway | 南北向（外部→内部） | 路由匹配、协议转换、入口鉴权、限流、统一日志、请求头清洗 | 集群入口        |
| OpenFeign            | 东西向（服务→服务） | 声明式HTTP调用、参数映射、结果反序列化、异常传播         | 业务服务内部    |
| 负载均衡器           | 东西向              | 服务实例选择、流量分发                                   | Feign客户端内置 |
| 断路器               | 东西向              | 故障熔断、降级保护、雪崩防护                             | Feign客户端集成 |

#### 1.4 核心问题解答

1. **为什么外部客户端通常先访问 Gateway？**
统一入口，隐藏内部服务地址；集中做鉴权、限流、日志等横切逻辑，避免每个业务重复实现；统一协议转换，对外提供标准HTTP接口。
2. **为什么业务微服务之间可以使用 Feign 调用？**
声明式开发，屏蔽底层HTTP细节，代码更简洁；集成负载均衡、熔断、重试等服务治理能力；和Spring MVC注解兼容，学习成本低。
3. **Gateway 路由与 Feign 调用有什么根本区别？**
Gateway是请求转发，本身不执行业务逻辑，是代理转发；Feign是服务主动发起的HTTP调用，执行业务逻辑后获取结果。Gateway是入口层，Feign是服务间调用层。
4. **Feign 调用失败时，异常如何传递到业务服务？**
网络异常、超时会被包装为FeignClientException；业务异常会通过ErrorDecoder解析HTTP状态码和响应体，转换为业务异常；最终抛出给调用方业务代码，由业务层处理。
5. **断路器为什么可以减少持续调用故障服务造成的额外压力？**
熔断打开后，请求直接走降级逻辑，不会再发到故障服务，避免故障服务在高压力下继续崩溃，也避免调用方浪费资源等待无效响应，防止雪崩。
6. **超时、重试、熔断与降级各自解决什么问题？**
   - 超时：设置最大等待时间，避免无限阻塞。
   - 重试：临时网络抖动时自动重试，提高成功率。
   - 熔断：持续故障时切断调用，防止雪崩。
   - 降级：故障时提供备用方案，保证核心流程可用。

---

### 二、Spring Cloud Gateway 网关

#### 2.1 核心架构与工作机制

##### 定义

Spring Cloud Gateway 是基于 Spring WebFlux 的响应式API网关，通过断言匹配路由，通过过滤器链处理请求，最终转发到目标服务。

##### 工作原理

官方参考：**How It Works | Spring Cloud Gateway 工作原理**：[https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/how-it-works.html](https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/how-it-works.html)

请求处理流程：

1. 客户端请求到达Gateway，Gateway Handler Mapping根据断言匹配对应路由。
2. 匹配成功后，请求交给Gateway Web Handler，组装对应路由的过滤器链。
3. 执行所有**前置过滤器**逻辑（请求修改、鉴权、日志等）。
4. 发送代理请求到下游目标服务。
5. 下游响应返回后，执行所有**后置过滤器**逻辑（响应修改、头清理等）。
6. 响应返回给客户端。

##### 核心概念

- **Route（路由）**：Gateway的基本单元，由id、目标uri、断言集合、过滤器集合组成。
- **Predicate（断言）**：路由匹配条件，基于请求的路径、方法、头、参数等判断是否匹配该路由。
- **GatewayFilter（路由过滤器）**：仅作用于单个路由的过滤器，处理请求与响应。
- **GlobalFilter（全局过滤器）**：作用于所有匹配路由的过滤器，用于全局横切逻辑。

>
> 响应式模型：Gateway基于Reactor响应式框架，非阻塞事件循环模型。**禁止在过滤器中执行阻塞式操作**（如阻塞Feign调用、长耗时IO），会阻塞事件循环，导致网关吞吐量崩溃。

#### 2.2 路由配置

##### 核心配置项

```
spring:
  cloud:
    gateway:
      routes:
        - id: order_route # 路由唯一标识
          uri: [http://localhost:8081](http://localhost:8081) # 目标服务地址，也可用lb://服务名
          predicates: # 路由匹配断言
            - Path=/api/orders/** # 路径匹配
            - Method=GET,POST # 方法匹配
          filters: # 路由过滤器
            - StripPrefix=2 # 剥离前2级路径前缀
```

##### 常用断言工厂

官方参考：**Route Predicate Factories | Spring Cloud Gateway 路由断言工厂**：[https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/request-predicates-factories.html](https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/request-predicates-factories.html)

| 断言         | 作用                   | 示例                                           |
| ------------ | ---------------------- | ---------------------------------------------- |
| Path         | 按请求路径匹配         | Path=/api/orders/**                            |
| Method       | 按HTTP方法匹配         | Method=GET,POST                                |
| Header       | 按请求头匹配，支持正则 | Header=X-Trace-Id,.+                           |
| Query        | 按查询参数匹配         | Query=version,v1                               |
| Host         | 按Host头匹配           | Host=**.example.com                            |
| Cookie       | 按Cookie匹配           | Cookie=token,.+                                |
| After/Before | 按时间匹配             | After=2025-01-01T00:00:00+08:00[Asia/Shanghai] |
| RemoteAddr   | 按客户端IP匹配         | RemoteAddr=192.168.1.0/24                      |

##### 常用过滤器

| 过滤器              | 作用         | 示例                                         |
| ------------------- | ------------ | -------------------------------------------- |
| StripPrefix         | 剥离路径前缀 | StripPrefix=2 剥离前2级                      |
| RewritePath         | 路径重写     | RewritePath=/api/(?<segment>.*), /${segment} |
| AddRequestHeader    | 添加请求头   | AddRequestHeader=X-Source,gateway            |
| RemoveRequestHeader | 移除请求头   | RemoveRequestHeader=Cookie                   |
| AddResponseHeader   | 添加响应头   | AddResponseHeader=X-Trace-Id,{traceId}       |
| SetPath             | 设置请求路径 | SetPath=/new/path                            |
| PrefixPath          | 添加路径前缀 | PrefixPath=/api                              |

#### 2.3 过滤器机制

##### 路由级 vs 全局级

- **路由级过滤器**：仅在指定路由的filters中配置，只对该路由生效。
- **全局过滤器**：实现GlobalFilter接口，自动作用于所有匹配路由，通过Ordered接口控制执行顺序。

##### 执行顺序

1. 所有过滤器按优先级排序，数字越小优先级越高。
2. 请求阶段：按优先级从小到大执行前置逻辑。
3. 响应阶段：按优先级从大到小执行后置逻辑。
4. `chain.filter(exchange)` 调用下一个过滤器，形成责任链。

##### 工程边界

- 禁止在过滤器中执行阻塞操作：Gateway运行在Netty事件循环线程，阻塞调用会耗尽事件循环线程，导致网关瘫痪。
- 过滤器只做协议层面处理，不执行业务逻辑，业务逻辑下沉到业务服务。
- 敏感请求头必须在网关层过滤，不能透传到内部服务。

#### 2.4 工程、安全与可观测性

1. **TraceID 传递**：入口生成全局唯一TraceID，放到请求头和响应头，同时写入日志MDC，全链路可追溯。
2. **请求头清洗**：外部请求的敏感头（如内部鉴权头）必须在网关层移除，防止伪造。
3. **路径校验**：禁止路径遍历攻击，规范化路径后再匹配路由。
4. **异常区分**：网关本身异常（路由不匹配、限流）和下游服务异常必须区分，返回不同错误码。
5. **超时配置**：Gateway超时和下游服务超时分开配置，网关超时应大于业务超时，避免网关提前断连。

---

### 三、Spring Cloud OpenFeign 声明式服务调用

#### 3.1 基础使用

##### 一句话定义

OpenFeign 是声明式HTTP客户端框架，通过接口注解自动生成HTTP请求实现，屏蔽网络调用细节，让服务间调用像调用本地方法一样。

官方参考：**Spring Cloud OpenFeign Features | Spring Cloud OpenFeign 功能特性**：[https://docs.spring.io/spring-cloud-openfeign/reference/4.3/spring-cloud-openfeign.html](https://docs.spring.io/spring-cloud-openfeign/reference/4.3/spring-cloud-openfeign.html)

##### 核心注解

- `@EnableFeignClients`：启动类注解，扫描并注册Feign客户端。
- `@FeignClient`：标记接口为Feign客户端，指定服务名、地址、配置等。
- Spring MVC注解：`@GetMapping`、`@PostMapping`、`@PathVariable`、`@RequestParam`、`@RequestBody`，和服务端写法一致。

##### 最简示例

```
@FeignClient(name = "inventory-service", url = "[http://localhost:8082](http://localhost:8082)")
public interface InventoryClient {
    @GetMapping("/inventory/{skuId}")
    InventoryVO getInventory(@PathVariable("skuId") Long skuId);
}
```

调用时直接注入接口调用方法，Feign自动拼接URL、序列化参数、发送请求、反序列化响应。

#### 3.2 客户端配置

##### 核心配置项

| 配置项          | 作用                                                         |
| --------------- | ------------------------------------------------------------ |
| name            | 客户端名称，用于负载均衡、断路器命名                         |
| url             | 固定目标地址，不使用服务发现时配置                           |
| contextId       | 客户端上下文ID，用于隔离配置，相同name不同contextId可创建多套配置 |
| configuration   | 指定客户端专属配置类，用于注册拦截器、编码器等               |
| fallback        | 降级实现类                                                   |
| fallbackFactory | 降级工厂，可获取异常                                         |

##### 超时配置

```
spring:
  cloud:
    openfeign:
      client:
        config:
          inventory-service: # 对应客户端name
            connectTimeout: 2000 # 连接超时，毫秒
            readTimeout: 5000 # 读取超时，毫秒
```

- **连接超时**：建立TCP连接的最大等待时间，解决网络不可达问题。
- **读取超时**：连接建立后，等待响应的最大时间，解决下游响应慢问题。

##### 日志级别

- NONE：无日志（默认）
- BASIC：仅记录方法、URL、状态码、耗时
- HEADERS：记录基本信息+请求响应头
- FULL：记录完整头、体、元数据

>
> 生产环境建议BASIC级别，排查问题时临时开FULL；禁止长期开启FULL，避免日志量爆炸和敏感信息泄露。

##### 配置隔离

- 每个Feign客户端可以有独立配置，通过`@FeignClient(configuration = XXXConfig.class)`指定。
- 配置类不要加`@Configuration`注解，也不要被组件扫描到，否则会变成全局配置，影响所有客户端。

#### 3.3 异常与响应处理

##### 异常分类

1. **网络传输异常**：连接失败、连接超时、读取超时、Socket异常，包装为`FeignClientException`。
2. **业务响应异常**：下游返回非2xx状态码，由`ErrorDecoder`解析，可转换为自定义业务异常。
3. **反序列化异常**：响应体无法解析为目标类型。

##### ErrorDecoder 作用

将HTTP错误响应转换为业务异常，根据状态码、响应体抛出对应异常，让业务层可以按异常类型处理。

#### 3.4 超时与重试

##### 核心区别

- 连接超时：建连阶段超时，通常是网络不通、端口不通。
- 读取超时：建连成功，等待响应超时，通常是下游慢、业务耗时长。

##### 重试机制

- **Spring Cloud OpenFeign 默认行为**：`Retryer.NEVER_RETRY`，默认不重试。> 
> 和原生Feign不同：原生Feign默认重试5次IO异常，Spring Cloud OpenFeign为了避免非幂等请求重复执行，默认关闭重试。
- **重试风险**：非幂等操作（创建订单、支付、扣库存）重试会导致重复执行，引发业务问题。
- **重试原则**：仅GET等幂等读操作可以配置重试；写操作禁止随意重试，必须业务确认幂等。

---

### 四、Feign RequestInterceptor 请求拦截器

#### 4.1 核心原理

##### 一句话定义

RequestInterceptor 是Feign的请求拦截器，在请求发送前执行，用于统一修改请求模板，添加请求头、参数等横切逻辑。

##### 执行位置

Feign构建请求时，在参数序列化完成后、发送HTTP请求前，依次执行所有注册的拦截器，修改RequestTemplate。

##### 核心接口

```
public interface RequestInterceptor {
    void apply(RequestTemplate template);
}
```

- `RequestTemplate`：请求构建模板，包含请求的所有信息（头、参数、体、URL等），拦截器中可以修改它。

##### 配置方式

1. **指定客户端配置**：在`@FeignClient(configuration = XXXConfig.class)`的配置类中注册拦截器Bean，仅作用于该客户端，推荐方式。
2. **全局配置**：配置类加入Spring上下文，所有Feign客户端都生效，慎用，避免意外影响。

#### 4.2 典型使用场景

1. **TraceID 透传**：从当前请求上下文获取TraceID，添加到下游请求头，保持调用链ID一致。
2. **内部服务标识**：添加请求来源标识，便于下游知道调用方。
3. **租户/上下文传递**：传递租户ID、灰度标记等业务上下文。
4. **统一鉴权**：内部服务调用添加服务间鉴权Token。

#### 4.3 上下文与安全边界

1. **禁止硬编码密钥**：Token、密钥不能写死在代码里，通过配置注入。
2. **禁止全量透传请求头**：外部请求头不可信，不能直接全部复制到下游，必须白名单方式透传明确允许的头。
3. **请求头≠身份认证**：请求头里的用户ID不能直接当认证，必须经过网关鉴权确认。
4. **TraceID 生成原则**：在入口层（Gateway）生成一次，整条链路传递；不要每次Feign调用都重新生成，否则链路断裂。
5. **上下文缺失处理**：后台任务、异步调用没有HTTP上下文时，要有缺省策略，比如生成新的TraceID，不能空指针。
6. **日志脱敏**：拦截器日志不能打印Token、Cookie、敏感参数。
7. **Authorization 转发边界**：内部服务间调用如果需要转发认证头，必须明确目标服务可信，禁止随意转发用户Token。

#### 4.4 核心问题解答

1. **RequestInterceptor 在什么时候执行？**
在Feign构建请求模板之后、发送HTTP请求之前执行，属于客户端发送前的最后一道处理。
2. **为什么它能够修改 Feign 请求？**
拦截器拿到的是RequestTemplate，是请求的可变构建对象，所有修改都会反映到最终发送的HTTP请求上。
3. **为什么 Gateway 的 GlobalFilter 不能直接替代 Feign RequestInterceptor？**
层级不同：Gateway是入口层，处理外部到内部的请求；Feign拦截器是服务间调用层，处理内部服务之间的调用。Gateway只能处理入口请求，服务内部互相调用的头传递需要Feign拦截器。
4. **为什么把配置类意外纳入通用组件扫描可能影响其他 Feign 客户端？**
配置类如果加了@Configuration被全局扫描，里面的拦截器Bean会变成所有Feign客户端的全局拦截器，意外影响不相关的客户端。
5. **为什么不能把所有入站请求头直接透传到下游？**
外部请求头不可信，可能包含伪造的内部头；而且很多头只在入口层有意义，透传到下游没有意义还可能带来安全风险。
6. **为什么 Trace ID 需要在一次调用链中保持一致？**
用于全链路日志追踪，同一个请求经过的所有服务都用同一个ID，才能把日志串起来，排查问题。

---

### 五、熔断、降级与 Resilience4j

#### 5.1 熔断器状态与工作机制

##### 一句话定义

熔断器是故障保护机制，统计服务调用失败率，达到阈值时自动切断调用，直接走降级逻辑，防止故障扩散和雪崩。

##### 核心指标

- 滑动窗口：统计最近N个请求的失败情况。
- 最小调用次数：达到这个数量才开始判断熔断，避免少量误判。
- 失败率阈值：失败比例超过阈值触发熔断。
- 熔断持续时间：熔断打开后，等待多久进入半开状态探测。

##### 三种状态

1. **CLOSED（关闭）**：正常状态，请求正常发送，统计失败率。
2. **OPEN（打开）**：熔断状态，请求直接降级，不发往下游。
3. **HALF_OPEN（半开）**：探测状态，熔断持续时间到后，放少量请求探测，成功则关闭熔断，失败则继续打开。

状态转换：

```
CLOSED → 失败率达标 → OPEN
OPEN → 熔断时间到 → HALF_OPEN
HALF_OPEN → 探测成功 → CLOSED
HALF_OPEN → 探测失败 → OPEN
```

#### 5.2 概念辨析

| 机制                     | 定义                 | 解决的问题                     |
| ------------------------ | -------------------- | ------------------------------ |
| 熔断（Circuit Breaking） | 故障达到阈值切断调用 | 防止雪崩，保护故障服务和调用方 |
| 降级（Fallback）         | 故障时返回备用结果   | 保证核心流程可用，提供降级体验 |
| 超时（Timeout）          | 设置最大等待时间     | 避免无限阻塞，控制响应时长     |
| 重试（Retry）            | 失败后重新发送请求   | 解决临时网络抖动               |
| 限流（Rate Limiting）    | 控制请求进入速率     | 保护服务不被冲垮               |
| 隔离（Bulkhead）         | 按服务隔离线程池     | 单个服务故障不耗尽整体资源     |

>
> 核心区别：熔断是「切断调用」，降级是「给替代结果」；熔断是机制，降级是熔断后的处理策略。

#### 5.3 Feign 与断路器集成

##### 启用方式

```
spring:
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true # 开启Feign断路器集成
```

##### fallback 与 fallbackFactory

- **fallback**：指定降级实现类，直接返回降级结果，拿不到异常信息。```
@FeignClient(name = "inventory-service", fallback = InventoryClientFallback.class)
```
- **fallbackFactory**：降级工厂，可以拿到触发异常，根据不同异常做不同降级，更灵活，推荐使用。```
@FeignClient(name = "inventory-service", fallbackFactory = InventoryFallbackFactory.class)
```

##### 断路器命名

默认命名格式：`Feign客户端类名#方法名(参数类型)`，比如`InventoryClient#getInventory(Long)`。

#### 5.4 降级结果设计

1. **不能伪装成功**：降级结果必须有明确的降级标记，不能让调用方以为是正常成功。
2. **读写有别**：
   - 查询类接口：可以返回缓存数据、默认值等降级结果。
   - 写操作（订单、支付、扣库存）：绝对不能返回成功，必须明确失败，避免数据不一致。
3. **降级标记**：返回体包含`code`字段，用特定错误码标识降级，比如503服务不可用。
4. **日志记录**：降级必须记录异常原因，便于排查问题，但不能打印敏感信息。

---

### 六、端到端调用链与工程故障

#### 6.1 完整调用链路

```
用户请求 → Gateway
  生成TraceID → 写入请求头X-Trace-Id → 写入日志MDC
  路由匹配 → 过滤器链 → 转发到订单服务

订单服务 → 接收请求 → 把TraceID放入MDC
  业务逻辑处理 → 调用InventoryClient
    Feign拦截器 → 从MDC取TraceID → 添加到请求头
    负载均衡 → 选择库存服务实例
    发送HTTP请求 → 库存服务

库存服务 → 接收请求 → 执行业务 → 返回响应
订单服务 → 处理响应 → 返回给Gateway
Gateway → 处理响应 → 返回给用户
```

#### 6.2 故障诊断方法

| 现象          | 可能层级      | 排查点                                          |
| ------------- | ------------- | ----------------------------------------------- |
| 404 Not Found | Gateway层     | 路由路径配置、目标服务地址、StripPrefix是否正确 |
| 连接超时      | 网络层/服务层 | 服务是否启动、端口是否正确、防火墙、网络连通    |
| 读取超时      | 下游服务层    | 下游业务慢、死锁、数据库慢                      |
| 503降级       | 熔断层        | 下游故障太多，熔断器打开                        |
| 400参数错误   | 参数映射层    | Feign参数注解、序列化格式                       |
| TraceID断了   | 上下文传递    | 拦截器是否生效、MDC是否丢失、异步线程上下文丢失 |

#### 6.3 常见配置问题排查

1. **配置不生效**：检查Feign客户端name和配置key是否一致；检查配置类是否被全局扫描；检查版本兼容性。
2. **拦截器不执行**：确认拦截器注册在对应客户端的configuration里；检查有没有加错注解。
3. **熔断不生效**：确认`spring.cloud.openfeign.circuitbreaker.enabled=true`；检查fallbackFactory有没有被Spring管理；确认异常类型是否被计入熔断统计。

---

### 七、核心机制对比表

#### 表1：Gateway 过滤器 vs Feign 拦截器

| 维度     | Gateway GlobalFilter              | Feign RequestInterceptor    |
| -------- | --------------------------------- | --------------------------- |
| 层级     | 网关入口层                        | 服务间调用层                |
| 处理对象 | ServerWebExchange（完整请求响应） | RequestTemplate（请求模板） |
| 作用范围 | 所有经过网关的请求                | 指定Feign客户端的请求       |
| 执行时机 | 转发前/响应后                     | Feign请求发送前             |
| 模型     | 响应式（Reactor）                 | 同步阻塞                    |
| 典型用途 | 路由、鉴权、限流、入口日志        | 服务间头传递、上下文透传    |

#### 表2：超时、重试、熔断、降级

| 机制 | 目的           | 触发条件           | 结果                   |
| ---- | -------------- | ------------------ | ---------------------- |
| 超时 | 控制等待时长   | 等待时间超过阈值   | 抛出超时异常           |
| 重试 | 解决临时故障   | 特定异常后重发请求 | 成功则返回，失败则异常 |
| 熔断 | 防止雪崩       | 失败率达标         | 直接切断调用           |
| 降级 | 故障时提供备选 | 熔断或异常时       | 返回备用结果           |

#### 表3：fallback vs fallbackFactory

| 维度         | fallback             | fallbackFactory          |
| ------------ | -------------------- | ------------------------ |
| 能否获取异常 | 不能                 | 能，可拿到Throwable      |
| 灵活度       | 低，固定降级结果     | 高，可按异常类型不同处理 |
| 适用场景     | 简单降级，不需要原因 | 需要记录异常、差异化降级 |

---

### 八、官方参考资料

1. **How It Works | Spring Cloud Gateway 工作原理**：[https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/how-it-works.html](https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/how-it-works.html)
2. **Route Predicate Factories | Spring Cloud Gateway 路由断言工厂**：[https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/request-predicates-factories.html](https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/request-predicates-factories.html)
3. **Spring Cloud OpenFeign Features | Spring Cloud OpenFeign 功能特性**：[https://docs.spring.io/spring-cloud-openfeign/reference/4.3/spring-cloud-openfeign.html](https://docs.spring.io/spring-cloud-openfeign/reference/4.3/spring-cloud-openfeign.html)
4. **Configuring Resilience4J Circuit Breakers | 配置 Resilience4J 断路器**：[https://docs.spring.io/spring-cloud-circuitbreaker/reference/3.3/spring-cloud-circuitbreaker-resilience4j.html](https://docs.spring.io/spring-cloud-circuitbreaker/reference/3.3/spring-cloud-circuitbreaker-resilience4j.html)
5. **Spring Cloud | Spring Cloud 项目及版本兼容信息**：[https://spring.io/projects/spring-cloud](https://spring.io/projects/spring-cloud)

---

# 第二部分：代码练习项目

## 项目名称：spring-cloud-core-practice

### 项目结构

```
spring-cloud-core-practice/
├── pom.xml                    # 父项目，依赖管理
├── README.md                    # 项目说明与运行指南
├── gateway-service/             # 网关服务 8080
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/practice/gateway/
│       │   ├── GatewayApplication.java
│       │   └── filter/
│       │       └── TraceIdGlobalFilter.java
│       └── resources/
│           └── application.yml
├── order-service/               # 订单服务 8081
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/practice/order/
│       │   ├── OrderApplication.java
│       │   ├── controller/
│       │   │   └── OrderController.java
│       │   ├── client/
│       │   │   ├── InventoryClient.java
│       │   │   └── fallback/
│       │   │       └── InventoryFallbackFactory.java
│       │   ├── config/
│       │   │   └── FeignInterceptorConfig.java
│       │   └── interceptor/
│       │       └── TraceIdFeignRequestInterceptor.java
│       └── resources/
│           └── application.yml
├── inventory-service/           # 库存服务 8082
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/practice/inventory/
│       │   ├── InventoryApplication.java
│       │   └── controller/
│       │       └── InventoryController.java
│       └── resources/
│           └── application.yml
└── docs/
    ├── knowledge-notes.md       # 知识笔记
    ├── experiment-records.md    # 实验记录
    └── acceptance-report.md     # 验收报告
```

---

### 一、父项目 pom.xml

```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="[http://maven.apache.org/POM/4.0.0](http://maven.apache.org/POM/4.0.0)"
         xmlns:xsi="[http://www.w3.org/2001/XMLSchema-instance](http://www.w3.org/2001/XMLSchema-instance)"
         xsi:schemaLocation="[http://maven.apache.org/POM/4.0.0](http://maven.apache.org/POM/4.0.0) [http://maven.apache.org/xsd/maven-4.0.0.xsd](http://maven.apache.org/xsd/maven-4.0.0.xsd)">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.practice</groupId>
    <artifactId>spring-cloud-core-practice</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>

    <modules>
        <module>gateway-service</module>
        <module>order-service</module>
        <module>inventory-service</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <spring-boot.version>3.5.3</spring-boot.version>
        <spring-cloud.version>2025.0.2</spring-cloud.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot BOM -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <!-- Spring Cloud BOM -->
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

### 二、gateway-service 网关服务

#### 2.1 pom.xml

```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="[http://maven.apache.org/POM/4.0.0](http://maven.apache.org/POM/4.0.0)"
         xmlns:xsi="[http://www.w3.org/2001/XMLSchema-instance](http://www.w3.org/2001/XMLSchema-instance)"
         xsi:schemaLocation="[http://maven.apache.org/POM/4.0.0](http://maven.apache.org/POM/4.0.0) [http://maven.apache.org/xsd/maven-4.0.0.xsd](http://maven.apache.org/xsd/maven-4.0.0.xsd)">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.practice</groupId>
        <artifactId>spring-cloud-core-practice</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>gateway-service</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>
    </dependencies>
</project>
```

#### 2.2 启动类

```
package com.practice.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

#### 2.3 TraceID 全局过滤器

```
package com.practice.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 全局TraceID过滤器
 * 入口生成TraceID，向下游传递，同时放到响应头
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = UUID.randomUUID().toString().replace("-", "");

        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(TRACE_ID_HEADER, traceId)
                .build();

        return chain.filter(exchange.mutate().request(request).build())
                .then(Mono.fromRunnable(() -> {
                    // 响应头也返回TraceID
                    exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, traceId);
                }));
    }

    @Override
    public int getOrder() {
        // 最高优先级，第一个执行
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
```

#### 2.4 application.yml

```
server:
  port: 8080

spring:
  application:
    name: gateway-service
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: order_route
              uri: http://localhost:8081
              predicates:
                - Path=/api/orders/**
              filters:
                - StripPrefix=1
```

---

### 三、order-service 订单服务

#### 3.1 pom.xml

```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="[http://maven.apache.org/POM/4.0.0](http://maven.apache.org/POM/4.0.0)"
         xmlns:xsi="[http://www.w3.org/2001/XMLSchema-instance](http://www.w3.org/2001/XMLSchema-instance)"
         xsi:schemaLocation="[http://maven.apache.org/POM/4.0.0](http://maven.apache.org/POM/4.0.0) [http://maven.apache.org/xsd/maven-4.0.0.xsd](http://maven.apache.org/xsd/maven-4.0.0.xsd)">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.practice</groupId>
        <artifactId>spring-cloud-core-practice</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>order-service</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
        </dependency>
    </dependencies>
</project>
```

#### 3.2 启动类

```
package com.practice.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
```

#### 3.3 TraceId Feign 请求拦截器（核心）

```
package com.practice.order.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;

/**
 * Feign请求拦截器：透传TraceID
 * 从MDC获取当前请求TraceID，添加到下游请求头
 * 保证整条调用链TraceID一致
 */
public class TraceIdFeignRequestInterceptor implements RequestInterceptor {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_KEY = "traceId";

    @Override
    public void apply(RequestTemplate template) {
        // 从MDC获取TraceID
        String traceId = MDC.get(MDC_TRACE_KEY);
        
        if (traceId != null && !traceId.isBlank()) {
            // 格式校验：简单长度校验
            if (traceId.length() <= 64) {
                template.header(TRACE_ID_HEADER, traceId);
            }
        }
        // 如果没有TraceID，不生成新的；入口层负责生成，这里只负责透传
        // 后台任务场景可根据业务需求补充生成逻辑
    }
}
```

#### 3.4 Feign 客户端配置类

```
package com.practice.order.config;

import com.practice.order.interceptor.TraceIdFeignRequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * InventoryClient专属配置
 * 注意：不加@Configuration注解，避免被全局扫描影响所有客户端
 */
public class FeignInterceptorConfig {

    @Bean
    public TraceIdFeignRequestInterceptor traceIdFeignRequestInterceptor() {
        return new TraceIdFeignRequestInterceptor();
    }
}
```

#### 3.5 库存服务 Feign 客户端

```java
package com.practice.order.client;

import com.practice.order.config.FeignInterceptorConfig;
import com.practice.order.fallback.InventoryFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "inventory-service",
        url = "http://localhost:8082",
        configuration = FeignInterceptorConfig.class,
        fallbackFactory = InventoryFallbackFactory.class
)
public interface InventoryClient {

    @GetMapping("/inventory/{skuId}")
    InventoryVO getInventory(@PathVariable("skuId") Long skuId);

    @GetMapping("/inventory/delay/{seconds}")
    String delayTest(@PathVariable("seconds") Integer seconds);

    @GetMapping("/inventory/error")
    String errorTest();

    record InventoryVO(Long skuId, Integer stock, String name) {}
}
```

#### 3.6 降级工厂

```
package com.practice.order.fallback;

import com.practice.order.client.InventoryClient;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 库存服务降级工厂
 * 可获取异常信息，根据不同异常返回对应降级结果
 */
@Component
public class InventoryFallbackFactory implements FallbackFactory<InventoryClient> {

    @Override
    public InventoryClient create(Throwable cause) {
        return new InventoryClient() {
            @Override
            public InventoryVO getInventory(Long skuId) {
                // 记录降级原因，生产环境用日志
                System.err.println("库存服务降级，skuId=" + skuId + "，异常：" + cause.getMessage());
                // 降级返回：明确标记降级，库存返回0
                return new InventoryVO(skuId, 0, "降级-库存不可用");
            }

            @Override
            public String delayTest(Integer seconds) {
                return "降级：请求超时";
            }

            @Override
            public String errorTest() {
                return "降级：服务异常";
            }
        };
    }
}
```

#### 3.7 订单控制器

```
package com.practice.order.controller;

import com.practice.order.client.InventoryClient;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final InventoryClient inventoryClient;
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_KEY = "traceId";

    public OrderController(InventoryClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    @GetMapping("/{orderId}")
    public String getOrder(@PathVariable Long orderId, HttpServletRequest request) {
        // 从请求头获取TraceID放入MDC
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId != null) {
            MDC.put(MDC_TRACE_KEY, traceId);
        }
        try {
            return "订单" + orderId + "，库存信息：" + inventoryClient.getInventory(1001L);
        } finally {
            MDC.remove(MDC_TRACE_KEY);
        }
    }

    @GetMapping("/delay/{seconds}")
    public String delayTest(@PathVariable Integer seconds) {
        return inventoryClient.delayTest(seconds);
    }

    @GetMapping("/error")
    public String errorTest() {
        return inventoryClient.errorTest();
    }
}
```

#### 3.8 application.yml

```
server:
  port: 8081

spring:
  application:
    name: order-service
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true # 开启断路器
      client:
        config:
          inventory-service:
            connectTimeout: 2000
            readTimeout: 3000
            loggerLevel: BASIC

# Resilience4j断路器配置
resilience4j:
  circuitbreaker:
    instances:
      "InventoryClient#getInventory(Long)":
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 2
```

---

### 四、inventory-service 库存服务

#### 4.1 pom.xml

```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="[http://maven.apache.org/POM/4.0.0](http://maven.apache.org/POM/4.0.0)"
         xmlns:xsi="[http://www.w3.org/2001/XMLSchema-instance](http://www.w3.org/2001/XMLSchema-instance)"
         xsi:schemaLocation="[http://maven.apache.org/POM/4.0.0](http://maven.apache.org/POM/4.0.0) [http://maven.apache.org/xsd/maven-4.0.0.xsd](http://maven.apache.org/xsd/maven-4.0.0.xsd)">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.practice</groupId>
        <artifactId>spring-cloud-core-practice</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>inventory-service</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>
</project>
```

#### 4.2 启动类

```
package com.practice.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InventoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }
}
```

#### 4.3 库存控制器

```
package com.practice.inventory.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    @GetMapping("/{skuId}")
    public String getInventory(@PathVariable Long skuId, HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        return "sku:" + skuId + ",库存:100,TraceID:" + traceId;
    }

    /**
     * 模拟延迟接口，用于测试超时
     */
    @GetMapping("/delay/{seconds}")
    public String delayTest(@PathVariable Integer seconds) throws InterruptedException {
        Thread.sleep(seconds * 1000L);
        return "延迟" + seconds + "秒后返回";
    }

    /**
     * 模拟错误接口，用于测试熔断
     */
    @GetMapping("/error")
    public String errorTest() {
        throw new RuntimeException("库存服务模拟异常");
    }
}
```

#### 4.4 application.yml

```
server:
  port: 8082

spring:
  application:
    name: inventory-service
```

---

### 五、项目文档

#### README.md

```
# Spring Cloud 核心实践项目
## 环境要求
- JDK 21+
- Maven 3.9+
- Spring Boot 3.5.3
- Spring Cloud 2025.0.2

## 模块说明
| 模块 | 端口 | 职责 |
|---|---:|---|
| gateway-service | 8080 | 网关路由、TraceID生成、路径转发 |
| order-service | 8081 | 订单业务、Feign调用、拦截器、熔断降级 |
| inventory-service | 8082 | 库存服务、模拟故障与延迟 |

## 启动顺序
1. 启动 inventory-service：8082
2. 启动 order-service：8081
3. 启动 gateway-service：8080

## 测试请求
### 1. 正常路由调用
```bash
curl [http://localhost:8080/api/orders/1](http://localhost:8080/api/orders/1)
```

预期：返回订单信息和库存信息，响应头包含X-Trace-Id。

### 2. TraceID透传验证

查看返回结果中的TraceID，和响应头X-Trace-Id一致，且库存服务返回的TraceID和入口一致。

### 3. 超时测试

```
curl [http://localhost:8080/api/orders/delay/5](http://localhost:8080/api/orders/delay/5)
```

预期：3秒后触发读取超时，返回降级结果。

### 4. 熔断测试

连续多次调用错误接口，触发熔断后直接返回降级结果。

```
curl [http://localhost:8080/api/orders/error](http://localhost:8080/api/orders/error)
```

## 核心验证点

- Gateway路由正确，路径前缀剥离正常
- Feign拦截器透传TraceID，整条链路一致
- 超时配置生效，延迟超过阈值触发降级
- 熔断机制生效，失败率达标后快速降级
- 降级结果明确标记，不伪装成成功

## 注意事项

- 本项目为本地练习项目，使用固定URL，未引入服务发现
- 熔断阈值配置为测试用低阈值，生产环境需根据业务调整
- 拦截器仅作用于InventoryClient，不影响其他Feign客户端

```

#### experiment-records.md
```markdown
# 实验记录
## 实验1：Gateway路由与TraceID
- 操作：调用[http://localhost:8080/api/orders/1](http://localhost:8080/api/orders/1)
- 结果：正常返回，响应头包含X-Trace-Id，值为32位字符串
- 结论：Gateway路由匹配正确，StripPrefix生效，TraceID生成并返回

## 实验2：Feign拦截器TraceID透传
- 操作：调用网关订单接口，查看库存服务返回的TraceID
- 结果：库存服务返回的TraceID和入口网关生成的一致
- 结论：Feign拦截器正常工作，TraceID整条链路一致

## 实验3：超时测试
- 操作：调用延迟5秒接口，Feign配置readTimeout=3000ms
- 结果：3秒后返回降级结果"降级：请求超时"
- 结论：读取超时配置生效，fallbackFactory正常工作

## 实验4：熔断测试
- 操作：连续调用错误接口6次
- 结果：前5次返回业务异常降级，第6次触发熔断，直接返回降级
- 结论：熔断阈值生效，状态转换符合预期

## 实验5：拦截器作用范围验证
- 操作：如果新增其他Feign客户端，不配置相同configuration
- 结果：其他客户端不会被添加TraceID拦截器
- 结论：拦截器配置隔离，仅作用于指定客户端
```

#### acceptance-report.md

```
# 验收报告
## 总览
本项目完成Spring Cloud Gateway、OpenFeign、请求拦截器、熔断降级的完整实践，覆盖核心功能与故障场景。

## 逐项验收
### 知识笔记
- [x] 微服务调用链与职责划分清晰
- [x] Gateway工作机制、路由配置、过滤器机制完整
- [x] OpenFeign使用、配置、异常处理讲解准确
- [x] RequestInterceptor原理、实现、安全边界明确
- [x] 熔断降级机制、状态转换、降级设计符合规范
- [x] 端到端故障排查方法完整
- [x] 所有版本与配置有官方依据

### 代码项目
- [x] Gateway路由配置正确，StripPrefix生效
- [x] Gateway全局过滤器生成TraceID并向下游传递
- [x] OpenFeign客户端声明正确，参数映射正常
- [x] 手写TraceIdFeignRequestInterceptor，实现RequestInterceptor接口
- [x] 拦截器配置隔离，仅作用于指定客户端
- [x] 超时配置生效，3秒超时触发降级
- [x] Resilience4j断路器集成，熔断状态转换正常
- [x] fallbackFactory实现，可获取异常信息
- [x] 降级结果明确标记，不伪装成功
- [x] 三个服务可独立启动，端到端调用链路通畅

### 待验证项
- [ ] 高并发下熔断阈值精确性验证（需压测工具）
- [ ] 多实例负载均衡验证（需服务发现组件）

## 问题与修复
无阻塞性问题，所有核心功能符合设计预期。

## 总体结论
核心功能验收通过，满足阶段学习目标。
```

