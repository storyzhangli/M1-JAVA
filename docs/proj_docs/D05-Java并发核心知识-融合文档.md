# Java 并发编程核心知识：中高级工程师系统学习指南

面向你的 Java 工程恢复阶段，这部分不能只停留在背诵八股文。中高级 Java 工程师需要同时掌握 JVM 线程机制、JMM 内存模型、并发工具、线程池调优、锁与同步、并发容器、任务编排、性能诊断，以及生产环境中的并发故障治理。

JDK 21 并发体系已形成**「平台线程池 + 虚拟线程 + 结构化并发」**三级生产级架构：线程池是传统CPU密集型并发的核心载体，基于`ThreadPoolExecutor` 7大核心参数运行；`synchronized`锁在JDK21中已正式废弃偏向锁，默认沿「无锁 → 轻量级自适应自旋锁 → 重量级锁」路径自动升级；中高级开发者需掌握虚拟线程原理、结构化并发、JMM内存模型、CAS无锁编程、高级并发工具、锁优化工程实践六大核心板块，覆盖生产级高并发场景与面试深度要求。

建议以 JDK 21 为学习基准。尤其注意：`synchronized` 的锁实现细节、虚拟线程、线程池默认配置等内容，必须区分 Java 语言规范、JDK 实现和具体版本，不能把旧版面试资料直接当成当前事实。

## 一、先建立并发编程的知识地图

第一层：线程与内存基础
Thread · Runnable · Callable · Future · JMM · happens-before

第二层：同步与线程安全
synchronized · volatile · CAS · AQS · Lock · 原子类

第三层：任务执行与协作
ThreadPoolExecutor · 拒绝策略 · BlockingQueue · CompletableFuture · 并发容器

第四层：生产级并发工程
限流 · 隔离 · 超时 · 取消 · 背压 · 监控 · 线程转储 · JFR · 虚拟线程

学习目标不是会说术语，而是能回答三个问题：

1. 为什么会发生并发问题？
2. 哪种机制可以解决，付出的代价是什么？
3. 在 Spring Boot 服务中，如何正确使用、监控并排查？

## 二、线程池核心参数：必须理解执行流程

### 2.1 `ThreadPoolExecutor` 七个核心参数

```
public ThreadPoolExecutor(
        int corePoolSize,
        int maximumPoolSize,
        long keepAliveTime,
        TimeUnit unit,
        BlockingQueue<Runnable> workQueue,
        ThreadFactory threadFactory,
        RejectedExecutionHandler handler
)
```

| 参数              | 含义             | 工程实践要点                                                 |
| ----------------- | ---------------- | ------------------------------------------------------------ |
| `corePoolSize`    | 核心线程数       | 常驻线程规模，不等于最大并发请求数；CPU密集型设为CPU核心数±1，IO密集型可适当调高 |
| `maximumPoolSize` | 最大线程数       | 线程池允许创建的最大工作线程数；禁止设为`Integer.MAX_VALUE`，避免耗尽系统资源 |
| `keepAliveTime`   | 空闲线程存活时间 | 非核心线程空闲多久后回收；核心线程也可通过`allowCoreThreadTimeOut`配置超时回收 |
| `unit`            | 时间单位         | 生产常用`SECONDS`、`MILLISECONDS`                            |
| `workQueue`       | 任务队列         | 决定任务积压方式与线程扩容时机；禁止使用无界队列避免OOM，生产优先用有界队列 |
| `threadFactory`   | 线程工厂         | 自定义线程名称前缀、优先级、是否守护线程；强制命名方便故障排查与监控 |
| `handler`         | 拒绝策略         | 线程和队列均满时的任务处理方式；生产禁止默认抛异常，需配合业务降级策略 |

### 2.2 任务提交的真实执行顺序

提交任务 `execute(task)`
当前工作线程数小于 `corePoolSize`？
是：创建核心工作线程执行任务
否则尝试加入 `workQueue`
入队成功：等待工作线程处理
队列无法接纳任务？
若线程数小于 `maximumPoolSize`，尝试创建非核心线程
线程数达到上限且队列已满
触发拒绝策略

**面试标准执行流程**：
任务提交 → 核心线程未满 → 创建核心线程执行
→ 核心线程已满 → 任务进入等待队列
→ 队列已满 → 创建非核心线程执行，直到最大线程数
→ 达到最大线程数 → 触发拒绝策略

最容易考错的地方：线程池不是一提交任务就从核心线程一路扩容到最大线程。 当核心线程已满时，它会优先尝试入队；只有队列不能接纳任务时，才会考虑扩容到 `maximumPoolSize`。

记忆简语：核心先执行，随后先排队；队列满了才扩容，扩容到顶才拒绝。

### 2.3 为什么队列会影响线程池扩容？

- 无界队列，例如 `LinkedBlockingQueue` 默认构造方式：任务通常持续入队，线程池一般不会因为队列积压而扩容到最大线程数；风险是内存耗尽、任务延迟持续增长。
- 有界队列，例如 `ArrayBlockingQueue`：队列满后，线程池才有机会扩容到最大线程数，之后才可能拒绝。
- 直接移交队列，例如 `SynchronousQueue`：不保存等待任务；如果没有空闲工作线程接手，则尝试创建线程，直到上限，再触发拒绝策略。

