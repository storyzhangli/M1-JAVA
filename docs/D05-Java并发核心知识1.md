### ✅ 核心结论

JDK 21 并发体系形成**「平台线程池 + 虚拟线程 + 结构化并发」**三级生产级架构；线程池是传统CPU密集型并发的核心载体，基于`ThreadPoolExecutor` 7大核心参数运行，配套4种标准拒绝策略；`synchronized`锁在JDK21中已正式废弃偏向锁，默认沿「无锁 → 轻量级自适应自旋锁 → 重量级锁」路径自动升级，由JVM内置完成优化；中高级开发者必须在此基础上掌握**虚拟线程原理、结构化并发、JMM内存模型、CAS无锁编程、高级并发工具、锁优化工程实践**六大核心板块，才能覆盖生产级高并发场景与面试深度要求。

---

## 一、线程池核心原理与工程实践

### 1. 七大核心参数与执行流程

`ThreadPoolExecutor`是Java线程池的核心实现，所有参数共同控制线程池的任务调度、资源分配与过载保护。

| 参数                                | 作用说明                                                     | 工程注意事项                                                 |
| ----------------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| `corePoolSize`                      | 核心线程数；线程池长期保留的线程数量，即使空闲也不会回收（除非设置`allowCoreThreadTimeOut`） | CPU密集型设为CPU核心数±1；IO密集型传统设为2倍CPU核数，JDK21优先用虚拟线程 |
| `maximumPoolSize`                   | 最大线程数；线程池允许创建的最大线程数量                     | 禁止设为`Integer.MAX_VALUE`，避免耗尽系统资源                |
| `keepAliveTime`                     | 非核心线程空闲存活时间；超过时间空闲的非核心线程会被回收     | 配合任务波峰波谷调整，避免频繁创建销毁线程                   |
| `TimeUnit unit`                     | 存活时间单位                                                 | 生产常用秒、毫秒                                             |
| `BlockingQueue<Runnable> workQueue` | 任务等待队列；核心线程满了之后，任务先进入队列排队           | 禁止使用无界队列，避免OOM；生产常用有界队列、延迟队列        |
| `ThreadFactory threadFactory`       | 线程创建工厂；用于自定义线程名称、优先级、是否守护线程       | 必须自定义线程名前缀，方便故障排查与监控                     |
| `RejectedExecutionHandler handler`  | 拒绝策略；队列和最大线程都满时，对新任务的处理方式           | 生产禁止默认抛异常，需配合降级策略                           |

**执行流程（面试必背）**：
任务提交 → 核心线程未满 → 创建核心线程执行
→ 核心线程已满 → 任务进入等待队列
→ 队列已满 → 创建非核心线程执行，直到最大线程数
→ 达到最大线程数 → 触发拒绝策略

### 2. 四种标准拒绝策略

| 策略                  | 行为                                           | 适用场景                                           |
| --------------------- | ---------------------------------------------- | -------------------------------------------------- |
| `AbortPolicy`         | 默认策略，抛出`RejectedExecutionException`异常 | 严格限流场景，不允许任务丢失                       |
| `CallerRunsPolicy`    | 由提交任务的调用者线程同步执行该任务           | 流量削峰、降级执行；保护线程池不被冲垮，任务不丢弃 |
| `DiscardPolicy`       | 直接静默丢弃新任务，不抛异常                   | 非核心任务、可丢失场景，如日志上报                 |
| `DiscardOldestPolicy` | 丢弃队列中最老的等待任务，重新提交当前任务     | 优先级高的新任务场景，如最新消息覆盖旧消息         |

### 3. JDK 21 线程池重要变更

1. **虚拟线程池正式支持**：新增`Executors.newVirtualThreadPerTaskExecutor()`，每个任务分配一个虚拟线程，不存在核心/最大线程、队列概念，天然支持百万级IO并发。
2. **AutoCloseable支持**：`ThreadPoolExecutor`实现`AutoCloseable`接口，可通过`try-with-resources`自动关闭线程池。
3. 优化了线程池在虚拟线程场景下的调度逻辑，减少载体线程的上下文切换开销。

### 4. 工程最佳实践与避坑

