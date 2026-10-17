# Redis 核心知识：从缓存三大问题到生产级架构

Java + AI 工程化导师 · 阶段 1 · 中高级 Java 后端必备专题

这次不仅学习 Redis 数据结构和缓存三大问题，还要覆盖中高级 Java 工程师在面试、项目开发和生产排障中必须掌握的机制。

本节以你指定的 Redis 6.2.12 为学习基线，主要采用 Spring Boot 3.x、Spring Data Redis 与 Java 后端的应用场景。代码以 Redis 命令为核心，后续可以映射到 Java 实现。

## 一、先建立 Redis 的整体知识地图

数据结构

String、Hash、List、Set、ZSet、Stream、Bitmap 等。

缓存治理

穿透、击穿、雪崩、一致性、缓存淘汰。

高可用与分布式

主从复制、Sentinel、Cluster、分布式锁。

性能与稳定性

大 Key、热 Key、慢命令、内存、连接池、监控。

最终能力目标

不只会使用 Redis，而是能根据业务场景选数据结构、设计缓存策略、解释一致性边界、分析生产故障，并写出安全可靠的 Java 实现。

Redis 不只是一个简单的 Key-Value 缓存。它提供多种原生数据结构，并支持持久化、复制、脚本执行、事务及不同的高可用部署方式。不同结构的时间复杂度、内存开销和语义各不相同，因此必须结合场景选择。