### 2.4 线程池参数如何设置？

不要机械套用“CPU 核数 + 1”或“CPU 核数 × 2”。这些只能作为初始实验的参考，不是生产配置公式。

- CPU 密集型：线程多于可用 CPU 数量太多，可能增加上下文切换。应以 CPU 核数为起点，通过基准测试和线上监控调整。
- I/O 密集型：线程可能长时间等待数据库、Redis、HTTP 服务等 I/O，可以考虑更多工作线程，但必须受下游连接池、服务限流、内存及延迟预算约束。
- 混合型任务：拆分不同资源类型的任务池，通常比所有任务共用一个线程池更容易控制故障影响范围。

还要区分三个容量：

1. 线程池可运行的工作线程数量。
2. 等待执行的任务队列容量。
3. 下游资源的并发容量，例如 JDBC 连接池最大连接数。

线程池能开 100 个线程，不代表数据库就能承受 100 个并发查询。

#### 工程最佳实践与避坑

- **禁止用`Executors`创建线程池**：`FixedThreadPool`/`SingleThreadPool`队列无界易引发OOM；`CachedThreadPool`最大线程无上限易耗尽系统资源。
- **线程命名强制规范**：自定义`ThreadFactory`指定线程名前缀，如`order-pool-%d`，排查问题时可快速定位线程归属。
- **必须监控的核心指标**：活跃线程数、队列堆积长度、任务完成数、拒绝次数、任务最大等待时间。

### 2.5 JDK 21 线程池重要变更

1. **虚拟线程池正式支持**：新增`Executors.newVirtualThreadPerTaskExecutor()`，每个任务分配一个虚拟线程，不存在核心/最大线程、队列概念，天然支持百万级IO并发。
2. **AutoCloseable支持**：`ThreadPoolExecutor`实现`AutoCloseable`接口，可通过`try-with-resources`自动关闭线程池。
3. **调度优化**：优化了线程池在虚拟线程场景下的调度逻辑，减少载体线程的上下文切换开销。

## 三、线程池拒绝策略：四种策略必须会选、会解释

《ThreadPoolExecutor | Java 21 线程池官方文档》：[Oracle JDK 21 ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)

### 3.1 四种标准拒绝策略

| 策略                  | 行为                                           | 主要风险与适用场景                                           |
| --------------------- | ---------------------------------------------- | ------------------------------------------------------------ |
| `AbortPolicy`         | 抛出 `RejectedExecutionException`              | 默认策略；调用方可感知失败并决定重试、降级或返回错误；适用于严格限流、不允许任务丢失的场景 |
| `CallerRunsPolicy`    | 由提交任务的线程执行任务                       | 形成流量削峰与反压，但可能拖慢HTTP请求线程、事件循环等关键线程；适用于普通任务降级执行，保护线程池不被冲垮 |
| `DiscardPolicy`       | 静默丢弃当前任务                               | 任务无声丢失，不适用于不可丢失业务；适用于非核心任务、可丢失场景，如日志上报、次要统计 |
| `DiscardOldestPolicy` | 尝试丢弃队列头部的等待任务，再重新提交当前任务 | 旧任务可能丢失，不保证新任务最终执行；适用于优先级高的新任务场景，如最新消息覆盖旧消息 |

>
> 重要坑点：`CallerRunsPolicy` 在执行器已关闭时不会执行被拒绝任务，因此它不等于“任务永不丢失”。

#### 如何选择拒绝策略？

- 订单、支付、账户余额变更：不能只靠某种拒绝策略保证业务正确性。应让失败可感知，结合持久化任务、可靠消息、幂等、重试和补偿机制。
- 报表、批处理：可以拒绝并记录，交给上游调度系统稍后重试。
- 可丢弃的非关键任务：例如部分非关键统计，可评估丢弃策略，但必须有指标记录丢弃数量。
- 需要反压的普通任务：可评估 `CallerRunsPolicy`，但必须确认调用方线程允许执行该任务。

### 3.2 生产代码示例：有界队列、命名线程、显式拒绝

以下示例可用于普通 Java 或 Spring 项目，数值仅为演示配置，不是通用生产参数。

```
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ExecutorFactory {
    private ExecutorFactory() {
    }

    public static ThreadPoolExecutor create() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(
                    task,
                    "business-worker-" + sequence.incrementAndGet()
            );
            thread.setDaemon(false);
            return thread;
        };

        return new ThreadPoolExecutor(
                4,                              // corePoolSize
                8,                              // maximumPoolSize
                60,                             // keepAliveTime
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(200),  // 有界队列
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
```

使用时应明确管理线程池的生命周期：

```
ThreadPoolExecutor executor = ExecutorFactory.create();
try {
    executor.execute(() -> {
        // 执行可控时长的业务任务
    });
} catch (java.util.concurrent.RejectedExecutionException e) {
    // 记录拒绝指标，并按业务语义返回失败、降级或持久化待处理任务
    throw e;
} finally {
    executor.shutdown();
}
```