- **禁止用`Executors`创建线程池**：`FixedThreadPool`/`SingleThreadPool`队列无界易OOM；`CachedThreadPool`最大线程无上限易耗尽系统资源。
- **线程命名强制规范**：自定义`ThreadFactory`指定前缀，如`order-pool-%d`，排查问题时可快速定位线程归属。
- **生产级参数配置参考**：
  - CPU密集型：核心线程 = CPU核心数 + 1
  - IO密集型（传统平台线程）：核心线程 = CPU核心数 × 2 ~ 4，需压测验证
  - IO密集型（JDK21）：优先使用虚拟线程，无需池化，每个任务一个线程
- **必须监控**：活跃线程数、队列堆积长度、任务完成数、拒绝次数、最大等待时间

#### 💻 生产级线程池配置示例（JDK21 平台线程）

```
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPoolConfig {
    public static ThreadPoolExecutor buildBusinessPool() {
        AtomicInteger threadNumber = new AtomicInteger(1);
        return new ThreadPoolExecutor(
                8,  // 核心线程数
                16, // 最大线程数
                60, // 空闲存活时间
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(200), // 有界队列，防止OOM
                r -> new Thread(r, "business-pool-" + threadNumber.getAndIncrement()),
                new ThreadPoolExecutor.CallerRunsPolicy() // 调用者降级，不丢任务
        );
    }
}
```

---

## 二、synchronized 锁升级机制（JDK21 版）

>
> **重要纠正**：JDK 15 起偏向锁被正式废弃并默认关闭，JDK21中不存在偏向锁阶段，大量旧教程仍在讲解偏向锁属于过时内容。
> 实现规范参考：《Java Language Specification 第17章 线程与锁》

### 1. 锁的载体：对象头 Mark Word

`synchronized`锁信息存储在对象头的**Mark Word**中，32位JVM占32位，64位JVM占64位，存储内容包括：锁状态标记位、GC分代年龄、对象哈希码、锁指针等。
锁升级本质就是Mark Word中存储内容和状态的切换过程，整个过程由JVM自动完成，对开发者透明。

### 2. 锁升级完整路径（JDK21）

#### 阶段1：无锁状态

对象刚创建时，处于无锁状态，Mark Word存储对象哈希码、GC分代年龄，锁标记位为01。

#### 阶段2：轻量级锁（自适应自旋锁）

- 触发：第一个线程竞争锁时，JVM在当前线程的栈帧中创建锁记录，通过CAS操作将对象的Mark Word替换为指向该锁记录的指针。
- 自旋等待：CAS失败时，线程不会立刻阻塞，而是**自适应自旋**：根据上一次同锁的自旋成功率动态调整自旋次数，自旋成功则获得锁。
- 升级条件：自旋超过一定次数、或另一个线程同时竞争，升级为重量级锁。
- 特点：用户态执行，不阻塞线程，性能高；适合锁持有时间短、竞争不激烈的场景。

#### 阶段3：重量级锁

- 触发：竞争激烈、自旋失败时，锁升级为重量级锁，依赖操作系统内核的互斥量（mutex）实现。
- 行为：获取锁失败的线程会被挂起阻塞，进入内核态，发生上下文切换。
- 特点：性能开销大，但不会持续消耗CPU；适合锁持有时间长、竞争激烈的场景。

### 3. JVM 内置锁优化

1. **锁消除**：通过逃逸分析判定对象不会逃逸出方法，自动消除方法内的加锁操作，比如方法内局部的StringBuffer加锁会被直接消除。
2. **锁粗化**：连续多次对同一个对象加锁解锁，JVM会自动合并为一次大范围的加锁，减少反复加锁解锁的开销。
3. **锁降级**：GC安全点时，重量级锁可以降级为轻量级锁，减少持续的内核开销。

### 4. synchronized 核心特性

- **可重入**：同一个线程可以多次获取同一把锁，Mark Word中维护重入计数器，加锁递增，解锁递减，计数为0才真正释放锁。
- **内存语义**：加锁时清空线程工作内存，从主存读取最新数据；解锁时将工作内存数据刷回主存，保证可见性。
- **非公平性**：默认非公平锁，新来的线程可以直接插队竞争锁，吞吐量高于公平锁。

### 面试高频对比：synchronized vs ReentrantLock

