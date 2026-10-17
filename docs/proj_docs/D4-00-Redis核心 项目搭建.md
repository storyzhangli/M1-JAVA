### 核心结论

本次迭代严格基于D04模块现有Redis骨架（复用Lettuce连接池、Jackson序列化、全局异常体系），采用**Cache Aside旁路缓存模式 + Redisson分布式锁**实现生产级用户信息缓存方案；完整覆盖「用户信息缓存、缓存击穿防护、多维度过期策略、服务端持久化配置」四大需求；代码完全对齐仓库分层规范与命名约定，无重复配置，可直接并入D04模块通过`mvn clean package`编译。

---

## 一、增量依赖与配置（兼容D04现有骨架）

### 1. 增量Maven依赖（D04/pom.xml）

说明：D04已集成`spring-boot-starter-data-redis`基础依赖，本次仅新增Redisson分布式锁组件，版本兼容SpringBoot 3.5.x与JDK21。

```
<!-- Redisson分布式锁：缓存击穿核心组件，兼容SpringBoot3自动装配 -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.30.0</version>
</dependency>
```

### 2. 配置兼容说明

- 复用D04现有`spring.data.redis`连接参数（host、port、database、连接池），Redisson自动读取Spring Redis配置，无需重复配置连接地址
- 复用D04现有RedisTemplate的Jackson序列化配置，保证缓存值序列化格式与项目全局一致
- 仅补充锁看门狗等业务级配置，追加到application.yml：

```yml
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

  # D04 Redisson 专用配置
  redis:
    redisson:
      config: |
        singleServerConfig:
          address: "redis://127.0.0.1:6379"
          database: 0
          connectionMinimumIdleSize: 2

        lockWatchdogTimeout: 30000

# MyBatis-Plus、Knife4j 等其他现有配置继续保留

# MyBatis-Plus配置
mybatis-plus:
  mapper-locations: classpath:mapper/**/*.xml
  type-aliases-package: com.zl.d04.entity # todo 重命名包名，此处需要手动修改
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto

# D03
# Knife4j 配置
knife4j:
  enable: true
  openapi:
    title: AI智能知识助手
    description: 阶段1工程骨架接口
    version: 1.0.0
  # 生产环境可设置为false关闭文档
#  production: false
```

### 3. 缓存常量统一维护（D04 common模块）

```
package com.m1.java.d04.common.constants;

/**
 * 缓存全局常量
 * 「规范说明」所有缓存Key前缀、过期时间统一维护，禁止业务代码硬编码
 */
public interface CacheConstants {
    // 用户信息缓存
    String USER_CACHE_PREFIX = "sys:user:";
    String USER_LOCK_PREFIX = "lock:sys:user:";
    
    // 过期时间配置（秒）
    long USER_CACHE_BASE_TTL = 30 * 60;      // 基础过期30分钟
    long TTL_RANDOM_OFFSET_MAX = 5 * 60;     // 随机偏移上限5分钟，防雪崩
    long NULL_VALUE_TTL = 2 * 60;            // 空值过期2分钟，防穿透
}
```

---

## 二、用户缓存核心业务实现

### 1. 实体层（复用D04 RBAC用户实体）

```
package com.m1.java.d04.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统用户实体
 * 「坑点提示」Jackson序列化要求保留无参构造，Lombok @Data默认生成
 */
@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

### 2. Mapper层（复用MyBatisPlus基础能力）

```
package com.m1.java.d04.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m1.java.d04.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
```

### 3. Service接口

```
package com.m1.java.d04.service;

import com.m1.java.d04.entity.SysUser;

public interface SysUserService {
    /**
     * 根据ID查询用户（带缓存+防击穿）
     */
    SysUser getUserById(Long userId);

    /**
     * 更新用户信息（同步失效缓存）
     */
    boolean updateUser(SysUser user);

    /**
     * 删除用户（同步失效缓存）
     */
    boolean deleteUser(Long userId);
}
```

### 4. ServiceImpl核心实现（防击穿逻辑）

```
package com.m1.java.d04.service.impl;