>
> 说明：这里的 `finally` 适合展示短生命周期示例，不适合在每个 Web 请求结束时关闭共享线程池。在 Spring Boot 服务中，通常应将线程池注册为受容器管理的 Bean，并在应用关闭时统一停止。

## 四、`synchronized` 锁升级：面试高频，但要分清版本

### 4.1 它究竟解决什么问题？

`synchronized` 提供两项核心保证：

1. **互斥性（Mutual Exclusion）**：同一时刻，多个线程不能同时持有同一个监视器锁，保证复合操作的原子性。
2. **可见性（Visibility）**：一个线程释放某个监视器锁，happens-before 后续线程获取同一个锁；加锁时清空线程工作内存，从主存读取最新数据；解锁时将工作内存数据刷回主存，保证之前的写入对后续持锁线程可见。

《Java Language Specification §14.19 / §17 | Java 语言规范：同步与内存模型》：[同步语句规范](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.19) · [线程、锁与 Java 内存模型](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html)

示例：

```
public class Counter {
    private int count;
    public synchronized void increment() {
        count++;
    }
    public synchronized int getCount() {
        return count;
    }
}
```

这里的关键不只是 `synchronized` 保证可见性，而是它还保护了 `count++` 这个读—改—写的复合操作，避免多个线程更新时相互覆盖。

### 4.2 锁对象到底是什么？

| 写法                                        | 监视器锁对象            |
| ------------------------------------------- | ----------------------- |
| 实例同步方法 `synchronized void m()`        | 当前实例 `this`         |
| `synchronized (lock) {}`                    | `lock` 对象             |
| 静态同步方法 `static synchronized void m()` | 该类对应的 `Class` 对象 |

实例锁和类锁不是同一把锁。不同实例的实例锁通常也不同。

一个容易被忽视的错误：

```
synchronized (new Object()) {
    // 每次都创建新对象，线程之间无法依靠这把锁互斥
}
```

锁必须是多个竞争线程共同使用的同一个对象。

### 4.3 什么是“锁升级”？

传统 HotSpot 面试资料常将 `synchronized` 的优化路径概括为：
无竞争或低竞争时的快速路径
尽量避免重量级同步的开销
轻量级锁相关优化
通过原子操作等机制尝试快速获取锁
竞争加剧时可能膨胀（Inflation）
使用更复杂的监视器机制协调竞争线程

但是，必须特别纠正一个常见的过时说法：

>
> 特别纠正：“偏向锁 → 轻量级锁 → 重量级锁”是历史上的 HotSpot 教材描述，**偏向锁在 JDK 15 被默认禁用并弃用**（JEP 374），JDK21中不存在偏向锁阶段。锁的内部表示、膨胀机制和优化策略属于 JVM 实现细节；Java 语言规范保证的是同步语义，而不是要求 JVM 必须按某个固定的锁升级流程执行。