[Redis data types | Redis 数据类型](https://redis.io/docs/latest/develop/data-types/?utm_source=chatgpt.com)

[Compare data types | 比较数据类型](https://redis.io/docs/latest/develop/data-types/compare-data-types/?utm_source=chatgpt.com)

[Redis persistence | Redis 持久化](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/?utm_source=chatgpt.com)

[Redis replication | Redis 复制](https://redis.io/docs/latest/operate/oss_and_stack/management/replication/?utm_source=chatgpt.com)



## 二、Redis 常见数据结构：选对结构比记住命令更重要

### 2.1 六种基本结构及常见扩展

| 数据结构   | 常用命令                          | 典型业务场景                     | 必须注意                           |
| ---------- | --------------------------------- | -------------------------------- | ---------------------------------- |
| String     | `GET`、`SET`、`INCR`、`SET NX EX` | 缓存对象、计数器、分布式锁标记   | 大对象、过期时间、并发更新         |
| Hash       | `HGET`、`HSET`、`HINCRBY`         | 用户部分字段、商品属性           | 大量 Field、字段更新与整体缓存语义 |
| List       | `LPUSH`、`RPOP`、`LRANGE`         | 简单队列、列表                   | 大范围读取、阻塞消费、多消费者语义 |
| Set        | `SADD`、`SISMEMBER`、`SINTER`     | 标签、去重、共同关注             | 集合大小、复杂集合运算成本         |
| Sorted Set | `ZADD`、`ZRANGE`、`ZRANK`         | 排行榜、按分数排序、延时任务索引 | 分数规则、时间复杂度、过期策略     |
| Stream     | `XADD`、`XREADGROUP`、`XACK`      | 事件流、消费组、消息处理         | 消息确认、待处理消息、消息保留策略 |

此外还应认识：

- Bitmap（位图）：适合签到、活跃标记等布尔状态集合。
- HyperLogLog：适合估算不重复元素数量，不能用于准确列出所有用户。
- Geospatial（地理空间结构）：适合经纬度附近位置查询等基础场景。
- Bitfield（位域）：在一个字符串中按位操作不同宽度的整数。

这些结构及其能力可以从 Redis 官方的数据类型说明中进一步查阅。

[Redis data types | Redis 数据类型](https://redis.io/docs/latest/develop/data-types/?utm_source=chatgpt.com)

[Compare data types | 数据类型比较](https://redis.io/docs/latest/develop/data-types/compare-data-types/?utm_source=chatgpt.com)



### 2.2 中高级开发者必须会判断时间复杂度

不要仅仅记“Redis 很快”。即使是内存操作，不同命令的成本也完全不同。

| 命令或操作              | 常见复杂度          | 工程风险                                       |
| ----------------------- | ------------------- | ---------------------------------------------- |
| `GET key`               | \\(O(1)\\)          | 单次操作虽快，但网络、序列化和连接排队仍有成本 |
| `HGET key field`        | 平均 \\(O(1)\\)     | 大 Hash 的总体内存与遍历成本仍需关注           |
| `SISMEMBER key member`  | 平均 \\(O(1)\\)     | 超大集合仍会占据内存                           |
| `ZRANGE key start stop` | \\(O(\log N + M)\\) | 返回大量成员时，输出量会影响成本               |
| `LRANGE key 0 -1`       | \\(O(N)\\)          | 大 List 全量读取可能造成延迟尖峰               |
| `KEYS pattern`          | \\(O(N)\\)          | 在生产大实例中可能阻塞其他请求                 |

这里的复杂度是典型命令模型；某些命令会随参数数量、返回元素数量、编码或结构大小而变化。Redis 官方文档明确提醒，复杂度较高的命令可能造成延迟尖峰，因为命令执行期间会占用服务端执行资源。

[Redis latency monitoring | Redis 延迟监控](https://redis.io/docs/latest/operate/oss_and_stack/management/optimization/latency-monitor/?utm_source=chatgpt.com)

[Compare data types | 数据类型比较](https://redis.io/docs/latest/develop/data-types/compare-data-types/?utm_source=chatgpt.com)



工程原则： 避免在生产热路径中无边界地执行 `KEYS`、大范围 `LRANGE`、巨大集合运算等操作。需要遍历 Key 时，优先研究 `SCAN` 的渐进式遍历方式；不过 `SCAN` 也不是严格快照，遍历时应正确处理重复结果及并发变更。

[SCAN命令|扫描](https://redis.io/docs/latest/commands/scan/?utm_source=chatgpt.com)





## 三、缓存三大问题：穿透、击穿、雪崩

这是 Redis 面试高频题，也是线上缓存系统最容易引发数据库故障的三个风险。

缓存穿透 Cache Penetration

请求的数据根本不存在，缓存和数据库都查不到；大量不同的无效请求持续绕过缓存，冲击数据库。

核心治理：参数校验、空值缓存、布隆过滤器、限流。

缓存击穿 Cache Breakdown

某个极热门的 Key 过期或失效，大量并发请求同时回源查询相同数据，造成数据库瞬时压力。

核心治理：互斥重建、逻辑过期、热点预热、请求合并。

缓存雪崩 Cache Avalanche

大量 Key 同时过期，或 Redis 集群整体故障，导致大量请求集中转向数据库，甚至引发级联故障。

核心治理：TTL 随机化、多级缓存、限流熔断、降级与高可用。

这三个问题的区别要非常明确：穿透是数据不存在，击穿是一个热点 Key 失效，雪崩是大量缓存同时失效或缓存层整体不可用。 实际事故可能同时出现多个问题，不应只靠一个技术手段处理。

### 3.1 缓存穿透：数据不存在怎么办？

例如：

```
// 伪代码：每个无效 ID 都绕过缓存查询数据库
Product product = redis.get("product:" + productId);

if (product == null) {
    product = database.findById(productId);
}
```

如果攻击者或异常调用方持续传入随机 ID，每次 Redis 都 Miss，即使数据库查询结果为空，下一次请求仍会重新访问数据库。

推荐按照下列层次防护：

1. 入口参数校验：过滤格式非法、明显越界的输入。
2. 缓存空结果：确认数据库记录不存在时，使用较短 TTL 缓存一个明确的空值标记。
3. 布隆过滤器 Bloom Filter：在访问数据库之前判断一个 ID 是否“肯定不存在”或“可能存在”。过滤器允许假阳性，不能把它当作准确查询结果。
4. 限流与异常检测：对高频随机 ID、恶意来源及异常访问速率做治理。

空值缓存示例：

```
SET product:999999 "__NULL__" EX 60
```

这里只是命令示例，实际系统应定义明确、不会与合法业务值冲突的编码协议。

还要防止两个常见问题：

- 空值缓存时间过长：新建数据可能在缓存到期前持续被判定不存在。
- 布隆过滤器不一致：如果新数据写入数据库后未及时更新过滤器，可能产生错误拦截。过滤器的初始化、更新与重建机制必须纳入设计。

### 3.2 缓存击穿：一个热点 Key 失效怎么办？

假设某个商品详情被大量用户同时访问。缓存一旦过期，所有请求可能同时访问数据库。

解决方法有几种，适用条件不同。

| 方案              | 工作方式                                                   | 适用场景与代价                                     |
| ----------------- | ---------------------------------------------------------- | -------------------------------------------------- |
| 互斥锁 / 请求合并 | 同一 Key 同一时刻只让一个请求重建缓存                      | 适合数据库回源昂贵的热点数据；其他请求可能等待     |
| 逻辑过期          | 缓存值携带业务过期时间，允许短时返回旧数据，由后台任务刷新 | 适合允许短暂陈旧的热点数据；需要刷新协调与失败兜底 |
| 提前刷新 / 预热   | 在预计过期前主动重建热点缓存                               | 适合热点可预测的活动、榜单和商品数据               |
| 多级缓存          | 本地缓存加 Redis，降低单点热点压力                         | 需要解决各应用实例之间的缓存失效同步问题           |

Redis 官方的 Cache-Aside 资料也指出，当热门 Key 到期时，大量进程同时访问数据源会形成缓存争用（cache stampede）。

[Redis cache-aside | Redis 旁置缓存](https://redis.io/docs/latest/develop/use-cases/cache-aside/?utm_source=chatgpt.com)





面试常见陷阱： 互斥锁只能协调遵循同一把锁协议的请求，不能自动解决所有请求洪峰。如果等待时间过长、加锁失败没有合理退避、锁持有者异常退出或下游数据库自身已过载，仍然会有可用性问题。

### 3.3 缓存雪崩：大规模失效如何治理？

主要分成两类故障。

第一类：大量 Key 集中到期。

例如所有商品缓存都设置成完全相同的 TTL，某次批量预热后大量 Key 在同一时刻失效。可以在业务 TTL 上加入一定随机抖动：

```
实际 TTL = 基础 TTL + 随机抖动
```

伪代码：

```
long ttlSeconds = baseTtlSeconds
        + ThreadLocalRandom.current()
                .nextLong(maxJitterSeconds + 1);
```

不要为所有 Key 写死完全相同的绝对到期时间。

第二类：Redis 整体不可用。

这时候 TTL 随机化并不能解决问题。应考虑：

- 在允许一定数据陈旧的业务中使用本地缓存作为有限兜底。
- 对访问数据库的流量实施限流、并发隔离和熔断。
- 为关键业务定义超时、降级及错误响应策略。
- 根据业务可接受的数据丢失和恢复目标设计 Redis 高可用。
- 对缓存重建任务实施并发控制，防止 Redis 恢复后所有业务瞬间回源。

缓存层失效时，不能只是简单地“直接查数据库”，否则缓存原本承担的削峰职责会消失，故障可能从 Redis 扩散至数据库和整个应用。

## 四、中高级工程师必须掌握：缓存一致性

三大缓存问题解决的是“如何减少错误和压力”，但缓存系统还有一个更难的问题：

MySQL 中的数据已经更新，Redis 中却仍然是旧值，怎么办？

### 4.1 Cache-Aside：旁路缓存模式

常见读取流程：

```
1. 先读 Redis
2. 命中：返回缓存
3. 未命中：查询 MySQL
4. 将查询结果写入 Redis，并设置 TTL
5. 返回结果
```

常见更新流程：

```
1. 更新 MySQL，并提交事务
2. 删除 Redis 缓存
3. 后续读取 Redis Miss，再从 MySQL 加载新值
```

这是 Java 后端常用的缓存模式，Redis 官方也提供了 Cache-Aside 使用指南。

[Redis cache-aside](https://redis.io/docs/latest/develop/use-cases/cache-aside/?utm_source=chatgpt.com)



示意 Java 代码：

```
@Transactional
public void updateProduct(Product product) {
    productMapper.updateById(product);
}

// 在事务成功提交后执行缓存失效。
// 这里省略事务事件监听器或事务同步器的具体封装。
redisTemplate.delete("product:" + product.getId());
```

需要注意，这段代码表达的是正确的业务顺序，不是完整事务实现：不能简单地在数据库事务尚未提交时就删除缓存，然后立刻认为一致性问题已经解决。 实际项目还需要考虑事务提交回调、删除失败重试及并发读写竞争。

### 4.2 为什么更新数据库后删除缓存仍不是强一致性？

考虑下面这个并发时间线：

1. 线程 A 查询 MySQL，读取到了旧版本数据。
2. 线程 B 更新 MySQL 并提交事务，随后删除 Redis。
3. 线程 A 将刚刚读取到的旧版本数据写回 Redis。
4. 后续请求命中旧缓存。

所以，数据库先更新、缓存后删除仍然存在并发竞争窗口。

根据一致性要求，可以进一步考虑：

- 缓存删除失败时使用重试队列或 Outbox 可靠投递失效事件。
- 使用短 TTL 限制旧数据可能存在的时间窗口。
- 在高一致性业务中引入版本号、缓存版本校验或更严格的协调机制。
- 对确实不能接受陈旧读取的操作，以数据库事务和数据库约束作为最终正确性边界。

重要结论： 普通 Cache-Aside 通常追求最终一致性，不等于数据库与缓存之间的强一致性。单纯“双删”也不是严格正确性的保证。

## 五、分布式锁：`SET NX PX` 还不够

分布式锁在订单处理、定时任务、热点数据重建等场景很常见。但仅仅会写 `SETNX`，距离企业生产级实现还有明显差距。

### 5.1 正确的基本加锁与释放方式

加锁可以使用原子命令：

```
SET lock:product:1001 random-token NX PX 10000
```

含义如下：

- `NX`：只有 Key 不存在时才设置成功。
- `PX 10000`：租约为 10 秒。
- `random-token`：当前持有者唯一的随机标识。

解锁不能直接执行：

```
DEL lock:product:1001
```

因为锁可能已过期并被其他线程重新获得；旧持有者随后删除 Key，会把新持有者的锁误删。

应该使用 Lua 脚本执行原子校验和删除：

```
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('DEL', KEYS[1])
else
    return 0
end
```

通过当前锁的唯一标识确认所有权，再删除锁。Redis Lua 脚本具备原子执行语义，适合将相关检查和修改组合成一个操作。

[Scripting with Lua | 使用 Lua 进行脚本编写](https://redis.io/docs/latest/develop/programmability/eval-intro/?utm_source=chatgpt.com)



### 5.2 生产级还必须考虑什么？

租约过期

业务执行时间可能超过锁的 TTL。其他客户端获得锁后，原持有者恢复运行，可能产生并发执行。

续租与进程暂停

看门狗机制可以尝试延长租约，但不能将所有网络分区、进程暂停和故障场景变成绝对安全。

Fencing Token（栅栏令牌）

对严格正确性要求的资源，可以引入单调递增的令牌，让资源提供方拒绝过期持有者发出的旧操作。只保护 Redis 锁本身而不保护真正的业务写入是不够的。

对于资金扣减、库存不超卖等关键业务，还应依靠数据库条件更新、唯一约束、幂等键或其他业务原子性手段保障正确性，不应把 Redis 分布式锁当成唯一安全边界。

## 六、Redis 持久化、主从复制与高可用

缓存只是 Redis 的使用方式之一。中高级开发者还必须理解 Redis 出故障后，数据是否会丢失、服务是否会恢复，以及读写是否始终一致。

### 6.1 RDB 与 AOF

| 方案      | 核心原理                       | 优点                               | 风险与代价                             |
| --------- | ------------------------------ | ---------------------------------- | -------------------------------------- |
| RDB       | 按配置生成数据集快照           | 文件紧凑、适合备份和恢复           | 两次快照之间的最新写入可能丢失         |
| AOF       | 记录写入命令日志并在恢复时重放 | 可以通过同步策略调整持久化强度     | 通常占用更多空间，重写与同步会带来成本 |
| RDB + AOF | 同时采用两种策略               | 兼顾快照恢复与较强的写入持久性诉求 | 需要管理磁盘、备份、重写及恢复流程     |
| 无持久化  | 不将数据持久化到磁盘           | 适合明确允许丢失数据的纯缓存场景   | 重启后无法通过持久化文件恢复缓存数据   |

Redis 官方指出，RDB 适用于紧凑备份和灾难恢复，AOF 可通过不同 `fsync` 策略调整可靠性与性能之间的取舍。两者都不能替代经过验证的备份和恢复演练。

[Redis persistence | Redis持久化](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/?utm_source=chatgpt.com)



需要记住：

- `appendfsync always`：通常提供更强的同步保障，但可能增加写入延迟。
- `appendfsync everysec`：常见折中策略，但在故障情况下仍可能丢失最近的写入。
- `appendfsync no`：由操作系统决定何时刷盘，数据丢失风险更依赖环境。

持久化策略必须根据数据重要性、可接受的数据丢失窗口、恢复目标和磁盘条件制定。

### 6.2 主从复制、Sentinel、Cluster 有什么区别？

### 主从复制 Replication

主节点把写入产生的变更复制给副本。Redis 使用异步复制，主节点已经确认的写入不一定在故障切换前复制到副本，因此可能存在已确认写入丢失的窗口。

[Redis replication | Redis 主从复制](https://redis.io/docs/latest/operate/oss_and_stack/management/replication/?utm_source=chatgpt.com)



### Sentinel（哨兵）

对非 Cluster 部署提供监控、故障检测、自动故障转移和主节点地址发现。它解决高可用管理问题，不负责水平分片。

[High availability with Redis Sentinel | 使用Redis Sentinel实现高可用性](https://redis.io/docs/latest/operate/oss_and_stack/management/sentinel/?utm_source=chatgpt.com)



### Redis Cluster（集群）

使用 16,384 个哈希槽把 Key 分布到多个主节点，从而支持数据分片、水平扩展和节点故障转移。

[Redis cluster specification | Redis集群规范](https://redis.io/docs/latest/operate/oss_and_stack/reference/cluster-spec/?utm_source=chatgpt.com)

[Scale with Redis Cluster | 使用Redis集群进行扩展](https://redis.io/docs/latest/operate/oss_and_stack/management/scaling/)



面试中不要把三个概念混淆：

- 主从复制：复制数据。
- Sentinel：管理非 Cluster 架构下的高可用和故障转移。
- Cluster：通过哈希槽分片，并提供集群级故障处理。

Cluster 对多 Key 操作还有一个重要限制：涉及多个 Key 的操作通常要求这些 Key 落在同一个哈希槽。可以利用哈希标签（Hash Tag）让相关 Key 按相同标签计算槽位，例如：

```
order:{1001}:detail
order:{1001}:items
```

花括号内的相同标签使两个 Key 能落到同一槽位；但大量业务都使用同一个标签也会造成槽位倾斜，需要合理设计 Key。

[Redis cluster specification | Redis 集群规范](https://redis.io/docs/latest/operate/oss_and_stack/reference/cluster-spec/?utm_source=chatgpt.com)



## 七、事务、Lua、Pipeline：Redis 操作的原子性与性能

这部分决定中高级开发者能否写出可靠的并发操作。

### 7.1 Redis 事务不等于 MySQL 事务

Redis 事务相关命令：

```
MULTI
SET account:1001:status "processing"
INCR account:1001:version
EXEC
```

`MULTI` 开始事务，之后的命令进入队列，`EXEC` 执行事务中的命令。执行期间，其他客户端的命令不会插入事务命令之间。`WATCH` 可以检测被观察 Key 在 `EXEC` 之前是否发生修改，并在条件不满足时取消本次事务。

[Transactions | Redis事务](https://redis.io/docs/latest/develop/using-commands/transactions/?utm_source=chatgpt.com)



但 Redis 事务与关系型数据库事务不同：

- 不提供与 MySQL 等价的完整回滚语义。
- `WATCH` 提供乐观并发控制，但应用需要处理事务取消与重试。
- 如果业务要求多个数据库和 Redis 操作构成原子整体，不能单靠 `MULTI/EXEC` 保证。

### 7.2 Lua 脚本：把检查和修改放到一次原子操作中

典型应用：库存扣减。

```
local stock = tonumber(redis.call('GET', KEYS[1]))

if not stock then
    return -1
end

if stock <= 0 then
    return 0
end

redis.call('DECR', KEYS[1])
return 1
```

约定返回值：

- `1`：扣减成功。
- `0`：库存不足。
- `-1`：库存 Key 不存在，需要按业务规则初始化或回源。

这个脚本将“检查库存”和“扣减库存”合并执行，避免两个客户端分别读取同一个库存值后重复通过检查。

不过，这仍然只是一个 Redis 库存示例。若业务把 MySQL 库存当作权威数据源，还需设计数据库扣减、缓存恢复、重复请求和失败补偿机制。不能仅凭 Redis 返回成功就认定整个订单交易已经成功。

Redis 官方说明，Lua 脚本执行期间具有原子性，但长时间运行的脚本会阻塞服务端处理其他命令；脚本应简短、边界明确。

[Scripting with Lua | 使用 Lua 进行脚本编写](https://redis.io/docs/latest/develop/programmability/eval-intro/?utm_source=chatgpt.com)



### 7.3 Pipeline：减少网络往返，不提供事务原子性

假设需要读取 100 个 Key。

逐个请求的方式会产生大量客户端与服务端往返；Pipeline 可以将多条命令批量发送，减少往返次数。

但要注意：

Pipeline 优化的是通信往返成本，不等于数据库事务，也不保证所有命令作为一个不可分割的操作执行。

批量太大又会增加客户端缓冲区、服务端输出缓冲区和单次请求的内存压力，因此需要控制批次大小，并根据真实延迟和吞吐量做基准测试。

## 八、内存淘汰、过期机制和大 Key

### 8.1 过期与淘汰不是一回事

- 过期（Expiration）：某个 Key 达到 TTL 后，在 Redis 的过期机制下被处理。
- 淘汰（Eviction）：当内存使用达到 `maxmemory` 限制时，根据内存淘汰策略删除适当的 Key。

Redis 提供 `maxmemory` 与多种淘汰策略，例如 LRU、LFU 及 `volatile-*`、`allkeys-*` 等策略家族。

[Key eviction | 键淘汰](https://redis.io/docs/latest/develop/reference/eviction/)



| 策略           | 大意                                         | 典型考虑                           |
| -------------- | -------------------------------------------- | ---------------------------------- |
| `noeviction`   | 不主动淘汰 Key，达到内存限制后部分写入会失败 | 不适合完全依赖自动淘汰的纯缓存     |
| `allkeys-lru`  | 从所有 Key 中淘汰较少使用的候选项            | 适合以近期访问热度为重要信号的缓存 |
| `allkeys-lfu`  | 从所有 Key 中淘汰使用频率较低的候选项        | 适合使用频率对缓存价值更重要的场景 |
| `volatile-lru` | 从设置 TTL 的 Key 中选择淘汰对象             | 需确保目标数据符合 TTL 约定        |
| `volatile-ttl` | 倾向淘汰剩余 TTL 较短的 Key                  | 不一定符合业务冷热价值             |

LRU 和 LFU 在 Redis 中采用近似策略，而不是对所有 Key 的真实访问历史进行无限精确统计。应根据业务行为选择策略，并监控实际命中率和淘汰情况。

[Key eviction | 键淘汰](https://redis.io/docs/latest/develop/reference/eviction/)



### 8.2 大 Key（Big Key）会带来什么风险？

大 Key 不只是“占内存很多”。

例如一个 List 中有数百万个元素，或者一个 Hash 下包含大量 Field。访问、删除、遍历、序列化或批量返回都可能增加 CPU、网络和内存压力。

常见治理方式：

- 控制单个集合的元素数量，合理分片。
- 避免在业务热路径执行无边界的集合全量读取。
- 批量删除或遍历时使用适当的渐进操作或后台治理策略。
- 定期使用 `redis-cli --bigkeys` 辅助排查，并结合实际内存占用分析。

`--bigkeys` 可以作为发现候选项的工具，但它不是完整的内存画像；还应结合 `MEMORY USAGE`、实例内存指标和具体 Key 的结构来判断。

### 8.3 热 Key（Hot Key）又是什么？

热 Key 与大 Key 不同：

- 大 Key 的主要风险是数据体量和单次操作成本。
- 热 Key 的主要风险是访问集中，可能使单个 Redis 节点、连接或网络路径成为瓶颈。

热 Key 即使只有几百字节，也可能因被极高频率访问而成为瓶颈。

治理措施可以包括：

1. 对允许短暂陈旧的数据使用本地缓存，降低远程访问频率。
2. 在确保语义正确的情况下，对计数、统计等高频更新设计合理的批量化或分片方案。
3. 避免把分布式锁、排行榜或单一计数器设计成不必要的全局热点。
4. 通过命令统计、应用链路和实例指标确认具体瓶颈，再决定是否拆分 Key 或迁移数据。

## 九、Redis 单线程与性能排障：不要把它理解成“所有工作都单线程”

Redis 的命令执行模型具有串行处理的重要特点，但 Redis 6.x 也可以使用 I/O 线程等机制改善部分网络读写处理。因此，面试时更准确的说法是：

Redis 核心命令执行具有串行化特点，许多命令在单个执行路径中完成；这有利于避免复杂锁竞争，但慢命令会影响其他请求的延迟。

Redis 官方的延迟排查指南建议，从慢命令、操作系统、持久化以及其他造成延迟尖峰的因素入手。

[Redis latency monitoring | Redis 延迟监控](https://redis.io/docs/latest/operate/oss_and_stack/management/optimization/latency-monitor/?utm_source=chatgpt.com)

[Diagnosing latency issues | 诊断延迟问题](https://redis.io/docs/latest/operate/oss_and_stack/management/optimization/latency/)



### 9.1 常用排障命令

```
INFO
INFO memory
INFO stats
INFO clients
INFO persistence
INFO replication
SLOWLOG GET 20
LATENCY DOCTOR
```

这些命令可帮助了解实例内存、统计数据、连接情况、持久化状态、复制状态和慢命令情况。

其中 `SLOWLOG GET 20` 获取最近的慢日志记录。慢日志统计的是 Redis 执行命令本身的时间，不包含完整的客户端网络往返时间，因此还需要结合应用端耗时和网络指标分析。

[SLOWLOG GET | 查询慢日志](https://redis.io/docs/latest/commands/slowlog-get/?utm_source=chatgpt.com)

[Redis latency monitoring | Redis 延迟监控](https://redis.io/docs/latest/operate/oss_and_stack/management/optimization/latency-monitor/?utm_source=chatgpt.com)



### 9.2 Java 后端还必须检查连接池和超时

很多 Redis 故障看上去像是“Redis 很慢”，实际可能来自：

- 连接池耗尽，线程等待借用连接。
- 客户端连接或读写超时配置不合理。
- 单个请求发起过多 Redis 命令。
- 序列化、对象映射或网络传输的开销过大。
- 高并发下大量线程同时回源数据库。
- Redis 服务端执行慢命令或出现持久化引起的延迟尖峰。

排查时至少区分三个耗时：

```
业务总耗时
  = 连接获取等待 + 网络往返 + Redis命令处理
    + 数据序列化/反序列化 + 应用其他处理
```

这个公式是用于分析的简化模型，不是对每次调用耗时的严格计时恒等式。真实链路可能存在并行执行、连接复用与不同阶段的重叠。

## 十、消息机制：Pub/Sub 与 Stream 有什么区别？

Redis 也可以用于事件通知与消息处理，但必须区分不同的可靠性语义。

| 特性       | Pub/Sub                            | Stream                                 |
| ---------- | ---------------------------------- | -------------------------------------- |
| 消息定位   | 实时发布与订阅                     | 持久化的有序消息流                     |
| 离线消费者 | 发布时未订阅的消费者通常收不到消息 | 可读取历史消息                         |
| 消费组     | 不提供 Stream 式消费组             | 支持消费组                             |
| ACK 确认   | 不提供消费确认机制                 | 消费者可以通过 `XACK` 确认             |
| 失败恢复   | 不提供内建消息重放机制             | 可管理待处理消息并重新分配处理         |
| 适用场景   | 在线通知、轻量事件广播             | 事件处理、后台任务和需要消费确认的场景 |

Redis Stream 提供消息 ID、消费组、待处理消息等能力。

[Redis data types](https://redis.io/docs/latest/develop/data-types/?utm_source=chatgpt.com)

[Redis 6.2官方文档](https://redis.io/docs/latest/develop/whats-new/6-2/?utm_source=chatgpt.com)



例如：

```
XADD order-events * orderId 1001 eventType CREATED
```

消费者组：

```
XGROUP CREATE order-events order-workers $ MKSTREAM
```

读取新消息：

```
XREADGROUP GROUP order-workers worker-1
  COUNT 10 BLOCK 5000
  STREAMS order-events >
```

确认消息：

```
XACK order-events order-workers <message-id>
```

以上命令需要根据实际数据选择 Stream 名、消费组及消息 ID；换行格式仅用于阅读，执行时应作为一条完整命令提交。

还要注意，消息确认不等于业务处理已经具备 exactly-once（精确一次）语义。消费者可能在业务操作成功、确认消息之前崩溃，随后再次收到同一消息。因此，业务仍需要幂等处理、重复消息识别以及必要的失败重试和死信治理。

## 十一、Spring Boot 3.x 中使用 Redis 的工程规范

在 Java 项目中，建议理解 Spring Cache 抽象与 Spring Data Redis 两层能力：

- Spring Cache：通过 `@Cacheable`、`@CacheEvict`、`@CachePut` 提供统一的缓存抽象。
- Spring Data Redis：通过 `RedisTemplate`、`StringRedisTemplate` 等进行更细粒度的 Redis 数据操作。

### 11.1 常见注解

| 注解          | 作用                               | 典型场景       |
| ------------- | ---------------------------------- | -------------- |
| `@Cacheable`  | 缓存方法结果，命中时可跳过方法执行 | 查询缓存       |
| `@CacheEvict` | 删除指定缓存项                     | 数据更新后失效 |
| `@CachePut`   | 执行方法，并将结果放入缓存         | 明确刷新缓存   |

常见陷阱：

1. Spring AOP 代理机制下，同一个 Bean 内通过 `this` 自调用缓存方法，可能绕过代理，导致注解行为不生效。
2. 使用 `@Cacheable` 不意味着已经设计好 TTL。必须结合实际 CacheManager 和 Redis 缓存配置。
3. 缓存 Key 必须有稳定的命名规则，并考虑租户、用户权限及数据版本等隔离要求。
4. 不要随意使用 Java 原生对象反序列化处理不可信数据。生产环境应明确序列化协议与允许的类型范围。
5. 缓存失效逻辑要考虑数据库事务是否提交、缓存操作是否成功及失败后的恢复策略。

### 11.2 推荐的缓存 Key 规范

例如：

```
product:v1:{productId}
user:v1:{userId}
order:detail:v1:{tenantId}:{orderId}
```

其中 `v1` 用来表达缓存结构或序列化协议的版本，而不是让所有缓存 Key 永久固定。

Key 设计需要做到：

- 避免不同业务对象意外共用同一 Key。
- 将必要的租户与数据范围纳入 Key，防止跨租户数据污染。
- 避免把敏感个人信息直接写入 Key。
- 控制 Key 与缓存对象大小。
- 对结构升级制定兼容、迁移或失效方案。

### 11.3 事务提交后失效缓存的示意实现

如果需要保证缓存删除发生在数据库事务成功提交后，可以采用事务事件监听：

```
public record ProductUpdatedEvent(Long productId) {
}
```

在业务事务内发布事件：

```
@Transactional
public void updateProduct(Product product) {
    productMapper.updateById(product);

    eventPublisher.publishEvent(
            new ProductUpdatedEvent(product.getId())
    );
}
```

在另一个 Spring Bean 中监听：

```
@Component
public class ProductCacheInvalidator {

    private final StringRedisTemplate redisTemplate;

    public ProductCacheInvalidator(
            StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void onProductUpdated(ProductUpdatedEvent event) {
        redisTemplate.delete(
                "product:v1:" + event.productId()
        );
    }
}
```

需要补齐的导入包括 Spring 的 `TransactionalEventListener`、`TransactionPhase`、`Component`，以及项目使用的事件发布器与 Mapper 依赖。

这是一种事务成功提交后再删除缓存的基础实现。但普通事务事件不是天然可靠的持久化消息：如果进程在数据库提交后、缓存删除前崩溃，就可能遗漏失效操作。对于重要业务，应进一步考虑事务 Outbox、可靠消息或可重试的缓存失效任务。

## 十二、Redis 安全：不能因为它是内网服务就忽略风险

Redis 官方明确建议，不应将 Redis 直接暴露给不可信的公网访问；应通过网络隔离、访问控制和受限的客户端权限保护实例。

[Redis security | Redis 安全](https://redis.io/docs/latest/operate/oss_and_stack/management/security/?utm_source=chatgpt.com)



中高级 Java 工程师需要关注：

- Redis 不应直接开放给互联网用户访问。
- 使用最小权限原则，不让业务账号无条件拥有全部命令权限。
- 根据环境配置 ACL 用户、密码、网络访问策略。
- 在需要保护网络传输的场景使用 TLS，并核实部署构建是否支持。
- 对 `FLUSHALL`、`CONFIG` 等高风险管理命令设置严格的操作权限。
- 不要在代码仓库、日志或异常输出中泄露 Redis 密码、连接地址及敏感缓存内容。

Redis 6.x 支持 ACL，可限制特定账号可以执行的命令与访问的 Key；在 Redis 6.2 中还支持 Pub/Sub 通道访问管理。

[ACL | Redis访问控制列表](https://redis.io/docs/latest/operate/oss_and_stack/management/security/acl/?utm_source=chatgpt.com)





## 十三、生产事故排查清单

遇到“接口突然变慢”时，不要直接把原因归为 Redis 宕机。

### Redis 故障排查顺序

### 1

先看应用

Redis 调用耗时、连接获取时间、超时和异常率是否升高。

### 2

再看 Redis

检查 INFO、SLOWLOG、LATENCY 和内存使用情况。

### 3

检查 Key 结构

排查大 Key、热 Key、全量读取和慢命令。

### 4

检查数据源

是否出现缓存击穿、穿透、雪崩导致数据库连接池被打满。

### 5

检查部署状态

复制延迟、主从切换、Cluster 槽位状态、持久化及网络问题。

### 6

验证恢复

恢复后检查命中率、数据库压力、缓存一致性和重复任务。

如果需要深入理解慢命令和延迟，建议阅读 Redis 官方的延迟监控和排障文档。

[Diagnosing latency issues | 诊断延迟问题](https://redis.io/docs/latest/operate/oss_and_stack/management/optimization/latency/?utm_source=chatgpt.com)

[Redis latency monitoring | Redis延迟监控](https://redis.io/docs/latest/operate/oss_and_stack/management/optimization/latency-monitor/?utm_source=chatgpt.com)



## 十四、中高级 Java 开发者的 Redis 知识清单

## Redis 能力自检

0/16 项

基础与数据结构

能解释 String、Hash、List、Set、ZSet、Stream 的适用场景

理解常见命令的时间复杂度与内存成本

能解释过期机制、淘汰策略及 TTL 的作用

缓存架构

能区分缓存穿透、击穿、雪崩并设计治理方案

理解 Cache-Aside、缓存一致性和并发读写竞争

能使用布隆过滤器、互斥重建、多级缓存等方案并说明边界

分布式与可靠性

理解 SET NX PX、Lua 解锁、租约过期和栅栏令牌

掌握 RDB、AOF、主从复制、Sentinel 与 Cluster 的区别

知道 Pub/Sub、Stream、Pipeline、事务的适用边界

生产工程化

能排查大 Key、热 Key、慢命令和连接池耗尽

理解内存淘汰、监控告警、网络安全与 ACL

能解释故障切换、消息重复、幂等和缓存失效重试

自检进度仅反映你标记的完成情况，不等于已通过实战考核。

提交自检结果，进入 Redis 实战考核

## 最后：请牢牢记住这 6 条

1. Redis 快，不代表所有 Redis 命令都快。 操作规模、命令复杂度和网络开销一样重要。
2. 缓存穿透是不存在的数据，击穿是单个热点 Key 失效，雪崩是大规模失效或缓存整体不可用。
3. 缓存和数据库不是天然强一致的。 更新顺序、并发竞争、重试与恢复策略都需要设计。
4. 分布式锁不等于绝对正确性。 必须考虑租约失效、旧持有者继续执行和业务幂等。
5. Redis 高可用不等于零数据丢失。 复制、持久化与故障转移均有各自的边界。
6. 中高级工程师的价值在于可验证的工程判断。 既能解释原理，也能通过指标、日志和实际故障演练证明方案有效。

记忆简语：结构选对、缓存治稳、一致性讲清、锁要安全、故障能定位、恢复可验证。

接下来的学习重点应当是：使用 Spring Boot 3.x 搭建一个 Redis 缓存模块，实际实现空值缓存、热点 Key 互斥重建、随机 TTL、事务提交后失效、幂等控制和监控排障，再通过并发测试验证其行为。