import com.m1.java.d04.common.constants.CacheConstants;
import com.m1.java.d04.entity.SysUser;
import com.m1.java.d04.mapper.SysUserMapper;
import com.m1.java.d04.service.SysUserService;
import com.m1.java.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现类
 * 「设计模式」Cache Aside旁路缓存：读命中直接返回，未命中查库后回写；写操作先更库再删缓存
 * 「防击穿方案」Redisson分布式锁 + 双重缓存检查，热点Key过期仅放行1个请求打库
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final SysUserMapper sysUserMapper;

    @Override
    public SysUser getUserById(Long userId) {
        String cacheKey = CacheConstants.USER_CACHE_PREFIX + userId;

        // 1. 优先查询缓存
        Object cacheValue = redisTemplate.opsForValue().get(cacheKey);
        if (cacheValue != null) {
            // 空值标记：直接返回null，避免缓存穿透
            if (cacheValue instanceof String && "NULL_MARK".equals(cacheValue)) {
                return null;
            }
            return (SysUser) cacheValue;
        }

        // 2. 缓存未命中，加分布式锁防击穿
        String lockKey = CacheConstants.USER_LOCK_PREFIX + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 尝试加锁：最大等待3秒，锁持有超时30秒（看门狗自动续期）
            boolean lockAcquired = lock.tryLock(3, 30, TimeUnit.SECONDS);
            if (!lockAcquired) {
                // 加锁失败：短暂休眠后重试，也可根据业务降级返回旧数据
                Thread.sleep(200);
                return getUserById(userId);
            }

            // 「原理说明」双重检查缓存：等待锁期间，可能已有其他线程完成缓存回写
            cacheValue = redisTemplate.opsForValue().get(cacheKey);
            if (cacheValue != null) {
                if (cacheValue instanceof String && "NULL_MARK".equals(cacheValue)) {
                    return null;
                }
                return (SysUser) cacheValue;
            }

            // 3. 执行数据库查询
            SysUser user = sysUserMapper.selectById(userId);

            // 4. 回写缓存，区分空值与正常值
            if (user == null) {
                // 空值短过期：防止恶意请求穿透数据库
                redisTemplate.opsForValue().set(cacheKey, "NULL_MARK", 
                        CacheConstants.NULL_VALUE_TTL, TimeUnit.SECONDS);
            } else {
                // 「坑点提示」基础TTL叠加随机偏移，避免大量Key同时过期引发缓存雪崩
                long ttl = CacheConstants.USER_CACHE_BASE_TTL 
                        + ThreadLocalRandom.current().nextLong(CacheConstants.TTL_RANDOM_OFFSET_MAX);
                redisTemplate.opsForValue().set(cacheKey, user, ttl, TimeUnit.SECONDS);
            }
            return user;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("用户查询缓存锁中断");
        } finally {
            // 仅持有锁的线程释放锁，避免误释放
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public boolean updateUser(SysUser user) {
        // Cache Aside标准流程：先更新数据库，再删除缓存
        int rows = sysUserMapper.updateById(user);
        if (rows > 0) {
            redisTemplate.delete(CacheConstants.USER_CACHE_PREFIX + user.getId());
        }
        return rows > 0;
    }

    @Override
    public boolean deleteUser(Long userId) {
        int rows = sysUserMapper.deleteById(userId);
        if (rows > 0) {
            redisTemplate.delete(CacheConstants.USER_CACHE_PREFIX + userId);
        }
        return rows > 0;
    }
}
```

---

## 三、缓存过期策略体系

### 1. 业务层可控过期策略（主动治理）

| 策略类型    | 实现方式                               | 解决问题                                    | 适用场景         |
| ----------- | -------------------------------------- | ------------------------------------------- | ---------------- |
| 随机TTL偏移 | 基础过期时间 + 0~5分钟随机值           | 缓存雪崩：大量Key同时过期导致数据库压力突增 | 全量业务缓存     |
| 分级过期    | 热点数据30分钟、冷数据5分钟、空值2分钟 | 内存资源优化，防止空值膨胀                  | 不同热度数据分级 |
| 主动失效    | 数据更新/删除时同步删除缓存            | 缓存与数据库数据不一致                      | 数据写操作场景   |
| 空值短存    | 不存在的数据缓存2分钟自动过期          | 缓存穿透：恶意查询不存在的数据打穿数据库    | 空结果缓存       |

### 2. Redis底层内置过期策略（被动清理）

- **定期删除**：Redis每100ms随机抽取一批设置了过期时间的Key，检查过期则删除，保证性能不阻塞主线程
- **惰性删除**：访问Key时校验是否过期，过期则删除并返回空
- 「面试考点」Redis不采用定时全量删除，是因为全量扫描会严重阻塞主线程，定期+惰性组合是性能与内存的平衡方案

---

## 四、Redis服务端持久化配置（生产级）

基于Redis 6.2.12版本，推荐**RDB+AOF混合持久化**，兼顾数据安全性与恢复速度，配置如下（redis.conf）：

```
# ===== RDB快照配置 =====
save 900 1      # 900秒内至少1次写入，触发快照
save 300 10     # 300秒内至少10次写入
save 60 10000   # 60秒内至少10000次写入
stop-writes-on-bgsave-error yes  # 快照失败时禁止写入，避免数据不一致
rdbcompression yes              # 开启RDB压缩
rdbchecksum yes                 # 开启校验
dbfilename dump.rdb
dir ./data

