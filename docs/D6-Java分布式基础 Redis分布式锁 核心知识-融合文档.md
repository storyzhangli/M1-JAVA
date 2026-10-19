# Java 分布式基础与 Redis 分布式锁：中高级工程师核心知识文档

适用方向：Java 后端开发、Spring Boot 微服务、MySQL、Redis、消息队列与分布式架构。以工程原理、方案选型、故障处理和面试考核为主。

### ✅ 核心结论

CAP定理是分布式系统的基础公理，定义了**一致性（Consistency）、可用性（Availability）、分区容错（Partition tolerance）**三大属性不可同时满足；工程实践中分区容错是分布式系统的天然前提，只能在一致性与可用性之间二选一。BASE理论是CAP定理的工程化落地，以最终一致性换取系统高可用与高性能，是绝大多数互联网业务的设计准则。
分布式事务按一致性强度分为刚性强一致事务与柔性最终一致事务两大类，共7种主流落地方案，在一致性、性能、侵入性上各有明确边界，需按业务场景选型。
Redis分布式锁是生产环境最主流的分布式锁实现，基于`SETNX`原语+过期时间实现，通过Lua脚本保证加解锁原子性；生产级方案需补充可重入、看门狗续期、持有者校验三大能力，解决原生锁的经典缺陷。
中高级Java开发者必须在此基础上掌握**分布式共识协议、一致性哈希、分布式ID生成、限流算法、熔断降级**五大进阶核心板块，覆盖分布式系统从设计、落地到运维的全链路能力。

这份文档重点回答四个问题：分布式系统为什么难、数据一致性如何设计、跨服务事务如何选择、Redis 分布式锁怎样正确使用以及它有哪些不能保证的事情。

---

## 一、分布式系统的核心认知

单体应用的很多问题，可以依靠进程内锁和单数据库事务解决。进入分布式环境后，应用需要跨越网络调用其他服务，网络延迟、节点故障、请求超时、进程暂停、消息重复、数据库复制延迟都会影响系统行为。

中高级工程师首先需要建立五个认知：

1. 超时不等于失败。 远程服务可能已经完成操作，只是响应没有送达。
2. 重试不等于安全。 如果操作不是幂等的，重试可能重复扣款、创建订单或扣减库存。
3. 分布式锁不等于绝对正确性。 锁可能过期，旧持有者可能在恢复后继续执行。
4. 最终一致性不等于数据一定正确。 消息丢失、补偿失败和错误的状态转换，都可能使系统无法收敛。
5. 组件能力不等于业务保证。 Redis、数据库、消息队列提供的是特定边界内的语义，业务系统必须把这些语义组合起来。

例如，订单服务调用支付服务，支付服务已扣款，但响应在网络中丢失。订单服务收到超时后不能直接认定支付失败，而应使用支付单号、幂等请求标识和支付结果查询机制确认最终结果。

---

## 二、CAP 定理：理解分区发生时的一致性取舍

CAP定理由加州大学Eric Brewer教授于2000年提出，后被麻省理工Lynch团队严格证明，是分布式系统的基础公理。

>
> 理论出处：《Towards Robust Distributed Systems》，Eric Brewer，2000

### 2.1 CAP 三个核心属性

| 缩写 | 英文全称                        | 含义                                                         |
| ---- | ------------------------------- | ------------------------------------------------------------ |
| C    | Consistency，一致性             | CAP 语境中主要指线性一致性（Linearizability），即所有节点在同一时间看到的数据完全一致；写操作成功后，所有节点的读操作都能返回最新值 |
| A    | Availability，可用性            | 每个到达非故障节点的请求最终都能获得非错误响应，不超时、不报错，不保证数据是最新版本 |
| P    | Partition Tolerance，分区容错性 | 当节点之间发生网络分区、无法正常互相通信时，系统仍能对外提供服务 |

CAP 定理的关键不是简单地说“三者不能同时拥有”，而是：
网络分区发生时，系统不能同时保证线性一致性与 CAP 定义下的可用性。
《Brewer's Conjecture and the Feasibility of Consistent, Available, Partition-Tolerant Web Services | CAP 定理形式化分析》：[https://dl.acm.org/doi/10.1145/564585.564601](https://dl.acm.org/doi/10.1145/564585.564601)

### 2.2 核心本质：“三选二”是常见误区

分布式系统天然存在网络分区（服务器跨机房部署、网络设备故障、链路抖动都是生产常态），因此**分区容错（P）是必须满足的前提**，不存在放弃P的选项。实际工程中只能在「C和A之间二选一」：

- **CP系统**：保证强一致性，网络分区发生时，为了避免数据不一致，拒绝部分请求，牺牲可用性。典型代表：ZooKeeper、Etcd、分布式数据库强一致模式。
- **AP系统**：保证可用性，网络分区发生时各节点仍可独立提供服务，数据可能短暂不一致，分区恢复后最终同步。典型代表：Nacos AP模式、大多数互联网业务系统、Eureka。

### 2.3 用账户余额理解 CAP

假设分布式系统有两个节点，A 和 B。两个节点最初都知道账户余额是 100 元。
网络突然分区，A 与 B 无法通信。

- 如果 A 允许余额扣减，B 也允许独立扣减，双方都继续成功处理请求，那么系统可能产生无法直接协调的冲突状态。
- 如果系统需要防止分区期间出现互相矛盾的余额，节点可能必须拒绝部分请求、等待协调，或者限制只能由一侧继续处理。

这就是 CAP 约束在工程中的体现。

| 策略倾向 | 分区期间的行为                                       | 更关注什么       |
| -------- | ---------------------------------------------------- | ---------------- |
| CP       | 无法确认操作符合一致性要求时，拒绝部分请求或等待协调 | 数据一致性       |
| AP       | 允许分区两侧继续处理部分请求，可能暂时观察到不同数据 | 可用性与后续收敛 |

需要注意，CP 和 AP 描述的是特定一致性与可用性保证的取舍，不代表系统所有功能永远只有一种模式。例如，订单支付确认可能采取严格的条件校验，而商品浏览量可以允许异步收敛。

### 2.4 中高级工程师必须纠正的误区

1. ❌ 误区：CAP三个属性可以任意选两个。✅ 正解：P是分布式系统的前提，只能在C和A之间权衡。
2. ❌ 误区：一致性就是数据完全不能不一样。✅ 正解：CAP中的一致性是**线性一致性（强一致）**，工程中可以降级为最终一致性、会话一致性等不同等级。
3. ❌ 误区：AP系统没有一致性保障。✅ 正解：AP系统放弃的只是强一致性，仍然保证最终一致性，数据会在时间窗口内达到一致。
4. ❌ 误区：A 代表 SLA 达到 99.99%。✅ 正解：CAP 中的 Availability 有特定理论定义，不能与业务可用率、错误率和 SLA 直接画等号。
5. ❌ 误区：数据库强一致就代表整个业务强一致。✅ 正解：即便数据库本身提供了强一致的单笔事务，服务之间依然可能出现订单已提交、消息未发送、缓存未失效、支付结果尚未同步等问题。