《JEP 374 | Disable and Deprecate Biased Locking》：[OpenJDK 偏向锁调整说明](https://openjdk.org/jeps/374)

#### 锁的载体：对象头 Mark Word

`synchronized`的锁信息存储在对象头的**Mark Word**中，32位JVM占32位，64位JVM占64位，存储内容包括锁状态标记位、GC分代年龄、对象哈希码、锁指针等。锁升级本质就是Mark Word中存储内容和状态的切换过程，整个过程由JVM自动完成，对开发者透明。

#### JDK21 锁升级完整路径

##### 阶段1：无锁状态

对象刚创建时，处于无锁状态，Mark Word存储对象哈希码、GC分代年龄，锁标记位为01。

##### 阶段2：轻量级锁（自适应自旋锁）

- **触发**：第一个线程竞争锁时，JVM在当前线程的栈帧中创建锁记录，通过CAS操作将对象的Mark Word替换为指向该锁记录的指针。
- **自适应自旋**：CAS失败时，线程不会立刻阻塞，而是根据上一次同锁的自旋成功率动态调整自旋次数，自旋成功则获得锁。
- **升级条件**：自旋超过一定次数、或另一个线程同时竞争，升级为重量级锁。
- **特点**：用户态执行，不阻塞线程，性能高；适合锁持有时间短、竞争不激烈的场景。

##### 阶段3：重量级锁

- **触发**：竞争激烈、自旋失败时，锁升级为重量级锁，依赖操作系统内核的互斥量（mutex）实现。
- **行为**：获取锁失败的线程会被挂起阻塞，进入内核态，发生上下文切换。
- **特点**：性能开销大，但不会持续消耗CPU；适合锁持有时间长、竞争激烈的场景。

#### JVM 内置锁优化

1. **锁消除**：通过逃逸分析判定对象不会逃逸出方法，自动消除方法内的加锁操作，比如方法内局部的StringBuffer加锁会被直接消除。
2. **锁粗化**：连续多次对同一个对象加锁解锁，JVM会自动合并为一次大范围的加锁，减少反复加锁解锁的开销。
3. **锁降级**：GC安全点时，重量级锁可以降级为轻量级锁，减少持续的内核开销。

### 4.4 中高级工程师应该掌握什么？

比背锁升级顺序更重要的是以下内容：

- 锁竞争：多个线程争抢同一个监视器会产生什么成本。
- 锁范围：只同步真正需要保护的共享状态，避免将数据库或网络 I/O 包在长时间持锁区间中。
- 锁粗化与锁消除：JIT 在满足语义的条件下进行的优化。
- 可重入性：同一线程可以再次获得自己已经持有的同一监视器锁，Mark Word中维护重入计数器，加锁递增，解锁递减，计数为0才真正释放锁。
- 安全发布：正确发布共享对象，避免构造过程中的状态被其他线程错误观察。
- 死锁：不同线程按相反顺序获取多把锁。
- 公平性和饥饿：`synchronized` 不提供业务层面的公平排队承诺，默认非公平锁，吞吐量高于公平锁。

## 五、`volatile`、CAS 与 JMM：真正理解并发的基础

### 5.1 `volatile` 能做什么，不能做什么？

Java内存模型（Java Memory Model，JMM）是规范多线程环境下内存可见性、有序性、原子性的模型，用于屏蔽不同硬件架构的差异，定义并发线程之间读写操作的可见性和顺序约束。

JMM三大核心特性：

- **原子性**：一个操作不可中断，要么全部执行要么不执行
- **可见性**：一个线程修改了变量，其他线程能立刻看到最新值
- **有序性**：禁止指令重排序

`volatile` 主要提供变量读写的**可见性**和**禁止指令重排序**两大作用，通过内存屏障实现，但它不保证复合操作的原子性。

```
private volatile boolean running = true;
public void stop() {
    running = false;
}
public void runLoop() {
    while (running) {
        // 执行任务
    }
}
```

适合用来表达跨线程共享的状态标志。

但下面的代码依然有并发问题：

```
private volatile int count;
public void increment() {
    count++; // 不是原子操作
}
```

`count++` 包含读取、计算和写入。多个线程可能读取同一个旧值，最后导致更新丢失。需要使用 `AtomicInteger`、锁或其他适合的并发方案。

>
> 经典误区：volatile只保证可见性和有序性，**不保证原子性**，不能替代锁。

《Java Concurrency Package | Java 并发包内存一致性说明》：[Java 21 并发包官方文档](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/package-summary.html)

### 5.2 JMM 与 happens-before

JMM 通过 happens-before 规则定义操作之间的可见性顺序。需要牢记的规则：

1. **程序顺序规则**：同一线程内，前面的操作happens-before后面的操作。
2. **监视器锁规则**：对同一监视器的解锁，happens-before后续的加锁。
3. **volatile规则**：对某个`volatile`变量的写入，happens-before后续对该变量的读取。
4. **线程启动规则**：调用`Thread.start()`之前的动作，happens-before被启动线程中的动作。
5. **线程终止规则**：线程内所有操作happens-before线程终止检测。
6. **传递性**：A happens-before B，B happens-before C，则A happens-before C。

不要把 happens-before 误解为“CPU 一定严格按照源码顺序执行”。它表达的是可观察行为的顺序约束，而不是禁止一切编译器或处理器优化。

### 5.3 CAS 是什么？

CAS 是 Compare-And-Set / Compare-And-Swap 的常见统称，指比较并在条件满足时原子更新。
概念上可以理解为：

```
如果当前值仍等于期望值：
    原子地更新为新值
否则：
    更新失败，重新读取并尝试
```

常见应用：

- `AtomicInteger`
- `AtomicLong`
- `AtomicReference`
- `LongAdder`
- 并发队列和其他并发算法

#### ABA问题与解决方案

CAS存在经典的ABA问题：值从A变成B再变回A，CAS会误以为没有发生变化。

- 解决方案：使用`AtomicStampedReference`增加版本号，每次修改版本号递增，通过“值+版本号”共同校验。

#### 常用原子工具类

- **基础原子类**：`AtomicInteger`、`AtomicLong`、`AtomicReference`，适用于低并发下的原子更新。
- **高并发计数器**：`LongAdder`、`LongAccumulator`，采用分段累加思想，高并发下性能远超AtomicLong，适合统计计数场景。
- **VarHandle**：JDK9引入，JDK21推荐的底层原子操作工具，替代`Unsafe`类，支持各种数据类型的原子操作、内存屏障控制，性能更优、使用更安全。

#### CAS 的局限

- 高竞争时反复重试会消耗 CPU。
- 对多个字段的复杂业务不变量，单个 CAS 通常不足以保证整体原子性。
- `LongAdder` 适合高并发统计等场景，但其 `sum()` 不是多字段业务事务的原子快照。

## 六、`ReentrantLock`、AQS 与 `Condition`

《AbstractQueuedSynchronizer | AQS 官方 API》：[Java 21 AbstractQueuedSynchronizer](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/locks/AbstractQueuedSynchronizer.html)

`AQS` 全称 AbstractQueuedSynchronizer，是 Java 并发工具中用于构建同步器的重要基础设施。它维护同步状态，并支持排队等待等同步机制。

### 6.1 `synchronized` 与 `ReentrantLock` 对比

| 维度       | `synchronized`                     | `ReentrantLock`                     |
| ---------- | ---------------------------------- | ----------------------------------- |
| 实现层面   | JVM内置关键字，底层由JVM实现       | JDK类库实现，API层面的锁            |
| 使用方式   | 进入同步块自动获取，退出时自动释放 | 显式调用`lock()` / `unlock()`       |
| 异常安全   | 语言结构保证退出同步块时释放监视器 | 必须在`finally`块中执行`unlock()`   |
| 锁公平性   | 默认非公平，不可修改               | 支持公平/非公平模式，构造参数指定   |
| 尝试获取锁 | 无原生`tryLock()`能力              | 支持`tryLock()`，可设置超时时间     |
| 等待可中断 | 无直接的可中断获取锁接口           | 支持`lockInterruptibly()`可中断等待 |
| 条件队列   | 仅支持单个`wait/notify`机制        | 可创建多个`Condition`，实现精准唤醒 |
| 锁状态查看 | 不可直接查看锁状态                 | 可通过`isLocked()`等方法查看锁状态  |
| 锁优化     | JVM自动锁消除、锁粗化、锁升级      | 需开发者自行控制锁粒度与超时策略    |

示例：

```
import java.util.concurrent.locks.ReentrantLock;

public class InventoryService {
    private final ReentrantLock lock = new ReentrantLock();
    private int stock = 100;

    public boolean deduct(int quantity) {
        lock.lock();
        try {
            if (quantity <= 0 || stock < quantity) {
                return false;
            }
            stock -= quantity;
            return true;
        } finally {
            lock.unlock();
        }
    }
}
```

>
> 注意：这个例子只能保证当前 JVM 内、共享该实例的线程之间的同步。若库存由多台应用服务器共同修改，还需要数据库事务、乐观锁、分布式协调或其他跨进程方案。

### 6.2 `wait/notify` 的常见陷阱

- `wait()`、`notify()`、`notifyAll()` 必须由当前线程持有对应对象的监视器，否则会抛出 `IllegalMonitorStateException`。
- `wait()` 会释放该对象的监视器锁，醒来后需要重新竞争锁。
- `Thread.sleep()` 不会因为睡眠而自动释放已经持有的监视器。
- 等待条件应使用 `while` 循环重新判断，而不是仅使用 `if`。

### 6.3 常用高级并发同步工具

除了基础的锁实现，JDK还提供了丰富的高级同步工具，覆盖不同协作场景：

| 工具类                   | 核心作用                           | 适用场景                             |
| ------------------------ | ---------------------------------- | ------------------------------------ |
| `ReentrantReadWriteLock` | 读写锁，读共享、写独占             | 读多写少场景，如配置缓存、元数据读取 |
| `StampedLock`            | 读写锁增强，支持乐观读模式         | 读极多写极少场景，性能优于读写锁     |
| `CountDownLatch`         | 倒计时门闩，一个线程等多个线程完成 | 主线程等待多个子任务初始化完成       |
| `CyclicBarrier`          | 循环栅栏，多个线程互相等待，可复用 | 多阶段批量任务同步，如数据分批计算   |
| `Semaphore`              | 信号量，控制同时访问的并发数       | 限流、资源池控制                     |
| `Phaser`                 | 阶段同步器，支持动态调整参与数     | 多阶段、可动态变更的复杂任务同步     |

## 七、`ThreadLocal`：线程隔离与内存泄漏风险

`ThreadLocal` 为每个线程提供独立的变量副本，常用于请求上下文、链路追踪标识等场景。

```
private static final ThreadLocal<String> TRACE_ID =
        new ThreadLocal<>();

public void process(String traceId) {
    try {
        TRACE_ID.set(traceId);
        doBusiness();
    } finally {
        TRACE_ID.remove();
    }
}
```

#### 底层实现原理

每个Thread对象维护一个`ThreadLocalMap`，key是ThreadLocal的弱引用，value是线程本地变量值。

#### 内存泄漏原因与解决方案

- **泄漏根源**：key是弱引用，GC会回收key，但value是强引用；如果线程长期存活（如线程池线程），value永远无法被回收，造成内存泄漏。
- **解决方案**：使用完必须调用`remove()`方法，配合`try-finally`保证执行。

#### JDK21 优化

优化了虚拟线程下的ThreadLocal存储结构，支持批量清理，减少内存泄漏风险。

必须掌握：

- 线程池会复用工作线程，不清理 `ThreadLocal` 可能导致数据串用。
- 在线程池中，任务结束不代表工作线程销毁。
- `ThreadLocal` 本身不提供线程间同步。
- 不要将请求级上下文无限期保存在长生命周期线程中。
- JDK 21 的虚拟线程虽然支持 `ThreadLocal`，但大量使用时仍需评估对象占用与生命周期。

记忆简语：线程隔离不等于自动清理，线程复用必须管理上下文。

## 八、并发容器：不只是会用 `ConcurrentHashMap`

《ConcurrentHashMap | Java 21 并发哈希表官方文档》：[ConcurrentHashMap API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)

| 工具                    | 主要用途          | 需要注意                                       |
| ----------------------- | ----------------- | ---------------------------------------------- |
| `ConcurrentHashMap`     | 高并发键值读写    | 单次操作线程安全，不代表多步业务操作自动原子   |
| `CopyOnWriteArrayList`  | 读多写少的列表    | 写操作需要复制数组，不适合频繁修改的大集合     |
| `BlockingQueue`         | 生产者—消费者协作 | 必须理解队列容量、阻塞、超时与关闭语义         |
| `ConcurrentLinkedQueue` | 高并发非阻塞队列  | 不提供有界容量和阻塞等待能力                   |
| `ArrayBlockingQueue`    | 有界 FIFO 队列    | 适用于限制任务积压                             |
| `DelayQueue`            | 延迟任务管理      | 需要理解到期时间、取消与任务清理               |
| `LongAdder`             | 高并发计数统计    | 适合统计吞吐，不适合所有要求精确原子快照的业务 |

>
> 核心误区：`ConcurrentHashMap`仅保证单次原子操作的线程安全，先检查后执行的复合操作（如`containsKey`+`put`）仍不安全。

一个常见错误：

```
if (!map.containsKey(key)) {
    map.put(key, value);
}
```

虽然 `map` 是 `ConcurrentHashMap`，这两个操作组合起来仍不是原子操作。多个线程可能同时判断 key 不存在，然后都执行 `put()`。

对于简单的按需初始化，可以考虑：

```
Value value = map.computeIfAbsent(key, this::loadValue);
```

但映射函数应当短小，避免在其中执行长时间阻塞的 I/O 或递归修改同一个 map。并且，`computeIfAbsent` 只能保证 map 映射操作的相应原子语义，不能自动保证外部数据库副作用只发生一次。

## 九、`CompletableFuture`：异步任务编排必须掌握

《CompletableFuture | Java 21 异步编程 API》：[CompletableFuture 官方文档](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html)

中高级工程师必须区分这些方法：

| 方法              | 含义                                       |
| ----------------- | ------------------------------------------ |
| `thenApply()`     | 对上一步结果进行转换                       |
| `thenCompose()`   | 将嵌套异步任务扁平化                       |
| `thenCombine()`   | 等待两个独立任务的结果并组合               |
| `allOf()`         | 等待多个任务完成，但本身不直接返回结果集合 |
| `anyOf()`         | 任一任务完成后得到其结果                   |
| `exceptionally()` | 为异常提供恢复结果的路径                   |
| `handle()`        | 同时处理正常结果与异常                     |
| `orTimeout()`     | 超时后使该 `CompletableFuture` 异常完成    |

### 示例：两个独立服务并行查询

```
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class DashboardService {
    private final Executor executor;

    public DashboardService(Executor executor) {
        this.executor = executor;
    }

    public CompletableFuture<String> loadDashboard() {
        CompletableFuture<String> profile =
                CompletableFuture.supplyAsync(
                        () -> queryProfile(),
                        executor
                );
        CompletableFuture<String> orders =
                CompletableFuture.supplyAsync(
                        () -> queryOrders(),
                        executor
                );
        return profile.thenCombine(
                orders,
                (p, o) -> "profile=" + p + ", orders=" + o
        );
    }

    private String queryProfile() {
        return "profile-data";
    }

    private String queryOrders() {
        return "order-data";
    }
}
```

这里的 `Executor` 由外部注入，方便对线程池进行统一配置、监控和关闭。

必须避免以下误区：

1. `CompletableFuture` 不意味着任务一定在独立线程中执行；非 `Async` 后缀的阶段可能在完成前一阶段的线程中执行。
2. 没有显式指定执行器时，`supplyAsync()` 等异步方法通常使用 `ForkJoinPool.commonPool()`，需要注意线程池隔离。
3. `allOf()` 完成后，仍需要从各个 future 中收集结果。
4. `orTimeout()` 不等于底层 HTTP 请求、数据库查询或其他业务操作必然停止。
5. `CompletableFuture.cancel()` 不应被当作可靠的底层任务终止机制。
6. 处理异常时要明确哪些异常可降级、哪些必须向上传播，不能一律返回默认值掩盖故障。

## 十、死锁、活锁、饥饿与中断：排障必须掌握

这几个概念经常混淆。

- 死锁（Deadlock）：多个线程相互等待对方持有的资源，形成循环等待。
- 活锁（Livelock）：线程持续运行、互相响应，却无法有效推进任务。
- 饥饿（Starvation）：某个线程长期得不到所需的 CPU 时间或锁资源。
- 阻塞（Blocking）：线程等待某个条件或资源，未必意味着发生故障。
- 中断（Interruption）：Java 用于请求线程停止等待或协作式取消的机制，不是强制杀死线程。

### 10.1 避免死锁的主要手段

死锁的形成需同时满足四个必要条件：互斥、占有且等待、不可剥夺、循环等待。

避免死锁的主要手段：

1. 多把锁统一获取顺序。
2. 缩短持锁时间。
3. 避免持锁期间执行未知的外部调用。
4. 在适合的场景中使用 `tryLock(timeout)`。
5. 设计取消和超时机制，但不能把超时误认为资源已经释放。

### 10.2 正确处理中断

```
try {
    Thread.sleep(1_000);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
}
```

当方法无法继续向上传播 `InterruptedException` 时，常见做法是恢复中断标志，并尽快结束当前工作。
如果方法能够声明 `throws InterruptedException`，也可以将中断向上传播，让调用方决定如何取消任务。

不要随意吞掉 `InterruptedException`。 否则线程池关闭、任务取消或上层超时控制可能失效。

### 10.3 其他并发经典问题

###### 活锁

线程不断重试但一直失败，不阻塞也无法推进任务。常见于互斥退让场景，多个线程同时释放锁又同时竞争。

- 解决方案：增加随机等待时间，错开重试时机。

###### 饥饿

线程长期抢不到锁或CPU资源，无法执行任务。

- 解决方案：使用公平锁、保证资源分配优先级。

###### 伪共享（False Sharing）

多个变量位于同一个CPU缓存行，不同线程修改不同变量时互相导致缓存行失效，大幅降低性能。

- 解决方案：使用`@Contended`注解填充缓存行，JDK21原生支持。

###### 并发集合安全误区

`ConcurrentHashMap`等并发容器仅保证单个方法的原子性，“检查-执行”的复合操作不具备原子性，不能替代锁。

## 十一、JDK 21 新重点：虚拟线程与结构化并发

《Virtual Threads | Java 21 虚拟线程官方指南》：[Oracle JDK 21 虚拟线程指南](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)

JDK 21 正式支持虚拟线程。它们是轻量级线程，适合大量以阻塞式 I/O 为主的任务。

### 11.1 虚拟线程基础

| 平台线程（Platform Thread）      | 虚拟线程（Virtual Thread）                              |
| -------------------------------- | ------------------------------------------------------- |
| 通常对应一个操作系统内核线程     | 由JVM调度，可在少量载体平台线程上运行                   |
| 创建和长期占用的资源成本相对较高 | 内存开销极小（每个栈KB级），百万级并发也不会耗尽资源    |
| 常使用线程池复用                 | 通常采用每个任务一个虚拟线程的方式，不建议池化          |
| 可适用于CPU和I/O任务             | 适合IO密集型任务，CPU密集型不会提升性能反而增加调度开销 |

#### 核心原理与特性

- **实现原理**：JVM管理的轻量级用户线程，少量平台线程（载体线程）作为调度器，IO阻塞时自动将虚拟线程从载体线程卸载，唤醒时重新挂载，实现海量虚拟线程复用少量平台线程。
- **核心优势**：上下文切换在用户态完成，无需内核介入，切换成本极低。
- **适用场景**：IO密集型任务（数据库调用、网络请求、Redis/模型接口调用），AI应用中大量并发IO场景收益最大。
- **不适用场景**：CPU密集型任务，不会提升性能，反而增加调度开销。
- **最佳实践**：不要池化虚拟线程，每个任务创建一个；优先使用`Executors.newVirtualThreadPerTaskExecutor()`。

### 简单使用

```
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class VirtualThreadDemo {
    public static void main(String[] args) throws Exception {
        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> future =
                    executor.submit(() -> "任务执行完成");
            System.out.println(future.get());
        }
    }
}
```

`ExecutorService` 在 JDK 21 中支持 `AutoCloseable`，这里退出 `try` 块时会关闭执行器并等待其任务结束。

### 虚拟线程的工程边界

- 虚拟线程降低的是大量阻塞任务的线程资源成本，不是数据库连接、HTTP 连接或下游服务的容量限制。
- 不要为了限制虚拟线程数量而机械地将其放进传统线程池；应根据资源瓶颈使用连接池、`Semaphore`、限流器等机制。
- CPU 密集型任务仍然需要控制并行度。
- JDK 21 中，虚拟线程在特定 `synchronized` 区域内执行阻塞操作时可能出现载体线程固定（Pinning），需要结合真实负载和诊断工具评估，而不是简单认定所有 `synchronized` 都有问题。

### 11.2 结构化并发（Structured Concurrency）

结构化并发是JDK21的正式特性，用于解决传统异步任务的生命周期管理问题。

#### 解决的核心痛点

传统线程池异步任务的生命周期与父线程解绑，父线程取消或异常时，子线程仍继续运行，造成资源泄漏、结果不一致。

#### 核心思想

任务有明确的作用域，所有子任务的生命周期不超过父任务的作用域，实现统一取消、统一异常处理、统一等待完成。

#### 核心API

核心类为`StructuredTaskScope`，内置两种常用策略：

- `ShutdownOnFailure`：任意一个子任务失败，立即取消所有其他子任务
- `ShutdownOnSuccess`：任意一个子任务成功，立即取消所有其他子任务

#### 代码示例

```
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Subtask<User> userTask = scope.fork(() -> userService.getUser(userId));
    Subtask<Order> orderTask = scope.fork(() -> orderService.getOrder(orderId));
    
    scope.join();           // 等待所有子任务完成
    scope.throwIfFailed();  // 任意失败则抛出异常，自动取消所有任务
    
    User user = userTask.get();
    Order order = orderTask.get();
    return new UserOrderVO(user, order);
}
```

## 十二、中高级工程师还必须掌握的并发知识清单

## 并发知识自检清单

### 线程与内存模型

- 线程生命周期与状态转换
- Runnable、Callable、Future、FutureTask
- JMM 与 happens-before
- 安全发布与 final 字段语义
- 伪共享与缓存行优化

### 同步与原子性

- synchronized 与可重入性、锁升级机制
- volatile 的可见性与局限
- CAS、ABA问题、AtomicReference、VarHandle
- AQS、ReentrantLock、Condition
- StampedLock、ReadWriteLock 的适用边界

### 线程池与任务编排

- ThreadPoolExecutor 七参数与执行流程
- 有界队列与拒绝策略
- CompletableFuture 组合与异常处理
- 定时任务、任务取消、超时与中断
- 线程池关闭、队列积压与背压
- 虚拟线程原理与适用场景
- 结构化并发与任务生命周期管理

### 并发容器与算法

- ConcurrentHashMap 的原子方法
- BlockingQueue 与生产者—消费者
- CopyOnWriteArrayList 的读写成本
- ConcurrentLinkedQueue 与无锁算法基础
- LongAdder 与高并发计数

### 生产级工程

- 死锁、活锁、饥饿与锁竞争
- CPU 密集型与 I/O 密集型隔离
- 限流、熔断、降级与资源隔离
- ThreadLocal 清理与上下文传播
- 虚拟线程与下游资源限流
- 线程转储、JFR、JConsole、JMC 与压测

### 更深层的能力

- ForkJoinPool 与 work-stealing
- CountDownLatch、CyclicBarrier、Semaphore、Phaser
- 内存可见性测试与并发压力测试
- 幂等、事务边界与分布式并发控制

## 十三、生产环境并发问题如何排查？

当 Java 服务出现 CPU 飙高、请求堆积、响应超时或线程池拒绝任务时，建议按以下顺序排查：

1. **先看指标**。 检查 CPU、堆内存、GC、请求延迟、错误率、线程池活跃线程数、队列长度、拒绝次数，以及数据库连接池等待情况。
2. **采集线程转储**。 在允许的情况下执行 `jcmd <pid> Thread.print`，检查线程状态、阻塞位置和是否存在死锁。生产操作前应确认进程、权限与采集影响。
3. **分析线程栈**。 区分线程是在执行 CPU 计算、等待监视器锁、等待条件、等待数据库连接，还是阻塞在外部 I/O。
4. **检查下游瓶颈**。 线程池队列增长不一定是线程数不足，也可能是数据库慢查询、HTTP 下游变慢或连接池耗尽。
5. **用证据验证修改**。 通过压测、JFR（Java Flight Recorder）或合适的性能分析工具比较修改前后的吞吐量、P95/P99 延迟、CPU、队列长度和拒绝率。

常用诊断命令示例：

```
# 查看 Java 进程
jcmd -l
# 输出线程转储
jcmd <pid> Thread.print
# 查看进程与线程的运行状态（Linux）
top -H -p <pid>
```

`<pid>` 是占位符，执行时替换为真实进程号。线程转储、JFR 和系统工具应结合环境权限、运行负载和采集策略使用。

## 十四、学习优先级：不要平均用力

**第一优先级：必须能手写、能解释**
线程池七参数、任务执行流程、四种拒绝策略、synchronized、volatile、CAS、JMM、happens-before、死锁、线程安全。

**第二优先级：必须能完成工程实践**
线程池配置与监控、CompletableFuture、并发容器、ThreadLocal 清理、中断、超时、优雅关闭、虚拟线程基础使用。

**第三优先级：形成中高级排障能力**
AQS、ForkJoinPool、JFR、线程转储、压测、结构化并发、资源隔离、并发性能分析。

---

### 参考文献

[1] Oracle. Java Language Specification, Java SE 21 — Threads and Locks. [官方文档](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html).
[2] Oracle. ThreadPoolExecutor, Java SE 21 API. [官方文档](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html).
[3] Oracle. RejectedExecutionHandler, Java SE 21 API. [官方文档](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/RejectedExecutionHandler.html).
[4] Oracle. Java Concurrency Package, Java SE 21 API. [官方文档](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/package-summary.html).
[5] OpenJDK. JEP 374: Disable and Deprecate Biased Locking. [官方文档](https://openjdk.org/jeps/374).
[6] Oracle. AbstractQueuedSynchronizer, Java SE 21 API. [官方文档](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/locks/AbstractQueuedSynchronizer.html).
[7] Oracle. ConcurrentHashMap, Java SE 21 API. [官方文档](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html).
[8] Oracle. CompletableFuture, Java SE 21 API. [官方文档](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html).
[9] Oracle. Virtual Threads, Java SE 21. [官方文档](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html).
[10] OpenJDK. JEP 444: Virtual Threads. [官方文档](https://openjdk.org/jeps/444).