| 维度       | synchronized       | ReentrantLock                       |
| ---------- | ------------------ | ----------------------------------- |
| 实现层面   | JVM内置，关键字    | JDK类库，API层面                    |
| 锁公平性   | 默认非公平，不可改 | 支持公平/非公平，构造参数指定       |
| 锁中断     | 不可中断           | 支持`lockInterruptibly()`可中断等待 |
| 超时获取   | 不支持             | 支持`tryLock(timeout)`超时返回      |
| 多条件变量 | 仅一个wait/notify  | 支持多个Condition，精准唤醒         |
| 锁状态     | 不可直接查看       | 可通过`isLocked()`等方法查看        |

---

## 三、中高级Java开发者必掌握并发核心知识

### 1. 虚拟线程（Virtual Threads）- JDK21 核心特性

- **原理**：JVM管理的轻量级用户线程，少量平台线程（载体线程）作为调度器，IO阻塞时自动将虚拟线程从载体线程卸载，唤醒时重新挂载，实现海量虚拟线程复用少量平台线程。
- **核心优势**：内存开销极小（每个虚拟线程栈KB级），百万级并发也不会耗尽系统资源；上下文切换在用户态完成，无需内核介入。
- **适用场景**：IO密集型任务（数据库调用、网络请求、Redis/模型接口调用），AI应用中大量并发IO场景收益最大。
- **不适用场景**：CPU密集型任务，不会提升性能，反而增加调度开销。
- **最佳实践**：不要池化虚拟线程，每个任务创建一个；优先使用`Executors.newVirtualThreadPerTaskExecutor()`。

### 2. 结构化并发（Structured Concurrency）- JDK21 正式特性

- **解决的痛点**：传统线程池异步任务生命周期与父线程解绑，父线程取消/异常时子线程继续运行，造成资源泄漏、结果不一致。
- **核心思想**：任务有明确的作用域，所有子任务的生命周期不超过父任务的作用域，统一取消、统一异常处理、统一等待完成。
- **核心API**：`StructuredTaskScope`，内置两种策略：
  - `ShutdownOnFailure`：任意一个子任务失败，立即取消所有其他子任务
  - `ShutdownOnSuccess`：任意一个子任务成功，立即取消所有其他子任务

#### 💻 结构化并发代码示例

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

### 3. Java内存模型（JMM）与 volatile

- **JMM核心目标**：规范多线程环境下的内存可见性、有序性、原子性，屏蔽不同硬件架构的差异。
- **三大特性**：
  - 原子性：一个操作不可中断，要么全部执行要么不执行
  - 可见性：一个线程修改了变量，其他线程能立刻看到最新值
  - 有序性：禁止指令重排序
- **happens-before 规则（面试必背）**：
  1. 程序顺序规则：一个线程内，前面的操作happens-before后面的操作
  2. 监视器锁规则：解锁操作happens-before后续的加锁操作
  3. volatile规则：对volatile变量的写happens-before后续对该变量的读
  4. 线程启动规则：线程start()方法happens-before线程内的所有操作
  5. 线程终止规则：线程内所有操作happens-before线程终止检测
  6. 传递性：A happens-before B，B happens-before C，则A happens-before C
- **volatile 两大作用**：保证内存可见性；禁止指令重排序（通过内存屏障实现）。
- **经典误区**：volatile只保证可见性和有序性，**不保证原子性**，不能替代锁。

### 4. CAS 与无锁编程

- **CAS原理**：Compare And Swap，CPU级原子指令；包含三个操作数：内存地址、预期旧值、新值；内存值等于预期旧值时才更新为新值，否则失败重试。
- **ABA问题**：值从A变成B再变回A，CAS误以为没有变化；解决方案：`AtomicStampedReference`增加版本号，每次修改版本号递增。
- **原子工具类**：
  - 基础原子类：`AtomicInteger`、`AtomicLong`、`AtomicReference`
  - 高并发计数器：`LongAdder`、`LongAccumulator`，分段累加，高并发下性能远超AtomicLong，适合统计计数场景
- **VarHandle**：JDK9引入，JDK21推荐的底层原子操作工具，替代`Unsafe`类，支持各种数据类型的原子操作、内存屏障控制，性能更优、使用更安全。

### 5. 高级并发同步工具