### 2.5 进一步理解 PACELC

PACELC 是对 CAP 思考方式的补充：

- P（Partition）：发生网络分区时，在一致性和可用性之间取舍。
- E（Else）：没有发生分区时，仍需要在延迟（Latency）与一致性（Consistency）之间取舍。

例如，跨多个节点协调强一致读取可能需要等待远端节点、仲裁或额外的同步步骤，即便系统没有发生故障，也可能增加延迟。
工程实践中，应该为不同业务定义一致性目标，而不是笼统地把整个系统贴上 CP 或 AP 标签。

---

## 三、BASE 理论：如何设计可控的最终一致性

BASE是CAP定理的工程化实践方案，由eBay架构团队提出，核心思想是**不必追求强一致性，通过适当放宽一致性要求，获得系统的高可用性与高性能**，是绝大多数互联网业务的设计准则。

>
> 理论出处：《Base: An Acid Alternative》，eBay技术团队，2008

### 3.1 BASE 的三大核心要素

| 缩写 | 英文全称              | 工程含义                                                     |
| ---- | --------------------- | ------------------------------------------------------------ |
| BA   | Basically Available   | **基本可用**：系统出现故障时，允许损失部分可用性（比如功能降级、接口限流、服务裁剪），保证核心功能可用，而非整体服务不可用。 |
| S    | Soft State            | **软状态**：允许系统存在中间状态，数据同步过程中存在短暂的不一致，该状态不影响系统整体可用性。 |
| E    | Eventually Consistent | **最终一致性**：数据不需要实时一致，经过一定时间窗口后，所有节点的数据最终会达到一致状态。 |