# ===== AOF追加配置 =====
appendonly yes
appendfilename "appendonly.aof"
appendfsync everysec            # 每秒刷盘一次，性能与安全平衡
no-appendfsync-on-rewrite no    # 重写期间不刷盘，避免IO阻塞
auto-aof-rewrite-percentage 100 # AOF增长100%触发重写
auto-aof-rewrite-min-size 64mb  # 重写最小触发大小

# ===== 混合持久化（Redis4.0+支持） =====
aof-use-rdb-preamble yes        # AOF重写时将RDB内容写入开头，增量命令追加，恢复快且数据完整
```

---

## 五、核心原理、坑点与面试考点

### 核心原理

1. **缓存击穿防护**：热点Key过期瞬间，通过分布式锁串行化数据库请求，配合双重检查保证仅第一个请求查库，其余请求等待缓存回写后直接命中
2. **Cache Aside模式**：业界最通用的缓存一致性方案，写操作先更新数据库再失效缓存，保证最终一致性，实现成本最低
3. **Redisson看门狗机制**：锁持有期间自动续期，避免业务执行超时导致锁提前释放，引发并发问题

### 坑点提示

1. 序列化兼容：实体必须保留无参构造，否则Jackson反序列化失败；D04现有全局序列化已适配JDK21时间类型
2. 锁释放安全：必须在finally块释放锁，且仅持有锁的线程可释放，避免跨线程误释放
3. 一致性顺序：禁止先删缓存再更库，会导致并发场景下脏数据写入缓存；必须先更库后删缓存
4. 空值膨胀：大量不存在的Key查询会产生冗余空值缓存，必须设置短过期时间

### 高频面试题

1. 缓存击穿、穿透、雪崩的区别是什么？分别有什么解决方案？
2. Redis的过期删除策略有哪些？为什么不用定时全量删除？
3. RDB和AOF持久化各有什么优缺点？生产环境如何选型？
4. 分布式锁的实现方式有哪些？Redisson锁的实现原理和看门狗机制是什么？
5. 缓存与数据库的一致性方案有哪些？Cache Aside为什么是删除缓存而不是更新缓存？

---

### 记忆简语

一锁二查三回写，随机TTL防雪崩；
空值短存防穿透，更库删缓存一致；
RDB快照AOF追，混合持久最稳妥。

需要我补充**Controller层接口实现**、**单元测试用例**或者**Redis集群模式下的配置适配**吗？