| 工具类                   | 核心作用                           | 适用场景                             |
| ------------------------ | ---------------------------------- | ------------------------------------ |
| `ReentrantReadWriteLock` | 读写锁，读共享、写独占             | 读多写少场景，如配置缓存、元数据读取 |
| `StampedLock`            | 读写锁增强，支持乐观读模式         | 读极多写极少场景，性能比读写锁高     |
| `CountDownLatch`         | 倒计时门闩，一个线程等多个线程完成 | 主线程等待多个子任务初始化完成       |
| `CyclicBarrier`          | 循环栅栏，多个线程互相等待，可复用 | 多阶段批量任务同步，如数据分批计算   |
| `Semaphore`              | 信号量，控制同时访问的并发数       | 限流、资源池控制                     |
| `Phaser`                 | 阶段同步器，支持动态调整参与数     | 多阶段、可动态变更的复杂任务同步     |

### 6. ThreadLocal 与内存泄漏

- **原理**：每个Thread对象维护一个`ThreadLocalMap`，key是ThreadLocal的弱引用，value是线程本地变量值。
- **内存泄漏原因**：key是弱引用，GC会回收key，但value是强引用；如果线程长期存活（如线程池线程），value永远无法回收，造成泄漏。
- **解决方案**：使用完必须调用`remove()`方法，配合`try-finally`保证执行。
- **JDK21优化**：优化了虚拟线程下的ThreadLocal存储结构，支持批量清理，减少内存泄漏风险。

### 7. 并发编程经典问题

- **死锁**：四个必要条件：互斥、占有且等待、不可剥夺、循环等待；排查工具：jstack、Arthas；解决：固定加锁顺序破坏循环等待、使用超时锁。
- **活锁**：线程不断重试但一直失败，不阻塞也无法推进；解决：增加随机等待时间。
- **饥饿**：线程长期抢不到锁，无法执行；解决：使用公平锁。
- **伪共享**：多个变量位于同一个CPU缓存行，修改时互相导致缓存失效；解决：`@Contended`注解填充缓存行，JDK21原生支持。
- **集合安全误区**：`ConcurrentHashMap`仅保证原子操作安全，先检查后执行的复合操作（如不存在则添加）仍不安全，需要额外加锁。

---

### ⚠️ 坑点 & 面试高频考点

#### 面试八股必背

1. **线程池核心参数有哪些？执行流程是什么？**

>
> 答题要点：7大参数；执行流程按核心线程→队列→非核心线程→拒绝策略四步描述。
2. **synchronized锁升级过程？JDK21有什么变化？**

>
> 答题要点：无锁→轻量级自适应自旋锁→重量级锁；JDK21废弃了偏向锁，直接从轻量级锁开始。
3. **volatile的作用？能保证原子性吗？**

>
> 答题要点：保证可见性、禁止指令重排；不保证原子性；结合happens-before规则说明。
4. **虚拟线程和平台线程的区别？适用什么场景？**

>
> 答题要点：JVM管理vs内核管理，轻量vs重量，IO密集型收益大，CPU密集型不适用。

#### 工程避坑红线

1. 线程池必须使用有界队列，禁止无界队列；最大线程数禁止设为无限大。
2. ThreadLocal用完必须remove，线程池场景下必然内存泄漏。
3. 不要池化虚拟线程，虚拟线程设计目标就是每个任务一个，池化反而降低性能。
4. CAS操作必须考虑ABA问题，涉及状态变更场景加版本号。

### 📝 记忆简语

>
> 线程池七参数，核心队列最大线，工厂时间拒绝策；四步执行流程，生产要有界。
> 同步锁升三级，无锁轻量重量级；二一废弃偏向锁，自旋自适应。
> 虚线程轻量IO强，结构化并发管生命周期；JMM可见有序原子性，volatile不保证原子。
> CAS无锁避ABA，ThreadLocal用完删；死锁破循环，伪共享补缓存行。

---

## ✍️ 随堂抽测（3题，作答即可批改）

1. 线程池执行任务的完整流程是什么？为什么生产环境不推荐用Executors创建线程池？
2. synchronized在JDK21中的锁升级路径是什么？偏向锁为什么被废弃？
3. volatile关键字有什么作用？为什么不能保证原子性？请结合JMM说明。