BASE 是用于指导分布式业务设计的架构思想，并不是与 CAP 同类的形式化定理。它是CAP中AP方案的延伸，通过牺牲强一致性换取高可用与高性能，同时通过最终一致性保证业务数据的正确性，是互联网业务的主流设计思想。
《BASE: An ACID Alternative | BASE 理论与 ACID 的取舍》：[https://queue.acm.org/detail.cfm?id=1394128](https://queue.acm.org/detail.cfm?id=1394128)

### 3.2 BASE 与 ACID 的区别

ACID 是数据库事务的四个属性：

- Atomicity：原子性。
- Consistency：一致性。
- Isolation：隔离性。
- Durability：持久性。

BASE 与 ACID 并非简单的替代关系。

- 本地 MySQL 事务依然可以使用 ACID。
- 跨服务业务流程可以通过事件、补偿和状态机实现最终一致性。
- 系统可以同时使用本地事务与跨服务的异步协调。

以创建订单为例：

```
创建订单：PENDING
      ↓
发布订单事件
      ↓
预留库存
      ↓
完成支付
      ↓
确认订单：CONFIRMED
```

如果预留库存或支付失败，订单可能转为 `FAILED` 或 `CANCELLED`，并触发库存释放或其他补偿动作。
此处的 `PENDING` 是明确建模的中间状态。只要状态机设计正确、异常能够恢复、关键动作能够对账，短暂处于中间状态并不必然意味着系统出现错误。

### 3.3 最终一致性必须具备哪些机制？

不能仅仅通过“发送一条消息”实现可靠的最终一致性。
至少要考虑：

1. 可靠记录变更：不能在数据库更新成功后永久丢失待发布事件。
2. 幂等消费：重复收到事件不能重复产生业务副作用。
3. 可恢复重试：区分暂时性错误和永久性业务错误。
4. 明确状态机：例如 `PENDING`、`PROCESSING`、`SUCCESS`、`FAILED`。
5. 超时处理：业务中间状态不能无限期悬挂。
6. 补偿与对账：发现无法自动恢复的异常时，应有告警、修复和审计机制。

记忆简语：BASE 接受可控的不一致，但不能接受无人处理的永久不一致。

---

## 四、分布式事务方案对比与选型

分布式事务常发生在一个业务动作涉及多个数据库、服务或消息系统的场景。选型时不能只比较一致性，还要比较锁持有时间、业务侵入性、吞吐量、恢复复杂度与组件依赖。

根据一致性强度可分为**刚性事务（强一致）**和**柔性事务（最终一致）**两大类，共7种主流落地方案，覆盖不同业务场景。

### 4.1 主流方案总对比

| 方案                          | 类型     | 核心原理                                                     | 一致性等级                     | 性能                         | 实现复杂度                | 业务侵入性           | 典型适用场景                                   |
| ----------------------------- | -------- | ------------------------------------------------------------ | ------------------------------ | ---------------------------- | ------------------------- | -------------------- | ---------------------------------------------- |
| **2PC（两阶段提交）**         | 刚性事务 | 基于XA协议，分Prepare（预提交）、Commit（提交）两阶段；事务协调者统一调度所有参与者 | 强一致（线性一致）             | 差，同步阻塞，资源锁定时间长 | 低，数据库原生支持        | 低，业务代码无侵入   | 银行、支付核心等强一致要求场景；并发量不高     |
| **3PC（三阶段提交）**         | 刚性事务 | 在2PC基础上增加CanCommit阶段，拆分阻塞阶段，引入超时机制     | 强一致（极端场景仍可能不一致） | 较差，比2PC略好              | 中                        | 低                   | 理论方案，生产环境极少使用                     |
| **TCC（Try-Confirm-Cancel）** | 柔性事务 | 业务层面两阶段：Try预留资源，Confirm确认提交，Cancel回滚补偿 | 最终一致（原子性保证）         | 高，无长锁                   | 高，每个业务需实现3个接口 | 极高，业务侵入强     | 金融、库存等需要资源预留的场景；短事务         |
| **SAGA 长事务**               | 柔性事务 | 长事务拆分为多个本地事务，按顺序执行；失败则反向执行补偿操作 | 最终一致（无隔离性，可能脏读） | 高                           | 中                        | 中，需编写补偿逻辑   | 链路长、周期长的分布式事务，如订单、物流全链路 |
| **本地消息表（Outbox）**      | 柔性事务 | 本地事务同时写入业务数据和消息数据；定时任务扫描消息表投递消息 | 最终一致                       | 高                           | 低                        | 中，消息表与业务耦合 | 中小规模系统，无专业消息中间件场景             |
| **可靠消息队列**              | 柔性事务 | 基于消息中间件的半消息机制；本地事务执行成功后确认消息，消费者消费执行下游 | 最终一致                       | 高                           | 中                        | 低，业务侵入小       | 互联网主流方案，适合大多数异步解耦场景         |
| **最大努力通知**              | 柔性事务 | 上游服务尽最大努力通知下游，下游可主动回调查询结果；允许通知失败 | 最终一致（弱保证）             | 最高                         | 低                        | 低                   | 非核心链路通知，如支付结果通知、站内信         |

>
> Java生态主流分布式事务框架为**Apache Seata**，原生支持2PC、TCC、SAGA、AT（自动代理）四种模式，与SpringBoot生态深度集成。
> 《What Is Seata? | Seata 分布式事务概览》：[https://seata.apache.org/docs/overview/what-is-seata/](https://seata.apache.org/docs/overview/what-is-seata/)

### 4.2 2PC / XA：两阶段提交

2PC 全称 Two-Phase Commit，即两阶段提交。XA 是分布式事务中常见的资源协调接口标准。

```
协调器
  │
  ├── 第一阶段：Prepare
  │      ├── 数据库 A 准备事务
  │      └── 数据库 B 准备事务
  │
  └── 第二阶段：Commit / Rollback
         ├── 数据库 A 执行协调决策
         └── 数据库 B 执行协调决策
```

优点： 对支持 XA 的事务资源，可以让多个参与者依据协调器决策完成事务。
主要代价：

- 协调器必须持久化决策状态，并具备故障恢复机制。
- 参与者在准备阶段可能保留锁或其他资源。
- 协调器故障、网络分区或参与者异常可能导致事务暂时无法完成。
- 并不是所有数据库、消息系统和外部 API 都支持 XA。

适用场景：多个事务资源都支持 XA，而且确有全局事务需求、可接受相应成本的系统。
不适用场景：希望把整个长时间微服务调用链无差别地包进一个全局事务。

### 4.3 3PC：三阶段提交

3PC是对2PC的理论优化方案，在2PC基础上新增CanCommit预询问阶段，拆分阻塞窗口，引入超时机制，一定程度上缓解了2PC的同步阻塞问题。
但该方案实现复杂度高，且极端网络分区场景下仍可能出现数据不一致，生产环境极少使用，仅作为理论参考。

### 4.4 TCC：Try、Confirm、Cancel

TCC 是一种业务层分布式事务模式。

- Try：检查条件并预留资源。
- Confirm：确认执行，完成预留资源的业务变更。
- Cancel：释放预留资源，撤销 Try 阶段产生的影响。

例如库存预留：

```
Try：预留 2 件库存
Confirm：预留库存转为已售库存
Cancel：释放已预留的 2 件库存
```

TCC 需要重点处理三类经典问题：

| 问题   | 含义                                     | 防护方式                                     |
| ------ | ---------------------------------------- | -------------------------------------------- |
| 幂等   | 同一 Confirm 或 Cancel 可能被重复调用    | 事务 ID、状态机、唯一约束和条件更新          |
| 空回滚 | Try 未成功执行，Cancel 却先到达          | 记录事务状态，避免重复释放不存在的资源       |
| 悬挂   | Cancel 已完成，延迟到达的 Try 又创建预留 | 通过事务状态检查，阻止终态之后再产生有效预留 |

适用场景： 库存、额度、名额等能够预留并确认或释放的业务。
主要代价： 业务层需要实现三个阶段及异常处理，开发和测试成本较高。
《TCC Mode | Seata TCC 模式与异常处理》：[https://seata.apache.org/docs/user/mode/tcc/](https://seata.apache.org/docs/user/mode/tcc/)

### 4.5 Saga：适合长流程业务

Saga 将跨服务操作拆解为一系列本地事务。

```
创建订单
   ↓
预留库存
   ↓
完成支付
   ↓
确认订单
```

如果支付失败，Saga 会执行库存释放、订单取消等补偿操作。

Saga 有两种常见协作方式：

- 编排式（Orchestration）：由协调器决定下一步操作。
- 协同式（Choreography）：服务根据事件执行后续业务。

适用场景：订单处理、预订、物流协调等持续时间较长、可以接受明确业务中间态的流程。
主要代价：

- 补偿不是恢复数据库历史快照。
- 已经发出的邮件、实际发生的物流动作或外部支付，不一定能被原样撤销。
- Saga 不天然提供全局隔离性，需要通过状态校验、版本控制、资源预留等机制保护业务不变量。
- 必须解决超时、补偿失败、重复命令与长期悬挂等问题。
《Saga Pattern | Saga 长事务模式》：[https://microservices.io/patterns/data/saga.html](https://microservices.io/patterns/data/saga.html)

### 4.6 Seata AT：常见的 Java 微服务方案

Seata AT 采用本地事务、回滚日志和全局事务协调机制完成跨服务数据协调。
核心过程可以概括为：

1. 通过数据源代理执行本地 SQL。
2. 同一阶段记录业务数据及回滚所需信息。
3. 协调器管理全局事务与分支事务状态。
4. 全局提交时完成后续协调；全局回滚时利用回滚信息执行反向恢复。
《AT Mode | Seata AT 模式机制》：[https://seata.apache.org/docs/user/mode/at/](https://seata.apache.org/docs/user/mode/at/)

需要特别关注：

- 当前数据库与 SQL 是否在兼容范围内。
- 数据源代理和事务传播是否正确配置。
- 全局锁竞争如何影响并发性能。
- 协调服务不可用、事务超时和回滚失败如何恢复。
- 不要假设所有查询都会受到相同的全局隔离保护。
Seata AT 并不是把 `@GlobalTransactional` 加到方法上，就能自动使整个跨服务业务具备本地事务一样的语义。

### 4.7 Transactional Outbox：解决数据库与消息双写

假设订单服务先写数据库，再发消息：

```
1. MySQL 创建订单并提交。
2. 向消息队列发布 OrderCreated。
```

如果数据库已提交，但发布失败，下游可能永远不知道订单已创建。
反过来，如果消息先发布而数据库事务随后回滚，下游可能收到无效事件。

Outbox 的解决方案是：在同一个本地数据库事务中完成业务数据写入与待发布事件记录。

```
BEGIN;
INSERT INTO orders (order_id, status)
VALUES ('ORD-1001', 'PENDING');
INSERT INTO outbox_event (
    event_id,
    aggregate_id,
    event_type,
    status
)
VALUES (
    'EVT-1001',
    'ORD-1001',
    'OrderCreated',
    'NEW'
);
COMMIT;
```

这是简化的 MySQL 风格示例，正式表结构还需考虑事件内容、创建时间、发送次数、下次重试时间、索引与清理策略。

后续发布器或 CDC 读取事件并发布至消息系统。需要注意，发布器可能在消息已发送但发送状态尚未更新时崩溃，所以 Outbox 并不自动保证消息只发布一次；消费者必须具备幂等处理能力。
《Transactional Outbox Pattern | 事务 Outbox 模式》：[https://microservices.io/patterns/data/transactional-outbox.html](https://microservices.io/patterns/data/transactional-outbox.html)

### 4.8 事务方案的实际选型顺序

建议按以下优先级判断：

1. 能否通过单库本地事务解决？能，则优先单库事务。
2. 是否必须让多个 XA 资源参与统一事务决策？若是，再评估 2PC/XA。
3. 是否可以业务预留、确认或取消？若是，可以评估 TCC。
4. 是否属于允许中间态的长业务流程？若是，可以评估 Saga。
5. 是否主要为了解决“数据库提交与消息发布”双写？重点评估 Outbox 或具备明确事务能力的消息方案。
6. 若选择 Seata AT 等框架，还必须验证数据库兼容性、隔离语义、锁竞争和异常恢复能力。

工程原则：事务边界越复杂，故障恢复与验证成本通常越高；不要为了架构形式而过度引入全局事务。

---

## 五、Redis 分布式锁：原理、实现、边界与最佳实践

按你目前的学习技术栈，这部分以 Redis 6.2.12 和 Spring Data Redis 常用 API 为例。

### 5.1 为什么需要分布式锁？

Java 的 `synchronized` 和 `ReentrantLock` 用于协调同一个 JVM 中的线程。如果有多个服务实例，它们各自拥有独立的 JVM 锁，因此不能单靠本地锁阻止不同实例同时操作同一个业务资源。
例如：

```
实例 A ───┐
          ├── 同时尝试生成同一订单的结算结果
实例 B ───┘
```

若两个实例需要对相同业务资源进行互斥协调，可以考虑分布式锁。
但应先问：是否可以直接通过数据库唯一约束、乐观锁或者条件更新解决？如果可以，这些方案有时比引入分布式锁更简单、可靠。

### 5.2 分布式锁至少需要哪些保证？

| 属性       | 英文                   | 含义                                                         |
| ---------- | ---------------------- | ------------------------------------------------------------ |
| 互斥性     | Mutual Exclusion       | 在设定的有效期与故障模型内，避免多个持有者同时执行受保护操作 |
| 活性       | Liveness               | 持有者退出、崩溃或租约到期之后，其他请求最终有机会取得锁     |
| 故障容忍   | Fault Tolerance        | Redis 节点、网络或客户端出现故障时，系统能按设计处理，而不是无限期卡住 |
| 所有权校验 | Ownership Verification | 只有当前持有者才能续租或释放自己对应的锁                     |
| 业务正确性 | Business Correctness   | 即使锁失效或持有者暂停，业务关键约束仍受到保护               |

最后一项最容易被忽视。锁协议正确不代表外部数据库或业务副作用一定正确。
《Distributed Locks with Redis | Redis 分布式锁设计》：[https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/](https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/)

### 5.3 原生实现原理：`SET NX PX`

Redis 单实例锁常见的基本命令，基于`SETNX`（SET if Not eXists）原语实现：key不存在时才能设置成功，代表获取锁；key存在时设置失败，代表获取锁失败。同时必须设置过期时间，避免服务宕机导致锁永久无法释放。

```
SET lock:order:10001 <unique-token> NX PX 3000
```

各部分含义：

- `lock:order:10001`：锁的业务键。
- `<unique-token>`：本次持有者的唯一标识。
- `NX`：只有当键不存在时才设置成功。
- `PX 3000`：设置 30 秒有效期。

成功时 Redis 返回 `OK`；未获取到锁时通常返回空值。

必须同时设置互斥条件和过期时间。
错误方式是：

```
先 SETNX
再 EXPIRE
```

如果两条命令之间客户端崩溃，就可能留下没有过期时间的锁。
正确方式是通过一个原子命令完成加锁与过期时间设置。
《SET Command | Redis SET 原子写入及过期参数》：[https://redis.io/docs/latest/commands/set/](https://redis.io/docs/latest/commands/set/)

### 5.4 原生分布式锁三大经典问题

原生Redis锁实现存在三个经典的工程缺陷，生产环境使用必须针对性解决：

1. **不可重入**：同一个线程不能多次获取同一把锁，重复加锁会直接失败。
2. **锁续期**：业务执行时间超过锁过期时间，锁提前释放，导致并发问题。
3. **误删除**：线程A的锁过期后，线程B拿到锁，线程A执行完误删线程B的锁。

### 5.5 生产级标准实现：Redisson

Redisson是Java生态最成熟的Redis分布式锁实现，完整解决了原生锁的三大问题，是生产环境的标准方案：

- **可重入**：通过ThreadLocal维护重入计数器，加锁时计数递增，释放时计数递减，计数为0才真正释放锁。
- **看门狗机制**：加锁成功后启动后台守护线程，默认每10秒续期一次，锁默认30秒过期，业务执行期间锁不会提前过期。
- **原子释放**：通过Lua脚本校验锁持有者，只有锁的持有者才能删除锁，避免误删。

### 5.6 正确释放锁：比较令牌后原子删除

为什么锁需要唯一令牌？
假设：

1. 客户端 A 获取了锁，令牌是 `token-A`。
2. A 长时间暂停，锁过期。
3. 客户端 B 获取同一把锁，令牌是 `token-B`。
4. A 恢复，开始尝试释放锁。
如果 A 直接执行 `DEL lock:order:10001`，就可能误删 B 的锁。

因此，锁的 value 应包含本次持有者的唯一令牌。释放时先比较令牌，只允许删除仍属于自己的锁。
重要区分：唯一令牌只用来验证锁的所有权，不等于严格递增的 Fencing Token。

Redis 6.2.12 可以使用 Lua 脚本执行比较与删除。

```
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
end
return 0
```

执行逻辑是：

```
读取锁当前令牌
     ↓
与调用者令牌比较
     ↓
相同：删除锁
不同：不做任何修改
```

这必须作为一个原子操作执行，不能分成 Java 先 `GET`、再 `DEL` 两个独立请求，因为两个请求之间状态可能变化。

Redis 保证 Lua 脚本原子执行；脚本运行期间，其他命令不会交错执行，因此应确保脚本短小。
《Scripting with Lua | Redis Lua 脚本原子执行》：[https://redis.io/docs/latest/develop/programmability/eval-intro/](https://redis.io/docs/latest/develop/programmability/eval-intro/)

>
> 版本注意：Redis 官方文档指出，`DELEX` 命令是在 Redis 8.4 引入的；Redis 6.2.12 不应使用该命令，应采用上述 Lua 比较删除方式。

### 5.7 Spring Boot 示例：封装 Redis 分布式锁

下面示例展示单 Redis 实例场景下，基本的加锁与安全释放。它不是完整的高可用分布式锁框架。

```
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisDistributedLock {
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        return redis.call('del', KEYS[1])
                    end
                    return 0
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisDistributedLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试获取锁。
     *
     * @return 成功时返回本次持有者令牌；获取失败返回 null。
     */
    public String tryLock(String key, Duration leaseTime) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (leaseTime == null
                || leaseTime.isNegative()
                || leaseTime.isZero()
                || leaseTime.toMillis() <= 0) {
            throw new IllegalArgumentException(
                    "leaseTime must be positive and at least 1 ms");
        }
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, token, leaseTime);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    /**
     * 仅允许当前令牌持有者释放锁。
     *
     * @return true 表示本次调用删除了锁；false 表示锁不存在或令牌不匹配。
     */
    public boolean unlock(String key, String token) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        Long result = redisTemplate.execute(
                RELEASE_SCRIPT,
                List.of(key),
                token
        );
        return Long.valueOf(1L).equals(result);
    }
}
```

Spring Data Redis 提供 `setIfAbsent(key, value, Duration)` 形式的 API，以及通过 `RedisTemplate.execute` 执行 `RedisScript` 的能力。
《ValueOperations | Spring Data Redis 条件写入 API》：[https://docs.spring.io/spring-data/redis/reference/api/java/org/springframework/data/redis/core/ValueOperations.html](https://docs.spring.io/spring-data/redis/reference/api/java/org/springframework/data/redis/core/ValueOperations.html)
《Scripting | Spring Data Redis 脚本执行机制》：[https://docs.spring.io/spring-data/redis/reference/redis/scripting.html](https://docs.spring.io/spring-data/redis/reference/redis/scripting.html)

#### 示例用法

```
String key = "lock:order:" + orderId;
String token = lock.tryLock(key, Duration.ofSeconds(30));
if (token == null) {
    // 未获取到锁：按业务需要等待、返回忙碌状态或走其他路径。
    return;
}
try {
    // 执行受锁保护的业务逻辑。
    // 关键业务仍应有数据库约束、条件更新或幂等机制。
} finally {
    lock.unlock(key, token);
}
```

这段调用展示了基本使用方式，生产环境还应完善异常和观测处理：如果解锁请求因网络异常而失败，应记录并告警，而不能假定锁已经释放。租约最终会到期，但这一过程不应成为业务正确性的唯一保障。

此外，如果获取锁请求发生网络超时，客户端有时无法确定 Redis 是否已成功加锁。这属于结果不确定情形，不应把网络异常当作明确的“未获取锁”；受保护操作不应在未确认获取成功时直接执行。

### 5.8 红锁（RedLock）：原理与争议

- **原理**：跨N个独立Redis节点加锁，超过半数节点加锁成功才算获取成功；释放时所有节点都释放。解决单节点Redis故障导致的锁丢失问题。
- **争议**：业界对其可靠性存在争议，极端场景下（时钟漂移、节点重启）仍可能出现锁安全问题。
- **生产建议**：绝大多数业务场景单节点Redis锁+看门狗足够；金融级高可靠场景优先选ZooKeeper分布式锁。

### 5.9 Redis vs ZooKeeper 分布式锁对比

| 维度     | Redis 分布式锁                       | ZooKeeper 分布式锁                               |
| -------- | ------------------------------------ | ------------------------------------------------ |
| 实现原理 | SETNX + 过期时间；Lua脚本保证原子性  | 临时顺序节点；最小序号节点获得锁，节点删除释放锁 |
| 可靠性   | 一般，主从切换可能丢锁               | 高，临时节点与会话绑定，断开自动释放             |
| 性能     | 极高，纯内存操作                     | 一般，节点创建删除有开销                         |
| 可重入性 | 需自行实现（Redisson内置）           | 原生支持，基于会话                               |
| 阻塞等待 | 需轮询实现                           | 原生支持Watcher监听，唤醒式等待                  |
| 适用场景 | 高并发、性能要求高、允许极低概率异常 | 高可靠、一致性要求高、并发量中等                 |

### 5.10 Redis 分布式锁最关键的五个问题

#### 问题一：为什么一定要设置 TTL？

如果持锁进程崩溃而锁没有过期时间，锁可能永远无法释放。
TTL（Time To Live，生存时间）可以使锁在一定时间后自动过期。
但 TTL 同时意味着：锁只在规定的有效期内有效，不能简单理解为持有者的代码执行完毕之前，锁永远有效。

#### 问题二：TTL 过短有什么风险？

例如锁的租约是 10 秒，而任务执行需要 15 秒。

```
0 秒：A 获取锁
10 秒：A 的锁到期
11 秒：B 获取相同的锁
12 秒：A 仍在执行原任务
```

这时 A 和 B 可能同时执行受保护的业务操作。
因此，租约需要结合任务时长分布、网络延迟、线程暂停、GC 暂停以及失败恢复机制设计，而不是随便设置为 5 秒或 30 秒。

#### 问题三：续租能彻底解决租约到期吗？

不能。
续租可以减少任务执行期间租约自然到期的概率，但如果客户端长时间暂停、网络中断或续租失败，租约仍可能过期。
续租脚本也必须原子检查令牌，只有当前令牌仍然匹配时才能更新 TTL。
不能只因为“使用了 Redisson 看门狗”或其他自动续租机制，就认为旧持有者永远不可能在失去锁之后继续操作。

#### 问题四：Redis 主从切换会不会造成两个持有者？

可能，取决于部署方式与故障时序。
Redis 使用异步复制时，主节点可能在锁写入复制到从节点之前故障。随后从节点被提升为主节点，新的客户端便可能在新主节点上获取同名锁，而原客户端仍认为自己持有锁。
《Replication | Redis 主从异步复制与写入安全》：[https://redis.io/docs/latest/operate/oss_and_stack/management/replication/](https://redis.io/docs/latest/operate/oss_and_stack/management/replication/)

因此，单实例下正确的 `SET NX PX` 加锁与 Lua 释放，解决的是基本互斥和所有权释放问题；它们本身并不保证在所有主从切换故障下仍具有严格互斥性。

#### 问题五：Redlock 一定比单 Redis 锁安全到足以保护资金吗？

不应不加条件地作出这种承诺。
Redis 官方文档提出了 Redlock 算法：通过多个独立 Redis 主实例获取多数锁，并将获取耗时从总有效期中扣除，来构建更强的租约协议。但其保证依赖特定的时钟、故障和时间边界假设。官方文档也提醒需要重视 Fencing Token、进程暂停与时钟变化等问题。
《Redlock Algorithm | Redis Redlock 多节点锁算法》：[https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/](https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/)

对于 Redlock 的安全性，还有学术与工程实践层面的讨论。Martin Kleppmann 对时间假设与 Fencing Token 提出过批评；在正确性高度敏感的场景，不能只凭 Redlock 获取成功就推导出所有情况下都不会有并发写入。
《How to Do Distributed Locking | 分布式锁的安全性与 Fencing Token》：[https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html)

### 5.11 Fencing Token：真正保护业务资源的关键

Fencing Token（栅栏令牌）通常是每次授予锁所有权时产生的严格递增编号。
例如：

```
持有者 A 获取令牌 41
A 暂停，锁租约到期
持有者 B 获取令牌 42
受保护资源接受 42
A 恢复后提交令牌 41 的写入
受保护资源拒绝 41，因为 41 < 42
```

这解决了一个更深层的问题：即使旧持有者在锁到期后恢复，目标资源也能够拒绝过期所有者的写入。

必须掌握三个条件：

1. 令牌由可信机制生成，并且对同一资源严格递增。
2. 每次业务写入都携带该令牌。
3. 目标资源本身校验令牌并拒绝旧令牌，不能只靠调用方自觉检查。

不要把 UUID 锁令牌当成 Fencing Token。 UUID 用于区分持有者，通常没有严格递增语义。
也不能未经验证就使用普通 Redis `INCR` 作为严格的全局递增令牌：若计数在故障切换时丢失或回退，就可能破坏令牌单调性。
对于高正确性要求的场景，应考虑支持共识与有序版本语义的协调系统，或直接在数据库中通过版本号和条件更新保护目标资源。
《ZooKeeper Recipes | ZooKeeper 顺序节点与分布式锁》：[https://zookeeper.apache.org/doc/r3.9.2/recipes.html](https://zookeeper.apache.org/doc/r3.9.2/recipes.html)

### 5.12 Redis 锁的工程选型建议

| 场景                           | 建议                                                         |
| ------------------------------ | ------------------------------------------------------------ |
| 缓存重建、减少重复计算         | Redis 单实例锁通常足够，重点是保证 TTL、令牌匹配释放和失败可恢复 |
| 多实例定时任务避免重复执行     | 可以评估 Redis 锁，但任务还应幂等，并设计执行状态和超时恢复  |
| 库存扣减                       | 优先考虑数据库条件更新、唯一约束与幂等机制；锁可以辅助协调，不能作为唯一保障 |
| 支付、资金和账户关键写入       | 不能仅依赖 Redis 锁；必须由权威数据资源承担最终正确性校验    |
| 严格互斥与旧持有者写入风险很高 | 评估共识型协调服务、Fencing Token 和资源端校验，并根据故障模型做设计验证 |

记忆简语：加锁要带 TTL，释放必须验令牌，续租要验所有权，关键写入要验版本。

---

## 六、Redis 锁之外，中高级工程师还必须掌握的分布式知识

### 6.1 分布式共识协议

共识协议解决「分布式系统中多个节点如何就某个提案达成一致」的问题，是分布式存储、注册中心、分布式锁的底层基石。

#### 三大主流协议对比

| 协议      | 核心思想                                                     | 特点                                     | 典型应用                          |
| --------- | ------------------------------------------------------------ | ---------------------------------------- | --------------------------------- |
| **Paxos** | 多角色（提议者、接受者、学习者），两阶段提交决议；少数派服从多数派 | 强一致、容错率高；理论复杂、实现难度极高 | Chubby、Google Spanner            |
| **Raft**  | Paxos的工程化简化版本；分为领导者选举、日志复制、安全性三个核心机制；单领导者模型 | 强一致、易理解、易实现、性能均衡         | Etcd、Consul、TiKV、Redis Cluster |
| **ZAB**   | ZooKeeper专属协议；基于崩溃恢复的原子广播，支持顺序一致性    | 强一致、顺序保证、可靠性高；性能一般     | ZooKeeper、HBase、Kafka调度       |

>
> Raft协议出处：《In Search of an Understandable Consensus Algorithm》，Diego Ongaro & John Ousterhout，2014

### 6.2 一致性哈希算法

一致性哈希是解决「分布式系统节点动态扩缩容时数据迁移量过大」的核心算法，广泛应用于分布式缓存、分布式存储、负载均衡。

- **核心原理**：构建0 ~ 2^32-1的哈希环，将服务器节点的哈希值映射到环上；数据的哈希值映射到环上，顺时针查找第一个节点存储。
- **核心优势**：节点数量变化时，只影响相邻的一个节点的数据，仅需迁移少量数据；普通哈希（对节点数取模）节点变化会导致全量数据迁移。
- **虚拟节点机制**：解决节点数量少时数据分布倾斜的问题；每个物理节点虚拟出多个虚拟节点映射到环上，大幅提升数据均衡性。

### 6.3 分布式ID生成方案

分布式ID是分布式系统的基础组件，核心要求：全局唯一性、趋势递增性、高性能、高可用、信息安全。

#### 主流方案对比

| 方案                      | 原理                                               | 优点                       | 缺点                               | 适用场景                         |
| ------------------------- | -------------------------------------------------- | -------------------------- | ---------------------------------- | -------------------------------- |
| UUID                      | 128位随机数                                        | 实现简单、无单点           | 无序、长度长、存储开销大、无递增性 | 非主键、链路追踪ID               |
| 数据库自增ID              | 单库自增主键                                       | 简单、有序                 | 单点故障、性能瓶颈、水平扩展难     | 小规模单库系统                   |
| 号段模式                  | 批量获取号段，本地消耗完再申请                     | 高性能、高可用、趋势递增   | 号段浪费、ID不连续                 | 互联网主流，美团Leaf、滴滴TinyId |
| **雪花算法（Snowflake）** | 64位结构：1位符号+41位时间戳+10位机器ID+12位序列号 | 高性能、趋势递增、无中心化 | 时钟回拨问题、机器ID分配           | 生产级首选，绝大多数大厂使用     |

选择 Snowflake 类算法时，必须考虑：

- Worker ID 冲突；
- 系统时钟回拨；
- 单时间片内序列号容量；
- 节点重启与容器重新调度；
- 高吞吐下的生成延迟。
还要区分事件时间、处理时间和业务版本顺序。不同机器的时间戳不是天然的全局因果顺序证明。

### 6.4 幂等性、唯一约束与乐观锁

三者解决的问题不同：

| 机制                      | 主要用途                         |
| ------------------------- | -------------------------------- |
| 幂等键（Idempotency Key） | 识别重复的业务请求或消息         |
| 数据库唯一约束            | 从数据库层防止重复的唯一业务记录 |
| 乐观锁 / 版本号           | 避免使用旧版本状态覆盖较新的状态 |
| 条件更新                  | 只有满足条件时才允许变更         |
| 分布式锁                  | 协调多个进程对共享资源的访问     |

例如库存扣减可以使用：

```
UPDATE inventory
SET available = available - 2
WHERE sku_id = ?
  AND available >= 2;
```

必须判断受影响行数：更新 1 行表示扣减成功，更新 0 行表示条件不满足。
如果同一个业务请求可能重试，还必须识别重复请求，避免再次扣减。条件更新解决库存不能低于阈值的问题，幂等机制则解决同一请求重复产生副作用的问题。

### 6.5 消息投递语义：At-most-once、At-least-once、Exactly-once

| 语义          | 含义                                     |
| ------------- | ---------------------------------------- |
| At-most-once  | 至多一次，可能丢失                       |
| At-least-once | 至少一次，可能重复投递                   |
| Exactly-once  | 恰好一次，但必须明确保证范围与参与的资源 |

消息中间件的 Exactly-once 一般不能直接推广为“整个业务链路只执行一次”。
例如 Kafka 的事务能力可以在一定的 Kafka 处理边界内协调输入位点和输出记录；当消费端还需要修改外部 MySQL、调用支付服务时，仍然需要幂等和事务设计。
《Message Delivery Semantics | Kafka 消息投递语义》：[https://kafka.apache.org/39/design/design/](https://kafka.apache.org/39/design/design/)

### 6.6 可靠消息需要消费者幂等

可靠消费者通常要做到：

1. 为业务事件设置稳定且唯一的 `eventId`。
2. 在数据库中为已处理事件设置唯一约束。
3. 将事件去重与本地业务修改尽量放在同一个数据库事务中。
4. 本地事务提交后，再按消息队列的消费协议确认消息。
5. 对持续失败的消息进行有限次重试、死信处理与人工修复。

不能在业务操作提交之前就无条件确认消息，否则消费者进程崩溃可能导致业务永久漏处理。
反之，如果业务已经提交但确认消息失败，重复消费也可能发生，因此幂等机制不可缺少。

### 6.7 分布式缓存一致性

Cache-Aside（旁置缓存）常见的写入流程是：先更新数据库，再使缓存失效。
即使按这个顺序执行，仍可能出现并发竞态：

```
A：读取数据库旧值 100
B：更新数据库为 200
B：删除缓存
A：将旧值 100 写回缓存
```

这说明缓存失效策略需要结合并发读写、过期时间、版本校验和业务一致性目标共同设计。
对于缓存重建，可用请求合并或互斥重建减少热点 Key 回源；对于关键业务数据，还要确认回源并发、连接池和下游容量是否受控。

### 6.8 限流核心算法

限流是分布式系统高可用防护的核心手段，防止突发流量冲垮系统。分布式场景下通常基于**Redis + Lua脚本**实现限流，保证多节点限流逻辑的原子性。

#### 四大主流算法对比

| 算法           | 原理                                         | 优点                             | 缺点                                     | 适用场景                         |
| -------------- | -------------------------------------------- | -------------------------------- | ---------------------------------------- | -------------------------------- |
| 固定计数器     | 单位时间内计数，超过阈值限流                 | 实现简单、性能高                 | 临界问题：时间窗口边界流量突增会击穿限流 | 要求不高的粗略限流               |
| **滑动窗口**   | 将时间窗口拆分为多个小格，滚动统计           | 解决临界问题，精度高             | 实现相对复杂，存储开销略大               | 精确限流场景                     |
| 漏桶算法       | 水以任意速率流入，以固定速率流出桶；水满溢出 | 平滑流量，输出速率恒定           | 无法应对突发流量，资源利用率低           | 流量整形、削峰填谷               |
| **令牌桶算法** | 固定速率向桶中放令牌，请求需获取令牌才能通过 | 平滑限流、支持突发流量、灵活性高 | 实现略复杂                               | 生产级首选，绝大多数业务限流场景 |

### 6.9 超时、重试、熔断与资源隔离

| 机制            | 解决的问题                               |
| --------------- | ---------------------------------------- |
| Timeout         | 限制请求等待时间                         |
| Retry           | 从可恢复的临时故障中恢复                 |
| Circuit Breaker | 故障率升高时暂时停止调用故障依赖         |
| Bulkhead        | 将线程、连接等资源隔离，限制故障扩散     |
| Rate Limiting   | 限制请求进入速率                         |
| Backpressure    | 下游处理能力不足时，控制上游继续产生任务 |

#### 熔断器状态机

熔断降级是解决「服务雪崩」的核心机制，属于系统的容错防护体系。

- **服务雪崩原理**：分布式系统中，下游服务故障导致上游服务调用失败，故障沿调用链向上传导，最终导致整个链路瘫痪。
- 熔断器三个状态：
  1. **关闭状态**：正常状态，请求正常通过；统计失败率，达到阈值则切换到打开状态。
  2. **打开状态**：熔断状态，请求直接降级，不调用下游；经过冷却时间后进入半开状态。
  3. **半开状态**：试探状态，放行少量请求；成功则关闭熔断器，失败则回到打开状态。

重试需要有界：设置最大尝试次数、总截止时间、指数退避与随机抖动；对可能产生副作用的请求还需要幂等。
如果多个调用层级各自重试，实际请求量可能倍增，造成重试风暴。
《Timeouts, Retries, and Backoff with Jitter | 超时、重试与随机抖动退避》：[https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/](https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/)

### 6.10 分库分表与全局唯一性

当系统开始水平拆分数据，需要重新考虑：

- 分片键如何选择、是否产生热点；
- 全局唯一 ID 如何生成；
- 跨分片查询和分页如何合并；
- 跨分片事务是否必要；
- 数据迁移与重新分片怎样保持业务可用；
- 读写分离期间是否需要处理复制延迟；
- 数据源切换时，已确认事务是否可能丢失或出现旧读。

分库分表不仅是扩容问题，也会改变事务、查询、唯一约束和运维的复杂度。

### 6.11 分布式可观测性

分布式故障不能只靠一个服务的错误堆栈定位。
应关注三类信号：

- Metrics（指标）：请求延迟、错误率、线程池队列长度、数据库连接池、消息积压。
- Logs（日志）：请求 ID、业务单号、事件 ID、状态迁移、重试次数和补偿结果。
- Traces（分布式追踪）：请求跨服务的调用路径、各阶段耗时和失败位置。
《OpenTelemetry Java | Java 可观测性官方文档》：[https://opentelemetry.io/docs/languages/java/](https://opentelemetry.io/docs/languages/java/)

---

## 七、知识体系与实战验收

### 7.1 面试高频考点

#### 面试八股必背

1. **CAP定理的理解？为什么不能同时满足CAP？**

>
> 答题要点：三大属性定义；P是分布式前提，只能C和A二选一；分区发生时，同步数据会导致不可用，保证可用会导致数据不一致。
2. **分布式事务有哪些方案？怎么选型？**

>
> 答题要点：刚性（2PC）、柔性（TCC、SAGA、可靠消息等）；强一致选2PC，资源预留选TCC，长事务选SAGA，异步解耦选可靠消息。
3. **Redis分布式锁的实现原理？有什么问题？怎么解决？**

>
> 答题要点：SETNX加锁+过期时间；核心问题是不可重入、续期、误删；用Redisson的可重入计数、看门狗续期、Lua原子释放解决。
4. **一致性哈希的原理？虚拟节点的作用？**

>
> 答题要点：哈希环映射，顺时针找节点；虚拟节点解决数据倾斜，提升均衡性。
5. **常见限流算法有哪些？生产级首选哪种？**

>
> 答题要点：固定计数器、滑动窗口、漏桶、令牌桶；生产首选令牌桶，支持突发流量，精度高。

#### 工程避坑红线

1. 不要盲目追求强一致性，绝大多数业务场景最终一致性足够，强一致会大幅降低系统可用性与性能。
2. 分布式锁必须设置过期时间+续期机制，避免死锁与锁提前释放。
3. 雪花算法必须处理时钟回拨，否则生产环境必然出现重复ID。
4. 熔断器必须配置合理的阈值与冷却时间，避免频繁切换状态导致系统抖动。

### 7.2 记忆简语

>
> CAP三属性，分区是前提，一致二选一；BASE做妥协，最终一致换可用。
> 事务分刚柔，二PC强一致慢，可靠消息解耦高，长事务用SAGA。
> Redis锁高性能，SETNX加过期，三大问题看门狗，Lua释放保原子。
> 共识三协议，Paxos难Raft简，ZAB用在ZooKeeper。
> 一致性哈希减迁移，虚拟节点解倾斜；雪花ID防回拨，限流令牌桶，熔断三状态。

### 7.3 学习优先级建议

建议按以下顺序学习，不必一开始就追求背熟所有架构模式。

第一阶段：分布式基础
CAP、BASE、一致性模型、ACID、网络超时、节点故障、主从复制与故障切换。

第二阶段：跨服务数据正确性
本地事务、XA、TCC、Saga、Seata AT、Outbox、消息幂等、补偿和对账。

第三阶段：Redis 分布式锁
SET NX PX、唯一令牌、Lua 比较删除、租约续期、主从切换风险、Fencing Token、资源端校验。

第四阶段：生产可靠性
共识协议、一致性哈希、分布式ID、超时预算、重试与退避、熔断、限流、资源隔离、分库分表、可观测性和故障恢复。

### 7.4 随堂抽测

1. CAP定理中为什么说分区容错是必须的？实际生产中CP和AP分别适合什么业务场景？
2. Redis分布式锁的三大经典问题是什么？Redisson是如何解决的？
3. TCC和SAGA两种柔性事务方案的核心区别是什么？分别适合什么场景？

### 7.5 分布式系统自检清单

建议只有在能够结合真实场景解释和实现时，才勾选为掌握。

#### 理论基础

- 准确解释 CAP 以及分区时的 C/A 取舍
- 区分 BASE 与 ACID
- 解释线性一致性、最终一致性与复制延迟
- 解释超时为何不等于远程操作失败

#### 事务与消息

- 比较 2PC/XA、TCC、Saga、Seata AT 和 Outbox
- 说明空回滚、悬挂、幂等分别是什么
- 设计 Outbox 与幂等消费者
- 设计补偿、死信、超时恢复和对账流程

#### Redis 分布式锁

- 使用 SET NX PX 原子获取锁
- 实现 Lua 比较令牌后释放锁
- 解释 TTL 到期后的旧持有者风险
- 区分唯一令牌与 Fencing Token
- 解释 Redis 异步复制导致的锁安全风险
- 判断哪些场景可以用 Redis 锁，哪些场景应使用数据库约束或更强协调

#### 生产工程

- 设计有界超时、重试与退避机制
- 解释分库分表对唯一约束与跨分片事务的影响
- 监控消息积压、连接池、线程池和关键业务状态
- 通过日志、指标与 Trace 定位跨服务故障

---

## 参考文献

[1] ACM. Brewer's Conjecture and the Feasibility of Consistent, Available, Partition-Tolerant Web Services | CAP 定理形式化分析. [https://dl.acm.org/doi/10.1145/564585.564601](https://dl.acm.org/doi/10.1145/564585.564601)
[2] ACM Queue. BASE: An ACID Alternative | BASE 理论与 ACID 的取舍. [https://queue.acm.org/detail.cfm?id=1394128](https://queue.acm.org/detail.cfm?id=1394128)
[3] Brewer E. Towards Robust Distributed Systems. 2000.
[4] Pritchett D. Base: An Acid Alternative. 2008.
[5] Ongaro D, Ousterhout J. In Search of an Understandable Consensus Algorithm. 2014.
[6] Apache Seata. What Is Seata? | Seata 分布式事务概览. [https://seata.apache.org/docs/overview/what-is-seata/](https://seata.apache.org/docs/overview/what-is-seata/)
[7] Apache Seata. TCC Mode | Seata TCC 模式与异常处理. [https://seata.apache.org/docs/user/mode/tcc/](https://seata.apache.org/docs/user/mode/tcc/)
[8] Apache Seata. AT Mode | Seata AT 模式机制. [https://seata.apache.org/docs/user/mode/at/](https://seata.apache.org/docs/user/mode/at/)
[9] microservices.io. Saga Pattern | Saga 长事务模式. [https://microservices.io/patterns/data/saga.html](https://microservices.io/patterns/data/saga.html)
[10] microservices.io. Transactional Outbox Pattern | 事务 Outbox 模式. [https://microservices.io/patterns/data/transactional-outbox.html](https://microservices.io/patterns/data/transactional-outbox.html)
[11] Apache Kafka. Message Delivery Semantics | Kafka 消息投递语义. [https://kafka.apache.org/39/design/design/](https://kafka.apache.org/39/design/design/)
[12] Redis 官方文档. Distributed Locks with Redis | Redis 分布式锁设计. [https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/](https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/)
[13] Redis 官方文档. SET Command | Redis SET 原子写入及过期参数. [https://redis.io/docs/latest/commands/set/](https://redis.io/docs/latest/commands/set/)
[14] Redis 官方文档. Scripting with Lua | Redis Lua 脚本原子执行. [https://redis.io/docs/latest/develop/programmability/eval-intro/](https://redis.io/docs/latest/develop/programmability/eval-intro/)
[15] Redis 官方文档. Replication | Redis 主从异步复制与写入安全. [https://redis.io/docs/latest/operate/oss_and_stack/management/replication/](https://redis.io/docs/latest/operate/oss_and_stack/management/replication/)
[16] Redis 官方文档. Redlock Algorithm | Redis Redlock 多节点锁算法. [https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/](https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/)
[17] Martin Kleppmann. How to Do Distributed Locking | 分布式锁的安全性与 Fencing Token. [https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html)
[18] Spring Data Redis. ValueOperations | 条件写入 API. [https://docs.spring.io/spring-data/redis/reference/api/java/org/springframework/data/redis/core/ValueOperations.html](https://docs.spring.io/spring-data/redis/reference/api/java/org/springframework/data/redis/core/ValueOperations.html)
[19] Spring Data Redis. Scripting | Redis 脚本执行机制. [https://docs.spring.io/spring-data/redis/reference/redis/scripting.html](https://docs.spring.io/spring-data/redis/reference/redis/scripting.html)
[20] Apache ZooKeeper. Recipes | 顺序节点与分布式锁. [https://zookeeper.apache.org/doc/r3.9.2/recipes.html](https://zookeeper.apache.org/doc/r3.9.2/recipes.html)
[21] AWS Builder's Library. Timeouts, Retries, and Backoff with Jitter | 超时、重试与随机抖动退避. [https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/](https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/)
[22] OpenTelemetry. Java Documentation | Java 可观测性官方文档. [https://opentelemetry.io/docs/languages/java/](https://opentelemetry.io/docs/languages/java/)