### ✅ 核心结论

Redis 6.2.12 是生产环境广泛落地的稳定LTS版本，核心竞争力是**纯内存+单线程命令执行+IO多路复用**的高性能架构，配合10余种原生数据结构覆盖缓存、分布式锁、限流、排行榜、消息队列等全场景；缓存击穿/穿透/雪崩本质都是「请求漏到数据库引发过载」，解决方案遵循「前置拦截→缓存防护→降级兜底」三层逻辑；中高级Java开发者必须掌握线程模型、持久化、分布式锁、集群架构、内存治理五大核心板块；Redis 8 跨代升级了持久化架构、多线程模型与权限体系，废弃多项旧特性，迁移必须先验证命令兼容性、客户端驱动与数据格式，禁止直接替换二进制启动。

---

## 一、Redis 6.2.12 核心：数据结构体系

### 1. 五大基础类型与底层编码（面试高频）

Redis官方定义的原生基础数据类型包含String、Hash、List、Set、Sorted Set五大类，是所有业务场景的核心载体；所有数据结构都有底层编码自动转换机制，在满足条件时自动切换，兼顾内存占用与访问性能。
《Redis data types | Redis 数据类型》：[https://redis.io/docs/latest/develop/data-types/](https://redis.io/docs/latest/develop/data-types/)

| 数据类型   | 底层编码                | 切换触发条件                                              | 典型业务场景                            |
| ---------- | ----------------------- | --------------------------------------------------------- | --------------------------------------- |
| **String** | int / embstr / raw      | 8字节内整数用int；≤44字节短字符串用embstr；>44字节用raw   | 对象缓存、计数器、分布式锁、Session存储 |
| **List**   | quicklist               | Redis3.2+默认，由多个ziplist双向拼接而成                  | 消息队列、栈、分页列表、最近联系人      |
| **Hash**   | ziplist / hashtable     | 字段数<512 且 每个value<64字节用ziplist，否则升级哈希表   | 对象字段缓存、字典表、用户信息          |
| **Set**    | intset / hashtable      | 全整数且元素数<512用intset，否则升级哈希表                | 去重、标签系统、共同关注、交集计算      |
| **ZSet**   | ziplist / skiplist+dict | 元素数<128 且 member长度<64字节用ziplist，否则跳表+哈希表 | 排行榜、权重队列、范围查找、热度排序    |

**Redis6.2专属优化**：优化了ziplist内存碎片问题，新增`HRANDFIELD`、`ZRANDMEMBER`等随机字段命令，支持毫秒级过期精度。

### 2. 高级数据结构（中高级必备）

Redis官方原生支持的高级数据结构包含Bitmap、HyperLogLog、Geospatial、Stream、JSON、概率数据类型、时间序列等，覆盖大数据量统计、地理位置、消息队列、向量检索等专业场景。
《Redis data types | Redis 数据类型》：[https://redis.io/docs/latest/develop/data-types/](https://redis.io/docs/latest/develop/data-types/)

- **Bitmap**：基于String的位运算结构，10亿用户签到仅需128MB内存；适用：日活统计、用户标签、布隆过滤器底层。
- **HyperLogLog**：基数统计算法，标准误差0.81%，1亿UV仅需12KB；适用：页面UV、独立访客统计，不保证100%精确。
- **Geo（Geospatial indexes）**：基于ZSet封装的地理位置索引，支持经纬度存储、距离计算、附近查找；适用：附近商家、骑手距离计算。
- **Stream**：Redis5.0引入的专业消息队列，支持消费组、ACK确认、消息持久化、死信队列；Redis6.2优化了XPENDING查询性能，是Redis原生MQ的首选方案。
- **布隆过滤器（Bloom filter）**：Redis原生支持的概率型数据结构，通过多个哈希函数映射到位数组实现「不存在则一定不存在，存在则可能存在」的判定，是缓存穿透场景的主流前置拦截方案；官方提供`BF.RESERVE`、`BF.ADD`、`BF.EXISTS`等完整操作命令集，支持容量扩容与误判率配置。
《Bloom filter | 布隆过滤器》：[https://redis.io/docs/latest/develop/data-types/probabilistic/bloom-filter/](https://redis.io/docs/latest/develop/data-types/probabilistic/bloom-filter/)

### 3. 数据选型黄金法则

- 简单对象缓存：优先String+JSON序列化，字段多且需字段级操作选Hash
- 排行榜/有序队列：必选ZSet，禁止用List+内存排序实现
- 去重/集合运算：选Set，大数据量基数统计选HyperLogLog
- 消息队列：轻量场景用List，需消费组、持久化用Stream，生产级优先RocketMQ/Kafka

---

## 二、缓存三大问题：击穿/穿透/雪崩（面试必考）

Redis旁置缓存（Cache-aside）模式的典型痛点是高并发场景下的缓存踩踏（cache stampede）：当热门缓存key过期时，大量进程同时查询数据库同一条记录，放大数据库负载峰值，也就是通常所说的缓存击穿与雪崩问题。
《Redis cache-aside | Redis 旁置缓存》：[https://redis.io/docs/latest/develop/use-cases/cache-aside/](https://redis.io/docs/latest/develop/use-cases/cache-aside/)

### 核心对比表

| 问题         | 触发场景                             | 核心特征                        | 根本原因                  |
| ------------ | ------------------------------------ | ------------------------------- | ------------------------- |
| **缓存穿透** | 查询数据库和缓存都**不存在**的数据   | 大量非法请求直接打穿到数据库    | 请求完全绕过了缓存        |
| **缓存击穿** | 单个**热点key过期**瞬间，高并发涌入  | 热点key失效，数据库瞬时压力陡增 | 热点并发未做限流控制      |
| **缓存雪崩** | 大量key**同时过期** 或 Redis节点宕机 | 数据库压力指数级上升，甚至宕机  | 批量失效/缓存层整体不可用 |

### 1. 缓存穿透：解决方案与工程实现

#### 方案1：空值缓存（简单兜底）

对查询为空的结果也写入缓存，设置较短过期时间（如30秒），避免重复查库。

- 坑点：空值会占用内存，必须设置短TTL；恶意大量不同key穿透仍会压库。

#### 方案2：布隆过滤器（前置拦截，主流方案）

将所有存在的业务key提前存入布隆过滤器，请求先过过滤器，判定不存在直接返回，完全不碰Redis和数据库。该方案基于Redis原生布隆过滤器数据结构实现，官方支持完整的增删查与扩容能力，误判率可通过参数配置控制。

- 原理：多个哈希函数映射到位数组，**不存在则一定不存在，存在则可能存在**，存在可控误判率。
《Bloom filter | 布隆过滤器》：[https://redis.io/docs/latest/develop/data-types/probabilistic/bloom-filter/](https://redis.io/docs/latest/develop/data-types/probabilistic/bloom-filter/)

#### 代码示例（SpringBoot3.5.16 + Guava布隆过滤器）

```
// 初始化布隆过滤器：预计100万数据，误判率0.01%
@Bean
public BloomFilter<Long> productBloomFilter() {
    return BloomFilter.create(Funnels.longFunnel(), 1_000_000, 0.0001);
}

// 业务查询逻辑
public Product getProductById(Long productId) {
    // 1. 布隆过滤器前置拦截
    if (!productBloomFilter.mightContain(productId)) {
        return null;
    }
    // 2. 查询Redis缓存
    String key = "product:" + productId;
    Product product = redisTemplate.opsForValue().get(key);
    if (product != null) {
        return product;
    }
    // 3. 查询数据库
    product = productMapper.selectById(productId);
    // 4. 空值也缓存，30秒短过期；正常值缓存1小时
    long ttl = product == null ? 30 : 3600;
    redisTemplate.opsForValue().set(key, product == null ? new Product() : product, ttl, TimeUnit.SECONDS);
    return product;
}
```

### 2. 缓存击穿：解决方案与工程实现

缓存击穿对应官方定义的cache stampede（缓存踩踏）场景，即单个热点key失效时，高并发请求同时穿透到数据库，造成数据库瞬时压力飙升。
《Redis cache-aside | Redis 旁置缓存》：[https://redis.io/docs/latest/develop/use-cases/cache-aside/](https://redis.io/docs/latest/develop/use-cases/cache-aside/)

#### 方案1：分布式互斥锁（生产最常用）

热点key失效时，只放行1个请求查库并回写缓存，其余请求等待重试。官方文档也提到可通过Lua脚本实现原子性的互斥锁逻辑，避免外部锁的开销。

- 技术选型：Redisson可重入锁，自带看门狗续期，避免锁提前释放。

#### 方案2：逻辑过期（热点永不过期）

缓存中不设置物理过期时间，value内存储逻辑过期时间；前台请求发现逻辑过期立刻返回旧值，异步触发后台更新。

- 适用：秒杀、热点商品等绝对不能打穿数据库的高并发场景。

#### 代码示例（Redisson互斥锁实现击穿防护）

```
public Product getProductWithLock(Long productId) {
    String key = "product:" + productId;
    // 1. 先查缓存
    Product product = redisTemplate.opsForValue().get(key);
    if (product != null) {
        return product;
    }
    // 2. 加分布式锁，只放一个请求查库
    RLock lock = redissonClient.getLock("lock:product:" + productId);
    try {
        boolean locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
        if (!locked) {
            // 获取锁失败，休眠100ms重试
            Thread.sleep(100);
            return getProductWithLock(productId);
        }
        // 3. 二次检查：防止锁等待期间缓存已被其他线程更新
        product = redisTemplate.opsForValue().get(key);
        if (product != null) {
            return product;
        }
        // 4. 查库并回写缓存，TTL加随机值打散
        product = productMapper.selectById(productId);
        int randomTtl = 3600 + new Random().nextInt(300);
        redisTemplate.opsForValue().set(key, product, randomTtl, TimeUnit.SECONDS);
        return product;
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
    } finally {
        // 5. 只释放当前线程持有的锁
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

### 3. 缓存雪崩：解决方案与工程实现

1. **过期时间打散**：所有key的TTL叠加随机值（如基础1小时+0~300秒随机），从根源避免批量同时过期。
2. **多级缓存架构**：Caffeine本地缓存 + Redis分布式缓存，热点数据放本地，大幅降低Redis压力。
3. **服务降级与熔断**：Redis不可用时触发降级，返回默认值或兜底数据，保护数据库不被压垮。
4. **高可用部署**：哨兵/Cluster集群架构，避免Redis单点故障；从节点做读写分离，分摊读压力。

---

## 三、中高级Java开发者必知Redis核心知识

### 1. 线程模型（面试八股核心）

Redis核心采用单线程事件循环处理命令，从设计上避免锁开销并保证所有操作的原子性，这是Redis高性能的核心原因之一。

- **Redis6.0之前**：单线程处理所有命令执行，基于epoll IO多路复用，纯内存操作+避免线程切换开销，因此性能极高。
- **Redis6.2多线程IO**：**命令执行依然是单线程**，多线程只负责「网络IO读写+协议解析」，提升高并发小数据包场景的吞吐量。该特性通过`io-threads`配置项开启，默认值为1，线程数建议设为CPU核心数的一半。
- 坑点：大value场景多线程IO收益极低，甚至性能下降。

**面试标准回答：Redis为什么单线程还这么快？**

>
> 答题要点：纯内存操作、IO多路复用模型、避免线程上下文切换、底层数据结构高效、内存操作极致优化。

### 2. 持久化机制

Redis官方提供RDB、AOF、无持久化、混合持久化四种持久化策略，可根据数据可靠性要求灵活选型。
《Redis persistence | Redis 持久化机制》：[https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/)

| 持久化方式     | 实现原理                               | 优点                     | 缺点                       | 生产适用场景               |
| -------------- | -------------------------------------- | ------------------------ | -------------------------- | -------------------------- |
| RDB            | fork子进程生成数据快照，二进制存储     | 恢复速度快、文件体积小   | 数据丢失多、fork阻塞主线程 | 全量备份、冷备、数据迁移   |
| AOF            | 追加写命令日志，按策略刷盘             | 数据丢失少、可读性强     | 文件大、恢复速度慢         | 数据可靠性要求高的核心业务 |
| **混合持久化** | AOF重写时，开头写RDB快照，后面追加命令 | 兼顾恢复速度与数据完整性 | 低版本Redis不兼容          | Redis4.0+生产环境默认方案  |

**Redis6.2优化**：AOF重写增量刷盘优化，降低重写时的IO峰值；支持RDB增量校验。

### 3. 内存管理

#### 过期删除策略

- **惰性删除**：访问key时才检查是否过期，过期则删除；对CPU友好，对内存不友好。
- **定期删除**：每隔一段时间随机抽取部分key检查，删除过期数据；兼顾CPU和内存。
- 核心结论：Redis不是key过期就立刻删除，过期key会残留占用内存。

#### 内存淘汰策略（8种，默认noeviction）

当内存达到`maxmemory`限制时，Redis会根据配置的淘汰策略清理数据，官方定义了8种标准策略，分为全量key、过期key、不淘汰三大类。

- 全量key维度：`allkeys-lru`（最久未使用）、`allkeys-lfu`（最少使用）、`allkeys-random`（随机）
- 过期key维度：`volatile-lru`、`volatile-lfu`、`volatile-random`、`volatile-ttl`（最快过期）
- 不淘汰：`noeviction`（默认，内存写满时报错）
- 生产推荐：热点数据多选用`allkeys-lru`；访问频率差异大选`allkeys-lfu`。

### 4. 分布式锁（工程核心）

#### 原生Redis锁的三大经典问题

1. 不可重入：同一个线程不能多次获取同一把锁
2. 锁续期：业务执行时间超过锁过期时间，锁提前释放
3. 误删除：线程A的锁过期后，线程B拿到锁，线程A执行完误删线程B的锁

#### Redisson分布式锁（生产标准方案）

- 可重入：ThreadLocal维护重入计数，释放时计数递减，计数为0才真正释放锁
- 看门狗机制：加锁成功后启动后台线程，默认每10秒续期一次，锁默认30秒过期
- 原子释放：Lua脚本校验锁持有者，只有持有者才能删除锁
- 红锁RedLock：跨多个独立节点加锁，过半成功才算加锁成功，解决单点故障问题

**面试高频题：Redis分布式锁怎么实现？有什么问题？怎么解决？**

>
> 答题要点：SETNX加锁+过期时间；核心问题是不可重入、续期、误删；用Redisson的可重入计数、看门狗续期、Lua原子释放解决。

### 5. 集群架构

1. **主从复制**：一主多从，主写从读，数据同步；解决读压力，不能解决单点故障和写瓶颈。
2. **哨兵Sentinel**：在主从基础上增加哨兵节点，自动故障转移；解决高可用，不能解决写压力、容量瓶颈。
3. **Cluster集群**：数据分片存储，16384个哈希槽，多主多从；解决写压力、容量水平扩展，支持高可用。
   - 核心概念：槽位分配、MOVED重定向、哈希标签、扩容缩容
   - 坑点：跨槽命令不支持（如跨槽MGET），需要用哈希标签把数据分到同一个槽。

### 6. 工程最佳实践

- 禁止生产使用`KEYS *`、`FLUSHDB`等危险命令，用`SCAN`迭代遍历，重命名危险命令
- 大key拆分：单个value超过10KB考虑拆分，避免阻塞单线程事件循环
- Pipeline批量操作：减少网络IO次数，注意不是原子操作，跨节点集群不可用
- Lua脚本原子性：复杂逻辑用Lua保证原子执行，比如限流、库存扣减
- 慢查询监控：开启慢查询日志，定位慢命令，优化大key、复杂运算命令

---

## 四、Redis 6 → Redis 8 迁移重点注意事项

Redis 8 跨了Redis7大版本迭代，架构、持久化、命令都有重大变更，属于破坏性升级，不能直接替换二进制启动。

### 1. 命令与语法不兼容

- **废弃命令**：`HMSET`正式移除，统一用`HSET`；部分冷门管理命令废弃或改名。Redis 8.0对命令语法、参数规则做了多项破坏性变更，升级前需完整扫描业务命令兼容性。
- **参数变更**：部分命令的参数顺序、可选值调整，比如`SORT`命令部分参数废弃。
- **RESP3协议**：Redis8默认优先使用RESP3协议，旧客户端仅支持RESP2可能出现解析异常。
- **验证方法**：用`redis-check-aof`、`redis-check-rdb`工具扫描现有脚本与业务命令兼容性。

### 2. 持久化架构重构

Redis 7.0及以上版本对AOF机制进行了架构级重构，原单文件AOF升级为多部分AOF（Multi-part AOF），这是跨版本迁移最核心的持久化变更。
《Redis persistence | Redis 持久化机制》：[https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/)

- **MP-AOF多部分AOF**：拆分为基础AOF+增量AOF，重写效率提升数倍；Redis6的单AOF文件无法直接加载到Redis8中，必须通过工具转换或全量同步迁移。
- **RDB格式升级**：Redis8的RDB新增元数据字段，向下兼容（Redis6的RDB可导入Redis8），但向上不兼容（Redis8的RDB不能在Redis6使用）。
- **混合持久化优化**：Redis8默认开启混合持久化，恢复速度比Redis6提升40%以上。

### 3. 核心架构变化

- **多线程增强**：Redis8重构了IO线程实现模型，主进程将客户端分配给固定IO线程，命令解析与网络读写完全异步，相同配置下吞吐量最高提升112%；命令执行主线程模型不变，保证原子性。
- **内存管理升级**：内存分配器优化，碎片率降低；LFU、LRU淘汰算法更精准。
- **ACL权限增强**：Redis8将JSON、搜索、概率数据结构等命令全部纳入标准ACL分类体系，新增`search`、`json`、`bloom`等多个细粒度权限类别，旧ACL规则需要重新适配。

### 4. 客户端与Java驱动适配

- **Jedis**：必须升级到5.x+版本，旧版本不支持Redis8新特性与协议。
- **Lettuce**：SpringBoot3.5.16默认Lettuce 6.x，需升级到6.3+版本才完整兼容Redis8。
- **Redisson**：必须升级到3.30+版本，否则分布式锁、Stream、集群功能可能异常。
- **迁移原则**：**先升级客户端驱动，再升级Redis服务端**，驱动版本要同时兼容Redis6和8，支持灰度切换。

### 5. 集群迁移注意

- Cluster集群不能直接复制配置文件启动，槽位映射、节点信息格式有变更。
- 官方推荐逐个节点升级，先从副本节点开始，全部副本升级完成后再升级主节点，升级过程中集群可正常提供服务。
- 推荐迁移工具：redis-shake、云厂商DTS等，支持全量+增量同步，业务无感知迁移。
- 哈希标签、跨槽命令行为有优化，原有业务逻辑需要回归验证。

### 6. 迁移标准步骤

1. **兼容性扫描**：扫描所有业务代码中的Redis命令、Lua脚本、配置项。
2. **客户端升级**：升级Java应用的Redis驱动，兼容双版本，灰度上线。
3. **全量数据同步**：用迁移工具将Redis6数据同步到Redis8集群。
4. **灰度切流**：切10%流量到Redis8，监控性能、错误率、数据一致性。
5. **全量切换+回滚预案**：保留原Redis6集群7天，出现问题秒级切回。

---

### ⚠️ 坑点 & 面试高频考点

#### 面试八股必背

1. **缓存击穿、穿透、雪崩的区别和解决方案？**

>
> 答题要点：穿透是查不存在的数据，用布隆过滤器+空值缓存；击穿是热点key过期，用分布式锁+逻辑过期；雪崩是大量key同时过期或Redis宕机，用打散过期+多级缓存+高可用+降级。
2. **Redis为什么快？Redis6多线程是怎么回事？**

>
> 答题要点：纯内存、IO多路复用、单线程避免切换、数据结构高效；Redis6多线程只做IO读写和协议解析，命令执行还是单线程，保证原子性。
3. **Redis分布式锁实现原理？Redisson看门狗机制？**

>
> 答题要点：SETNX加锁+过期时间；Redisson可重入用ThreadLocal计数，看门狗后台线程定时续期，Lua脚本原子释放。

#### 工程避坑红线

1. 布隆过滤器只能过滤不存在的请求，有误判率，不能替代缓存。
2. 分布式锁必须设置过期时间，同时必须有续期机制，避免业务超长执行锁失效。
3. Cluster集群禁止跨槽批量操作，必须用哈希标签统一槽位。
4. 迁移Redis8必须先升客户端再升服务端，禁止直接替换RDB/AOF文件启动。

### 📝 记忆简语

>
> 五大数据结构底编码，ziplist省内存，超阈值转哈希。
> 穿透拦、击穿控、雪崩打散加高可用。
> 单线程执行命令，多线程跑IO，持久化用混合。
> 分布式锁Redisson，可重入加看门狗，Lua释放保原子。
> 六迁八，先升客户端，再同步数据，灰度验证再切流。

---

## ✍️ 随堂抽测（3题，作答即可批改）

1. 缓存击穿和缓存穿透的核心区别是什么？分别说出两种主流解决方案。
2. Redis6的多线程和传统多线程数据库有什么区别？为什么命令执行依然用单线程？
3. Redis6迁移到Redis8，持久化层面有哪些重大变更？为什么不能直接复制AOF文件启动？

---

### 参考文献

[1] Redis 官方文档. Redis data types | Redis 数据类型. [https://redis.io/docs/latest/develop/data-types/](https://redis.io/docs/latest/develop/data-types/)
[2] Redis 官方文档. Redis cache-aside | Redis 旁置缓存. [https://redis.io/docs/latest/develop/use-cases/cache-aside/](https://redis.io/docs/latest/develop/use-cases/cache-aside/)
[3] Redis 官方文档. Bloom filter | 布隆过滤器. [https://redis.io/docs/latest/develop/data-types/probabilistic/bloom-filter/](https://redis.io/docs/latest/develop/data-types/probabilistic/bloom-filter/)
[4] Redis 官方文档. Redis persistence | Redis 持久化机制. [https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/)