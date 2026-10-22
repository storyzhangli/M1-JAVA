# Java 核心 API 工程实践：Lambda、Stream、Optional、日期时间、IO 与资源管理

技术基线：JDK 21 LTS

---

## 一、学习目标与知识地图

### 1.1 学习目标

1. 填平 JDK8→JDK21 核心 API 代差，掌握函数式编程、流处理、空值安全、现代日期时间、IO 资源管理的工程化用法
2. 建立「API 选型→边界识别→异常处理→性能风险」的工程化思维，拒绝 API 死记硬背
3. 同步覆盖面试高频考点与生产最佳实践，直接适配企业后端开发与 AI 工程化场景
4. 所有示例与 API 均匹配 JDK 21 版本，明确标注版本差异与升级要点

### 1.2 知识地图

```
Java核心API工程实践（JDK21）
├─ 函数式编程
│  ├─ Lambda表达式
│  ├─ 函数式接口体系
│  └─ 方法引用
├─ Stream流处理
│  ├─ 执行机制与惰性求值
│  ├─ 中间/终止操作
│  ├─ Collectors聚合
│  └─ 并行流与性能边界
├─ Optional空值处理
│  ├─ 核心API
│  ├─ 链式调用
│  └─ 使用边界
├─ 日期时间API
│  ├─ 核心类型体系
│  ├─ 时区与时间语义
│  └─ 工程场景选型
└─ IO与资源管理
   ├─ 字节/字符流
   ├─ NIO.2文件系统
   ├─ try-with-resources
   └─ 文件安全与工程边界
```

---

## 二、Lambda 表达式与函数式编程

### 2.1 Lambda 表达式核心定义

Lambda 是 Java 8 引入的函数式编程语法，用于简洁地表示可传递的匿名函数，本质是「可作为参数传递的代码块」，替代单方法匿名内部类的冗余写法。

官方规范：**Java Language Specification, Java SE 21, Chapter 15.27 Lambda Expressions | Java SE 21《Java语言规范》第15.27节 Lambda表达式**：[https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.27](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.27)

#### 2.1.1 语法结构

```
(参数列表) -> { 方法体 }
```

- 参数类型可省略，编译器可通过目标类型自动推断
- 单参数可省略括号
- 单语句方法体可省略大括号与 `return`

代码示例：

```
// 完整写法
Comparator<Integer> fullComparator = (Integer a, Integer b) -> {
    return a.compareTo(b);
};

// 简化写法（生产推荐）
Comparator<Integer> simpleComparator = (a, b) -> a.compareTo(b);
```

#### 2.1.2 Lambda 与匿名内部类的核心区别

| 对比维度  | 匿名内部类                                             | Lambda 表达式                                               |
| --------- | ------------------------------------------------------ | ----------------------------------------------------------- |
| 编译本质  | 编译后生成独立 `.class` 文件                           | 编译后生成字节码，由 `invokedynamic` 指令调用，无额外类文件 |
| this 指向 | 指向匿名类实例本身                                     | 指向外部类实例，与外部方法 `this` 语义一致                  |
| 适用范围  | 可实现任意接口（含多方法）、抽象类                     | 仅能实现函数式接口（单抽象方法）                            |
| 变量捕获  | 局部变量需 `final` / effectively final，成员变量可修改 | 局部变量必须为 `final` / effectively final，成员变量可修改  |
| 创建开销  | 类加载、实例创建开销高                                 | 调用更轻量，创建与执行开销更低                              |

### 2.2 函数式接口（Functional Interface）

#### 2.2.1 定义与规则

函数式接口：**有且仅有一个抽象方法的接口**，可包含默认方法、静态方法、`Object` 类的公有方法（如 `equals`、`toString`）。

- `@FunctionalInterface`：可选编译期注解，作用是校验接口是否符合函数式接口规范，防止后续误加抽象方法破坏契约。

#### 2.2.2 JDK 内置核心函数式接口

官方参考：**Package java.util.function, Java SE 21 | Java SE 21 `java.util.function` 函数式接口包**：[https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/function/package-summary.html](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/function/package-summary.html)

| 接口                | 抽象方法              | 核心作用                          | 典型业务场景         |
| ------------------- | --------------------- | --------------------------------- | -------------------- |
| `Predicate<T>`      | `boolean test(T t)`   | 条件判断，返回布尔值              | 集合过滤、参数校验   |
| `Function<T, R>`    | `R apply(T t)`        | 类型转换，输入 T 输出 R           | 对象转 DTO、字段提取 |
| `Consumer<T>`       | `void accept(T t)`    | 消费输入，无返回值                | 遍历执行、日志打印   |
| `Supplier<T>`       | `T get()`             | 提供结果，无输入                  | 延迟求值、默认值生成 |
| `UnaryOperator<T>`  | `T apply(T t)`        | 同类型一元转换，继承自 Function   | 数值运算、字符串处理 |
| `BinaryOperator<T>` | `T apply(T t1, T t2)` | 同类型二元运算，继承自 BiFunction | 求和、数据合并       |

扩展双参数版本：`BiPredicate<T,U>`、`BiFunction<T,U,R>`、`BiConsumer<T,U>`，逻辑与上述一致，仅支持两个入参。

### 2.3 方法引用（Method Reference）

方法引用是 Lambda 的语法糖，当 Lambda 体仅调用一个已有方法时，可直接引用该方法，进一步提升可读性与可维护性。

官方规范：**Java Language Specification, Java SE 21, Chapter 15.13 Method Reference Expressions | Java SE 21《Java语言规范》第15.13节 方法引用表达式**：[https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.13](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.13)

四种标准形式：

1. **静态方法引用**：`类名::静态方法名`

```
Function<String, Integer> lambda = s -> Integer.parseInt(s);
Function<String, Integer> ref = Integer::parseInt;
```
2. **特定对象的实例方法引用**：`对象实例::实例方法名`

```
String str = "hello";
Supplier<Integer> lambda = () -> str.length();
Supplier<Integer> ref = str::length;
```
3. **特定类型的任意对象实例方法引用**：`类名::实例方法名`

```
Function<String, Integer> lambda = s -> s.length();
Function<String, Integer> ref = String::length; // 第一个参数作为方法调用者
```
4. **构造器引用**：`类名::new`

```
Supplier<User> lambda = () -> new User();
Supplier<User> ref = User::new;
```

### 2.4 变量捕获与 effectively final

#### 2.4.1 核心规则

Lambda 表达式可以捕获外部的局部变量，但该变量必须是 **`final` 修饰** 或 **effectively final（事实上不可变）**，即变量初始化后没有被重新赋值。

- 底层原理：局部变量存储在栈帧中，Lambda 执行时栈帧可能已销毁，因此 Lambda 实际使用的是变量的副本；为保证数据一致性，要求变量不可变。
- 成员变量（实例/静态变量）不受此限制，存储在堆中，生命周期与对象一致。

#### 2.4.2 常见误区

错误示例（编译失败）：

```
int count = 0;
List<Integer> list = Arrays.asList(1,2,3);
list.forEach(i -> count++); // 编译错误：count 必须是 final 或 effectively final
```

修复方案：使用原子类/数组包装，或改用 Stream 的 `collect`/`reduce` 做聚合；生产环境禁止通过共享可变变量在 Lambda 中累计结果。

### 2.5 工程边界与核心误区

1. **Lambda ≠ 异步/并行**：Lambda 只是语法简化，本身不会创建线程，也不会自动并行执行，执行线程由调用方决定。
2. **避免过度复杂的 Lambda**：Lambda 体超过 3 行时，建议抽取为独立方法，改用方法引用，提升可读性与可测试性。
3. **受检异常处理**：JDK 内置函数式接口均未声明受检异常，不能直接在 Lambda 中抛出受检异常，需手动捕获或自定义声明异常的函数式接口。
4. **副作用控制**：尽量避免在 Lambda 中修改外部状态，函数式编程推荐纯函数（无副作用、输入确定输出），降低并发风险与调试成本。

### 2.6 面试高频考点

1. 什么是函数式接口？`@FunctionalInterface` 的作用是什么？
2. Lambda 和匿名内部类的核心区别有哪些？
3. 为什么 Lambda 捕获的局部变量必须是 `final` / effectively final？
4. 方法引用有哪几种形式？分别举一个业务场景示例。

### 2.7 随堂练习

**练习**：将以下匿名内部类依次改写为 Lambda、方法引用

```
Runnable runnable = new Runnable() {
    @Override
    public void run() {
        System.out.println("task execute");
    }
};
```

**参考答案**：

```
// Lambda 写法
Runnable runnableLambda = () -> System.out.println("task execute");

// 方法引用（需先定义静态方法 printTask）
// public static void printTask() { System.out.println("task execute"); }
Runnable runnableRef = LambdaDemo::printTask;
```

---

## 三、Stream API 与集合数据处理

### 3.1 Stream 基础与执行机制

官方参考：**Package java.util.stream, Java SE 21 | Java SE 21 `java.util.stream` 流处理 API**：[https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html)

#### 3.1.1 核心定义

Stream 是 Java 8 引入的「数据元素序列的流式处理抽象」，用于对集合数据进行声明式、链式的聚合操作，核心是**计算逻辑的抽象**，而非数据存储。

- Stream ≠ 集合：Stream 不存储数据，只是从数据源（集合、数组、文件等）获取数据并执行计算
- 声明式编程：只定义「做什么」，不定义「怎么做」，底层自动优化执行流程

#### 3.1.2 执行机制：惰性求值

Stream 操作分为两类，共同构成「操作链」：

1. **中间操作（Intermediate Operations）**：返回新的 Stream，仅构建操作管道，不立即执行，具有惰性。如 `filter`、`map`、`sorted` 等。
2. **终止操作（Terminal Operations）**：触发实际计算，产生最终结果，执行后 Stream 被消费，无法复用。如 `collect`、`forEach`、`count` 等。

>
> 核心特性：只有遇到终止操作时，中间操作才会真正按优化后的顺序执行；没有终止操作的 Stream 链没有任何实际效果。

#### 3.1.3 Stream 的创建方式

```
// 1. 集合创建（生产最常用）
Stream<String> stream1 = list.stream();

// 2. 静态方法创建
Stream<String> stream2 = Stream.of("a", "b", "c");
Stream<Integer> emptyStream = Stream.empty();

// 3. 数组创建
int[] arr = {1,2,3};
IntStream stream3 = Arrays.stream(arr);

// 4. 无限流
Stream<Integer> iterateStream = Stream.iterate(0, n -> n + 2); // 迭代生成
Stream<Double> generateStream = Stream.generate(Math::random); // 生成式
```

#### 3.1.4 有状态 vs 无状态操作

- 无状态操作：元素处理不依赖其他元素，如 `filter`、`map`，每个元素独立处理
- 有状态操作：处理当前元素需要依赖其他元素的状态，如 `sorted`、`distinct`、`limit`，需要缓存全部/部分元素才能继续，大数量下内存开销更高

### 3.2 常用中间操作

| 操作方法                          | 核心作用                             | 输入输出    | 典型场景                     | 常见误区                                 |
| --------------------------------- | ------------------------------------ | ----------- | ---------------------------- | ---------------------------------------- |
| `filter(Predicate)`               | 过滤符合条件的元素                   | T→T         | 条件筛选                     | 条件内不要嵌入重 IO 与副作用             |
| `map(Function)`                   | 元素类型转换/字段映射                | T→R         | 对象转 DTO、提取字段         | 避免 map 中执行数据库查询等重操作        |
| `flatMap(Function)`               | 扁平化：元素转流再合并               | T→Stream<R> | 嵌套集合展开、一对多数据展开 | 注意底层流的资源关闭                     |
| `distinct()`                      | 去重，基于 `equals/hashCode`         | T→T         | 数据去重                     | 大集合下性能与内存开销高                 |
| `sorted()` / `sorted(Comparator)` | 元素排序                             | T→T         | 结果排序                     | 有状态操作，大内存占用                   |
| `limit(long n)`                   | 截取前 n 个元素                      | T→T         | 分页、限制结果数             | 短路操作，配合 sorted 注意顺序语义       |
| `skip(long n)`                    | 跳过前 n 个元素                      | T→T         | 分页                         | 大数据量下深度 skip 效率低               |
| `peek(Consumer)`                  | 遍历执行操作，不改变元素，仅用于调试 | T→T         | 打印日志、调试排错           | 禁止作为主业务逻辑，并行流不保证执行次数 |
| `takeWhile(Predicate)`            | 取元素直到条件不成立（JDK9+）        | T→T         | 有序流的前置截取             | 无序流结果不确定                         |
| `dropWhile(Predicate)`            | 跳过元素直到条件不成立（JDK9+）      | T→T         | 跳过前置无效数据             | 同 takeWhile                             |

#### 重点辨析：map vs flatMap

- `map`：**一对一**转换，每个输入元素对应一个输出元素
- `flatMap`：**一对多再扁平化**，每个输入元素转为一个流，最终所有流合并为一个输出流

代码示例：

```
List<User> userList = ...;

// map 得到嵌套流：Stream<List<Order>>
Stream<List<Order>> nestedStream = userList.stream()
        .map(User::getOrderList);

// flatMap 扁平化：Stream<Order>
Stream<Order> flatStream = userList.stream()
        .flatMap(user -> user.getOrderList().stream());
```

### 3.3 常用终止操作

#### 3.3.1 遍历与匹配

- `forEach(Consumer)`：遍历元素，并行流下不保证顺序
- `forEachOrdered(Consumer)`：按元素 encounter order 顺序遍历，并行流下也保证顺序
- `anyMatch(Predicate)`：任意元素匹配即返回 true，短路操作
- `allMatch(Predicate)`：所有元素匹配才返回 true，短路操作
- `noneMatch(Predicate)`：所有元素都不匹配才返回 true，短路操作
- `findFirst()`：返回第一个元素，结果为 `Optional<T>`
- `findAny()`：返回任意一个元素，并行流下性能更好，结果为 `Optional<T>`

>
> 面试考点：空流上调用 `allMatch` 返回 true（空真），`noneMatch` 也返回 true，属于逻辑上的空洞真理（vacuous truth）。

#### 3.3.2 聚合与归约

- `count()`：返回元素总数
- `min(Comparator)` / `max(Comparator)`：返回最小/最大值，结果为 `Optional<T>`
- `reduce`：归约操作，将所有元素合并为一个结果```
```java
// 无初始值，返回 Optional
Optional<Integer> sum = list.stream().reduce(Integer::sum);
// 带初始值，不会为空
Integer sumWithSeed = list.stream().reduce(0, Integer::sum);
```
### 3.4 Collectors 与数据聚合

`collect` 是最灵活的终止操作，将流元素收集为集合、聚合结果等，配合 `Collectors` 工具类使用。

#### 3.4.1 核心收集器

| 收集器                                      | 核心作用                                     | 生产注意事项                                           |
| ------------------------------------------- | -------------------------------------------- | ------------------------------------------------------ |
| `Collectors.toList()`                       | 收集为 List                                  | 不保证具体实现类；返回列表可修改，允许 null 元素       |
| `Collectors.toSet()`                        | 收集为 Set                                   | 自动去重，不保证顺序                                   |
| `Collectors.toMap(k, v)`                    | 收集为 Map                                   | key 重复时抛出 `IllegalStateException`，生产禁用该重载 |
| `Collectors.toMap(k, v, mergeFunc)`         | 带合并策略的 Map 收集                        | 生产环境必须使用该重载，处理重复键                     |
| `Collectors.groupingBy(classifier)`         | 按分类器分组，返回 `Map<K, List<T>>`         | 最常用的分组 API                                       |
| `Collectors.partitioningBy(predicate)`      | 按布尔条件分区，返回 `Map<Boolean, List<T>>` | groupingBy 的特殊情况，仅两组                          |
| `Collectors.joining(delimiter)`             | 字符串拼接                                   | 仅适用于 `Stream<String>`                              |
| `Collectors.summarizingInt()`               | 数值统计，返回 `IntSummaryStatistics`        | 一次性得到 count、sum、min、max、avg                   |
| `Collectors.collectingAndThen(c, finisher)` | 收集后再执行转换                             | 比如收集后转不可变集合                                 |

#### 3.4.2 版本重点：JDK16+ `Stream.toList()` 与 `Collectors.toList()` 的差异

- `Stream.toList()`（JDK16 引入，JDK21 保留）：返回**不可修改的 List**，不允许 add/remove 操作，元素不允许为 null（传入 null 会抛 `NullPointerException`）
- `Collectors.toList()`：返回可修改的 List（具体实现不保证），允许 null 元素

>
> 工程建议：结果仅用于读取时，优先使用 `Stream.toList()`，不可变性带来线程安全与语义清晰；需要后续修改时使用 `Collectors.toList()`。

#### 3.4.3 toMap 重复键处理

生产错误写法（禁用）：

```
// 订单号重复时直接抛出异常
Map<String, Order> orderMap = orderList.stream()
        .collect(Collectors.toMap(Order::getOrderNo, Function.identity()));

```
生产标准写法：
```
// 重复键时保留后者
Map<String, Order> orderMap = orderList.stream()
        .collect(Collectors.toMap(
                Order::getOrderNo,
                Function.identity(),
                (oldVal, newVal) -> newVal
        ));
```
#### 3.4.4 reduce 与 collect 的区别

- `reduce`：不可变归约，每次计算返回新对象，适合简单数值聚合，大对象场景下创建对象开销高
- `collect`：可变收集，使用可变容器（如 ArrayList）累积结果，适合集合收集、复杂聚合，性能更好
- 选型规则：简单数值聚合用 reduce，集合/复杂对象聚合用 collect

### 3.5 并行流与性能边界

#### 3.5.1 基本概念

并行流通过 `parallelStream()` 或 `stream().parallel()` 创建，底层使用 Fork/Join 框架，将任务拆分到多个线程执行。

- 核心原则：并行流 ≠ 一定更快，任务拆分、线程调度、结果合并都有固定开销。

#### 3.5.2 核心风险与边界

1. **线程安全问题**：并行流中修改外部共享变量会出现数据竞争，结果随机错误。
错误示例：

```
List<Integer> result = new ArrayList<>();
list.parallelStream().forEach(result::add); // 线程不安全，结果不可预期
```
修复：使用 collect 收集结果，避免外部共享容器。
2. **非干扰性原则**：流执行过程中，不能修改数据源（如增删集合元素），否则结果不确定或抛出异常。
3. **性能适用场景**
   - 适合：数据量大、CPU 密集型计算、任务可均匀拆分
   - 不适合：数据量小、IO 密集型、任务拆分开销大、共享状态多
   - 铁则：没有性能测试数据支撑时，默认使用顺序流，禁止盲目并行化。
4. 顺序差异：`forEach` 在并行流下不保证顺序，需要顺序时使用 `forEachOrdered`，但会损失并行性能。

### 3.6 面试高频考点

1. Stream 的惰性求值是什么？中间操作和终止操作的区别？
2. `map` 和 `flatMap` 的核心区别？各举一个适用场景。
3. `Collectors.toMap` 遇到重复键会怎么样？生产环境怎么处理？
4. JDK16 后 `Stream.toList()` 和 `Collectors.toList()` 有什么区别？
5. `reduce` 和 `collect` 的区别？选型原则是什么？
6. 并行流有什么风险？什么时候适合使用并行流？

### 3.7 随堂练习

**需求**：筛选金额>100 的已支付订单，按创建时间降序排列，提取订单号转为不可变 List。
参考答案：

```
List<String> orderNoList = orderList.stream()
        .filter(order -> "PAID".equals(order.getStatus()))
        .filter(order -> order.getAmount().compareTo(BigDecimal.valueOf(100)) > 0)
        .sorted(Comparator.comparing(Order::getCreateTime).reversed())
        .map(Order::getOrderNo)
        .toList(); // JDK16+，返回不可变列表
```
---

## 四、Optional 与空值处理

官方参考：**Class Optional, Java SE 21 | Java SE 21 `Optional` 可选值容器**：[https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Optional.html](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Optional.html)

### 4.1 核心定义

`Optional<T>` 是 Java 8 引入的**值容器类**，用于显式表达「值可能不存在」的语义，替代隐性 null，减少空指针异常（NPE），提升 API 契约的清晰度。

- 核心思想：用类型系统明确表达「可选值」，让编译器强制处理空值场景，降低人为遗漏风险。
- 重要边界：Optional 不能彻底消除 NPE，只是强制开发者显式处理空值场景。

### 4.2 核心创建方式

1. `Optional.empty()`：创建空 Optional，表达值不存在
2. `Optional.of(T value)`：创建非空 Optional，value 为 null 时直接抛 NPE；用于确定值一定非空的场景
3. `Optional.ofNullable(T value)`：允许 null，value 为 null 时返回 empty；用于值可能为空的场景

>
> 选型规则：明确值一定非空用 of，可能为空用 ofNullable，禁止无差别全用 ofNullable。

### 4.3 核心 API

#### 4.3.1 存在性判断与消费

- `isPresent()`：值存在返回 true，JDK8 引入
- `isEmpty()`：值不存在返回 true，JDK11 引入
- `ifPresent(Consumer)`：值存在时执行消费操作
- `ifPresentOrElse(Consumer, Runnable)`：值存在执行消费，不存在执行备选逻辑，JDK9 引入

#### 4.3.2 链式转换：map / flatMap

- `map(Function)`：值存在时执行转换，返回新的 Optional；值为空则返回 empty
  - 适用于转换结果不是 Optional 的场景
- `flatMap(Function)`：值存在时执行转换，转换函数返回 Optional，直接返回该 Optional，避免嵌套 Optional
  - 适用于转换结果本身就是 Optional 的场景

代码示例（嵌套空值处理）：

```
// 传统多层 null 判断
String city = null;
if (user != null) {
    Address address = user.getAddress();
    if (address != null) {
        city = address.getCity();
    }
}

// Optional 链式写法
String city = Optional.ofNullable(user)
        .map(User::getAddress)
        .map(Address::getCity)
        .orElse("未知城市");
```
>
> flatMap 示例：如果 `getAddress()` 返回 `Optional<Address>`

```
String city = Optional.ofNullable(user)
        .flatMap(User::getAddress) // 转换结果是 Optional，用 flatMap 避免嵌套
        .map(Address::getCity)
        .orElse("未知城市");
```
#### 4.3.3 值获取与兜底

- `orElse(T other)`：值存在返回值，否则返回默认值；**无论值是否存在，默认值都会被求值**
- `orElseGet(Supplier)`：值存在返回值，否则通过 Supplier 生成默认值；**仅值不存在时才执行 Supplier**，延迟求值
- `orElseThrow()`：值存在返回值，否则抛出 `NoSuchElementException`，JDK10 引入
- `orElseThrow(Supplier<Throwable>)`：值存在返回值，否则抛出自定义异常
- `or(Supplier<Optional>)`：值存在返回自身，否则返回 Supplier 生成的 Optional，JDK9 引入

#### 重点辨析：orElse vs orElseGet

```
// orElse：即使 user 存在，createDefaultUser() 也会执行，有性能损耗
User user1 = Optional.ofNullable(user)
        .orElse(createDefaultUser());

// orElseGet：仅 user 不存在时，才执行 createDefaultUser()
User user2 = Optional.ofNullable(user)
        .orElseGet(() -> createDefaultUser());
```
>
> 工程铁则：默认值创建有开销（IO、计算、对象创建）时，必须用 orElseGet；仅默认值是简单常量时可用 orElse。

### 4.4 使用边界与常见误区

#### 4.4.1 不推荐的用法

1. **不要用 Optional 作为方法参数**：会让调用方被迫包装，增加冗余，不如方法重载更清晰。
2. **不要用 Optional 作为类的字段**：增加序列化复杂度，内存开销更高，直接用 null + 校验更合适。
3. **不要用 Optional 包装集合**：集合本身就可以用空集合表达空语义，不需要 `Optional<List<T>>`。
4. **不要滥用 `isPresent()+get()`**：本质和 null 判断没有区别，完全失去 Optional 的价值。
5. **不要在 Optional 中存 null**：`Optional.ofNullable(null)` 得到 empty 是正常语义，但不要主动把 null 作为业务值存入 Optional。

#### 4.4.2 推荐用法

- 方法返回值：用于明确表达「可能返回空结果」的 API 契约，是 Optional 最核心的使用场景
- 链式空值处理：替代多层嵌套的 null 判断，提升代码可读性
- 结合 Stream 使用：`findFirst`、`findAny`、`min/max` 等操作默认返回 Optional，无缝衔接

### 4.5 面试高频考点

1. Optional 的设计目的是什么？能彻底避免 NPE 吗？
2. `map` 和 `flatMap` 的区别？分别在什么场景下使用？
3. `orElse` 和 `orElseGet` 的求值差异？选型原则是什么？
4. Optional 有哪些不推荐的使用场景？为什么？
5. `Optional.of()` 和 `ofNullable()` 的区别？

### 4.6 随堂练习

**需求**：将以下多层 null 判断重构为 Optional 链式写法

```
public String getUserDeptName(Long userId) {
    User user = userMapper.selectById(userId);
    if (user != null) {
        Dept dept = user.getDept();
        if (dept != null) {
            String name = dept.getName();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
    }
    return "未分配";
}
```
参考答案：
```
public String getUserDeptName(Long userId) {
    return Optional.ofNullable(userMapper.selectById(userId))
            .map(User::getDept)
            .map(Dept::getName)
            .filter(name -> !name.isEmpty())
            .orElse("未分配");
}
```
---

## 五、Java 日期时间 API

官方参考：**Package java.time, Java SE 21 | Java SE 21 `java.time` 日期时间 API**：[https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/package-summary.html](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/package-summary.html)

### 5.1 新旧 API 对比

#### 5.1.1 旧 API 的核心问题

- `java.util.Date`、`java.util.Calendar`：可变、线程不安全，API 设计混乱（月份从 0 开始），易用性极差
- `SimpleDateFormat`：线程不安全，多线程共用会出现格式错乱、异常
- 时区、偏移量处理混乱，极易出现线上时间 bug

#### 5.1.2 java.time API 的设计优势

- **不可变性**：所有核心类都是不可变对象，修改操作返回新对象，天然线程安全
- **类型清晰**：日期、时间、瞬时点、时区、时长严格区分，语义明确
- **线程安全**：格式化类 `DateTimeFormatter` 线程安全，可全局单例使用
- **完善的时区、夏令时支持**，自动处理边界场景

### 5.2 核心类型体系

#### 5.2.1 日期时间核心类

| 类型             | 语义                         | 典型业务场景                 | 包含信息                       |
| ---------------- | ---------------------------- | ---------------------------- | ------------------------------ |
| `LocalDate`      | 本地日期（无时区）           | 生日、业务日期、账单日       | 年-月-日                       |
| `LocalTime`      | 本地时间（无时区）           | 上班时间、营业时段           | 时:分:秒.纳秒                  |
| `LocalDateTime`  | 本地日期时间（无时区）       | 单时区后台系统展示           | 年-月-日 时:分:秒.纳秒         |
| `Instant`        | 时间线上的瞬时点（UTC 标准） | 日志时间戳、跨系统时间、排序 | 纳秒级时间戳，基于 UTC         |
| `ZonedDateTime`  | 带时区的日期时间             | 跨时区业务、全球化系统       | 日期时间 + 时区 ID             |
| `OffsetDateTime` | 带 UTC 偏移量的日期时间      | API 接口传输、数据库存储     | 日期时间 + 偏移量（如 +08:00） |

>
> 面试核心考点：`LocalDateTime` 不包含时区信息，**不能唯一确定全球某一瞬间**，因为相同时刻在不同时区的本地时间完全不同。跨时区、分布式系统必须使用 Instant 或带时区的类型。

#### 5.2.2 辅助工具类

- `ZoneId`：时区 ID，如 `Asia/Shanghai`
- `ZoneOffset`：时区偏移量，如 `+08:00`
- `Duration`：时间间隔，基于秒/纳秒，用于计算两个时间点的间隔（时分秒维度）
- `Period`：日期间隔，基于年/月/日，用于计算两个日期的间隔（年月日维度）
- `DateTimeFormatter`：日期时间格式化与解析，线程安全
- `TemporalAdjusters`：时间调整器，用于计算「当月第一天」「下一个周一」等复杂日期

#### 5.2.3 Duration vs Period 辨析

- `Duration`：处理「时间长度」，基于 Instant 或 LocalTime，精确到纳秒，支持时分秒毫秒
- `Period`：处理「日期长度」，基于 LocalDate，精确到天，支持年月日
- 常见坑：用 Duration 计算日期间隔会忽略夏令时、月份天数差异，结果不准确；日期间隔必须用 Period。

### 5.3 核心操作代码示例

#### 5.3.1 创建与获取

```
// 当前时间（默认时区）
LocalDate today = LocalDate.now();
LocalDateTime now = LocalDateTime.now();
Instant instant = Instant.now();
ZonedDateTime shanghaiNow = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));

// 指定时间
LocalDate date = LocalDate.of(2025, 10, 1);
LocalDateTime dateTime = LocalDateTime.of(2025, 10, 1, 14, 30, 0);
```
#### 5.3.2 格式化与解析

```
// 全局常量，线程安全
private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

// 格式化
String formatStr = now.format(FORMATTER);

// 解析
LocalDateTime parsed = LocalDateTime.parse("2025-10-01 14:30:00", FORMATTER);
```
#### 5.3.3 时间计算与调整

```
// 加减运算，返回新对象，原对象不变
LocalDate nextWeek = today.plusWeeks(1);
LocalDateTime twoHoursAgo = now.minusHours(2);

// 复杂调整：当月第一天、下一个周日
LocalDate firstDayOfMonth = today.with(TemporalAdjusters.firstDayOfMonth());
LocalDate nextSunday = today.with(TemporalAdjusters.next(DayOfWeek.SUNDAY));

```
#### 5.3.4 时区转换

```
// 本地时间转 Instant（必须指定时区）
LocalDateTime localDateTime = LocalDateTime.now();
Instant instant = localDateTime.atZone(ZoneId.of("Asia/Shanghai"))
        .toInstant();

// Instant 转不同时区时间
ZonedDateTime newYorkTime = instant.atZone(ZoneId.of("America/New_York"));
```
### 5.4 工程场景选型指南

| 业务场景                      | 推荐类型                                            | 选型原因                             |
| ----------------------------- | --------------------------------------------------- | ------------------------------------ |
| 用户生日、证件有效期          | `LocalDate`                                         | 仅需日期，与时区无关                 |
| 订单创建时间（单时区系统）    | `LocalDateTime`                                     | 内部系统统一时区，展示友好           |
| 订单创建时间（跨时区/分布式） | `Instant` / `OffsetDateTime`                        | 绝对时间，不受时区影响，传输存储一致 |
| 日志时间戳、埋点时间          | `Instant`                                           | 精确绝对时间，便于排序、跨地域比对   |
| 跨时区会议、全球化业务        | `ZonedDateTime`                                     | 明确时区，自动处理夏令时             |
| 接口对外传输                  | `OffsetDateTime`                                    | 携带偏移量，兼容性好，避免时区歧义   |
| 数据库存储                    | datetime 对应 LocalDateTime；timestamp 对应 Instant | 根据业务时区策略选择                 |

### 5.5 常见坑点与边界

1. **默认时区陷阱**：`LocalDateTime.now()` 使用 JVM 默认时区，不同服务器时区配置不同会导致结果不一致，生产环境建议显式指定时区。
2. **月末日期加减**：`plusMonths(1)` 会自动处理月末，比如 1 月 31 日加 1 个月得到 2 月 28/29 日，不会溢出。
3. **夏令时问题**：使用 `ZonedDateTime` 时，JDK 会自动处理夏令时导致的时间缺口/重复，禁止手动计算时区偏移。
4. **格式化字母易错点**：`yyyy` 是日历年，`YYYY` 是周年（跨年周会出错）；`HH` 是 24 小时制，`hh` 是 12 小时制，生产环境统一使用 `yyyy-MM-dd HH:mm:ss`。
5. **旧 API 互转**：`Date` 与 `Instant` 可通过 `date.toInstant()`、`Date.from(instant)` 互转，是新旧 API 转换的标准桥梁。

### 5.6 面试高频考点

1. `LocalDateTime` 和 `Instant` 的核心区别？
2. 为什么 `LocalDateTime` 不能确定全球唯一瞬间？
3. `Duration` 和 `Period` 的区别？分别适用什么场景？
4. `DateTimeFormatter` 和 `SimpleDateFormat` 的核心区别？
5. 跨时区系统应该用什么日期时间类型？为什么？

### 5.7 随堂练习

**需求**：计算 2025 年 1 月 31 日加 1 个月后的结果并解释；计算 2025-01-01 到 2025-10-01 的天数差。
参考答案：

```java
// 月末加月：自动适配月末，结果为 2025-02-28
LocalDate date = LocalDate.of(2025, 1, 31);
LocalDate nextMonth = date.plusMonths(1);

// 天数差
LocalDate start = LocalDate.of(2025, 1, 1);
LocalDate end = LocalDate.of(2025, 10, 1);
long days = ChronoUnit.DAYS.between(start, end); // 273 天
```
---

## 六、IO、NIO.2 与资源管理

### 6.1 IO 基础

官方参考：**Package java.io, Java SE 21 | Java SE 21 `java.io` 输入输出 API**：[https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/package-summary.html](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/package-summary.html)

#### 6.1.1 核心概念与分类

IO 即输入输出，用于程序与外部系统（文件、网络、其他进程）的数据交互，两大核心分类：

- **字节流**：以字节为单位，处理二进制数据（图片、视频、文件复制），基类 `InputStream` / `OutputStream`
- **字符流**：以字符为单位，处理文本数据，自动处理编码转换，基类 `Reader` / `Writer`

#### 6.1.2 选型原则

- 二进制文件（图片、音频、压缩包、可执行文件）：必须用字节流，保证数据完整性
- 文本文件（日志、配置、代码）：优先用字符流，显式指定字符集，避免乱码
- 字符集铁则：所有文本读写必须显式指定字符集，禁止依赖平台默认字符集，生产环境统一 UTF-8

#### 6.1.3 缓冲流

- `BufferedInputStream` / `BufferedOutputStream`、`BufferedReader` / `BufferedWriter`
- 作用：内置缓冲区，减少物理 IO 次数，大幅提升读写性能；大文件读写必须加缓冲
- 注意：缓冲流是装饰器模式，包装底层流，关闭缓冲流会自动关闭底层流

### 6.2 NIO 与 NIO.2

官方参考：

- **Package java.nio, Java SE 21 | Java SE 21 `java.nio` 缓冲区与 NIO 基础 API**：[https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/package-summary.html](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/package-summary.html)
- **Package java.nio.file, Java SE 21 | Java SE 21 `java.nio.file` 文件系统 API**：[https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/package-summary.html](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/package-summary.html)

#### 6.2.1 NIO 核心概念

NIO（New IO）是 Java 4 引入的新 IO 模型，核心是「通道(Channel)+缓冲区(Buffer)」，网络场景支持非阻塞 IO；文件场景下主要使用缓冲区提升性能。

- 重要边界：文件 NIO 默认还是阻塞的，非阻塞主要针对网络 `SocketChannel`，不要误以为 NIO = 非阻塞。

#### 6.2.2 ByteBuffer 核心属性

- `capacity`：容量，缓冲区总大小，创建后不变
- `position`：当前读写位置，从 0 开始
- `limit`：读写上限，position 不能超过 limit
- `flip()`：切换为读模式，limit 设为当前 position，position 设为 0
- `clear()`：切换为写模式，position 设为 0，limit 设为 capacity
- `rewind()`：重读模式，position 设为 0，limit 不变

代码示例：

```java
ByteBuffer buffer = ByteBuffer.allocate(1024);
// 写模式：写入数据
buffer.put("hello".getBytes(StandardCharsets.UTF_8));

// 切换为读模式
buffer.flip();

// 读取数据
byte[] bytes = new byte[buffer.remaining()];
buffer.get(bytes);
```
#### 6.2.3 NIO.2 文件系统 API（Java 7 引入，JDK21 增强）

NIO.2 是 Java 7 对文件系统 API 的重大升级，核心类：

- `Path`：替代 `File`，更灵活的路径抽象，原生支持跨平台
- `Paths`：Path 工具类，创建 Path 实例
- `Files`：静态工具类，提供几乎所有文件操作方法

核心常用 API：

```java
// 1. 路径操作
Path path = Paths.get("E:/data/test.txt");
Path absolutePath = path.toAbsolutePath();
Path parent = path.getParent();

// 2. 小文件便捷读写（JDK11+）
String content = Files.readString(path, StandardCharsets.UTF_8);
Files.writeString(path, content, StandardCharsets.UTF_8);

// 3. 缓冲流方式（大文件推荐）
BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);

// 4. 文件操作
Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
Files.move(sourcePath, targetPath, StandardCopyOption.ATOMIC_MOVE);
Files.deleteIfExists(path);
Files.createDirectories(dirPath);

// 5. 文件遍历（必须 try-with-resources 关闭）
try (Stream<Path> walk = Files.walk(dirPath, 3)) { // 遍历深度 3 层
    long fileCount = walk.filter(Files::isRegularFile).count();
}
```
>
> 生产红线：`Files.walk()`、`Files.lines()` 返回的 Stream 是资源型 Stream，底层持有文件句柄，**必须用 try-with-resources 关闭**，否则会造成文件句柄泄漏。

### 6.3 资源管理与异常处理

#### 6.3.1 try-with-resources 机制

Java 7 引入的自动资源管理机制，所有实现了 `AutoCloseable` 接口的资源都可以使用，自动在 try 块结束后关闭资源，无需手动 finally 关闭。

- `Closeable` 继承自 `AutoCloseable`，IO 流、通道等都实现了该接口

语法与示例：

```java
// 文件复制示例，自动关闭两个流
try (InputStream in = new FileInputStream("src.txt");
     OutputStream out = new FileOutputStream("dest.txt")) {
    byte[] buffer = new byte[8192];
    int len;
    while ((len = in.read(buffer)) != -1) {
        out.write(buffer, 0, len);
    }
} catch (IOException e) {
    log.error("文件复制失败", e);
}
```
#### 6.3.2 核心规则

1. **关闭顺序**：资源按声明的逆序关闭，先声明的后关闭，符合依赖顺序（先关外层、后关内层）。
2. **被抑制异常（Suppressed Exception）**：try 块抛出主异常，关闭资源时也抛出异常，关闭异常会被添加为主异常的抑制异常，通过 `e.getSuppressed()` 获取，不会丢失异常信息。
3. **资源所有权**：谁创建的资源谁负责关闭；共享资源、调用方传入的资源不要随意关闭，严格遵守 API 契约。

#### 6.3.3 try-with-resources vs finally

- try-with-resources：代码简洁，关闭逻辑正确，自动处理抑制异常，生产环境优先使用
- finally：手动关闭容易漏写、顺序错误、异常覆盖，仅在资源不支持 AutoCloseable 时使用

### 6.4 文件安全与工程边界

#### 6.4.1 路径遍历攻击（Path Traversal）

- 风险：用户传入文件路径时，通过 `../` 等方式越过允许目录，访问系统敏感文件
- 防护方案：
  1. 对用户输入路径进行规范化（normalize），解析出真实路径
  2. 校验真实路径是否在允许的根目录下
  3. 禁止直接字符串拼接路径，使用 `Path.resolve()`

代码示例：

```
public boolean isPathSafe(Path rootDir, String userInput) {
    Path targetPath = rootDir.resolve(userInput).normalize();
    return targetPath.startsWith(rootDir);
}
```
#### 6.4.2 大文件处理风险

- 禁止一次性读取整个大文件到内存（如 `Files.readAllBytes`、`Files.readString`），会导致 OOM
- 大文件必须使用缓冲流逐行/逐块读取，或使用 `FileChannel`、`MappedByteBuffer`
- 文本文件逐行处理使用 `BufferedReader.readLine()` 或 `Files.lines()`

#### 6.4.3 其他工程边界

1. **文件操作不是事务**：写入失败可能出现部分写入，不要假设原子性；需要原子性使用 `Files.move` 的 `ATOMIC_MOVE` 选项。
2. **目录前置校验**：写入文件前必须确保父目录存在，否则抛出 `NoSuchFileException`。
3. **异常处理**：文件操作必须捕获 IOException，记录详细日志，包含路径、操作类型。

### 6.5 面试高频考点

1. 字节流和字符流的区别？选型依据是什么？
2. try-with-resources 的原理？资源关闭顺序？抑制异常是什么？
3. `Files.walk()` 返回的 Stream 需要关闭吗？为什么？
4. NIO Buffer 的三个核心属性？`flip()` 的作用是什么？
5. 什么是路径遍历攻击？怎么防范？
6. BIO 和 NIO 的核心区别？（文件场景 + 网络场景）

### 6.6 随堂练习

**需求**：使用 try-with-resources 实现 UTF-8 编码的文本文件逐行读取，并统计行数。
参考答案：

```java
public long countLines(String filePath) throws IOException {
    Path path = Paths.get(filePath);
    try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
        return lines.count();
    }
}
```
---

## 七、核心 API 对比速查表

### 7.1 JDK8 → JDK21 核心 API 新增速览

| API                                  | 引入版本 | 核心作用                          |
| ------------------------------------ | -------- | --------------------------------- |
| `Stream.toList()`                    | JDK16    | 收集为不可变 List，禁止 null 元素 |
| `Optional.isEmpty()`                 | JDK11    | 判断值是否为空                    |
| `Optional.ifPresentOrElse()`         | JDK9     | 存在/不存在双分支处理             |
| `Optional.or()`                      | JDK9     | 空值时返回另一个 Optional         |
| `Files.readString() / writeString()` | JDK11    | 小文件字符串便捷读写              |
| `Stream.takeWhile() / dropWhile()`   | JDK9     | 有序流的条件截取/跳过             |

### 7.2 空值处理方式对比

| 方式     | 语义清晰度   | 编译器检查 | 代码冗余度   | 适用场景                 |
| -------- | ------------ | ---------- | ------------ | ------------------------ |
| null     | 低，隐性契约 | 无         | 高，多层判断 | 类字段、方法内部局部变量 |
| Optional | 高，显式契约 | 强制处理   | 低，链式调用 | 方法返回值、链式空值处理 |

### 7.3 日期时间类型选型对比

| 类型          | 时区信息  | 能否确定绝对瞬间 | 线程安全 | 典型场景             |
| ------------- | --------- | ---------------- | -------- | -------------------- |
| LocalDate     | 无        | 不能             | 是       | 生日、业务日期       |
| LocalDateTime | 无        | 不能             | 是       | 单时区系统展示       |
| Instant       | UTC 标准  | 能               | 是       | 日志、时间戳、跨系统 |
| ZonedDateTime | 有时区 ID | 能               | 是       | 跨时区业务           |

---

## 八、综合业务案例

### 8.1 案例 A：订单数据统计

#### 8.1.1 需求

给定订单列表，完成：

1. 筛选已支付（PAID）状态、金额大于 100 元的订单
2. 按订单类别分组，统计每个类别的订单数、总金额
3. 转换为统计 DTO 返回
4. 处理空字段、重复订单号、空集合场景
5. 金额使用 BigDecimal 保证精度

#### 8.1.2 代码实现

```java
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

// 订单实体
class Order {
    private String orderNo;
    private String status;
    private String category;
    private BigDecimal amount;
    private LocalDateTime createTime;
    public Order(String orderNo, String status, String category, BigDecimal amount, LocalDateTime createTime) {
        this.orderNo = orderNo;
        this.status = status;
        this.category = category;
        this.amount = amount;
        this.createTime = createTime;
    }

    public String getOrderNo() { return orderNo; }
    public String getStatus() { return status; }
    public String getCategory() { return category; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getCreateTime() { return createTime; }
}
```


```java
// 统计 DTO
class CategoryStatDTO {
    private String category;
    private Long orderCount;
    private BigDecimal totalAmount;
    public CategoryStatDTO(String category, Long orderCount, BigDecimal totalAmount) {
        this.category = category;
        this.orderCount = orderCount;
        this.totalAmount = totalAmount;
    }

    public String getCategory() { return category; }
    public Long getOrderCount() { return orderCount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
}
```


```java
public class OrderStatisticsDemo {
    public List<CategoryStatDTO> statistics(List<Order> orderList) {
        // 空集合快速返回
        if (orderList == null || orderList.isEmpty()) {
            return Collections.emptyList();
        }

        // 先按订单号去重，重复订单保留最新创建时间
        Map<String, Order> distinctOrderMap = orderList.stream()
                .filter(order -> "PAID".equals(order.getStatus()))
                .filter(order -> order.getAmount() != null
                        && order.getAmount().compareTo(BigDecimal.valueOf(100)) > 0)
                .filter(order -> order.getCategory() != null)
                .collect(Collectors.toMap(
                        Order::getOrderNo,
                        Function.identity(),
                        (oldOrder, newOrder) ->
                                oldOrder.getCreateTime().isAfter(newOrder.getCreateTime()) ? oldOrder : newOrder
                ));

        // 按类别分组统计总金额
        Map<String, BigDecimal> totalAmountMap = distinctOrderMap.values()
                .stream()
                .collect(Collectors.groupingBy(
                        Order::getCategory,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                Order::getAmount,
                                BigDecimal::add
                        )
                ));

        // 按类别统计订单数
        Map<String, Long> countMap = distinctOrderMap.values()
                .stream()
                .collect(Collectors.groupingBy(
                        Order::getCategory,
                        Collectors.counting()
                ));

        // 组装 DTO 并排序
        return totalAmountMap.entrySet().stream()
                .map(entry -> new CategoryStatDTO(
                        entry.getKey(),
                        countMap.getOrDefault(entry.getKey(), 0L),
                        entry.getValue()
                ))
                .sorted(Comparator.comparing(CategoryStatDTO::getCategory))
                .toList();
    }

    public static void main(String[] args) {
        List<Order> orderList = Arrays.asList(
                new Order("O001", "PAID", "数码", new BigDecimal("299.00"), LocalDateTime.now()),
                new Order("O002", "PAID", "食品", new BigDecimal("59.90"), LocalDateTime.now()),
                new Order("O003", "PAID", "数码", new BigDecimal("1599.00"), LocalDateTime.now()),
                new Order("O001", "PAID", "数码", new BigDecimal("399.00"), LocalDateTime.now().plusHours(1)),
                new Order("O004", "UNPAID", "图书", new BigDecimal("89.00"), LocalDateTime.now())
        );

        OrderStatisticsDemo demo = new OrderStatisticsDemo();
        List<CategoryStatDTO> result = demo.statistics(orderList);
        result.forEach(dto -> System.out.printf("类别：%s，订单数：%d，总金额：%s%n",
                dto.getCategory(), dto.getOrderCount(), dto.getTotalAmount()));
    }
}
```


#### 8.1.3 验收标准

- 空集合输入返回空列表，不抛 NPE
- 重复订单号按最新创建时间保留
- 金额使用 BigDecimal 计算，无精度丢失
- 结果按类别排序，返回不可变列表

### 8.2 案例 B：日志文件分析

#### 8.2.1 需求

1. 读取指定目录下所有 `.log` 后缀的日志文件
2. 统计 ERROR 级别的日志行数
3. 支持大文件，不一次性加载到内存
4. 处理文件不存在、权限不足、编码问题
5. 正确关闭所有资源

#### 8.2.2 代码实现

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class LogAnalysisDemo {
    private static final String ERROR_PREFIX = "ERROR";

    /**
     * 统计目录下所有日志文件的 ERROR 行数
     * @param logDir 日志目录路径
     * @return ERROR 总行数
     */
    public long countErrorLines(String logDir) throws IOException {
        Path dirPath = Paths.get(logDir);

        // 校验目录合法性
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            throw new IllegalArgumentException("日志目录不存在或不是目录: " + logDir);
        }

        long totalErrorCount = 0;

        // 遍历目录下所有 .log 文件，walk 必须 try-with-resources 关闭
        try (Stream<Path> fileStream = Files.walk(dirPath, 1)) {
            Iterable<Path> logFiles = fileStream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".log"))
                    ::iterator;

            for (Path logFile : logFiles) {
                totalErrorCount += countSingleFileError(logFile);
            }
        }

        return totalErrorCount;
    }

    private long countSingleFileError(Path logFile) {
        // 逐行读取，大文件友好，lines 返回的 Stream 自动关闭文件句柄
        try (Stream<String> lines = Files.lines(logFile, StandardCharsets.UTF_8)) {
            return lines
                    .filter(line -> line != null && line.startsWith(ERROR_PREFIX))
                    .count();
        } catch (IOException e) {
            // 单个文件读取失败，记录日志，不影响整体统计
            System.err.printf("读取文件失败：%s，原因：%s%n", logFile, e.getMessage());
            return 0;
        }
    }

    public static void main(String[] args) throws IOException {
        LogAnalysisDemo demo = new LogAnalysisDemo();
        long count = demo.countErrorLines("E:/logs");
        System.out.println("ERROR 总行数：" + count);
    }
}
```


#### 8.2.3 验收标准

- 所有文件流、目录流均使用 try-with-resources 关闭
- 逐行读取，内存占用稳定，支持 GB 级大文件
- 单文件失败不中断整体任务，降级处理
- 显式指定 UTF-8 编码，无乱码风险

### 8.3 案例 C：日期驱动的文件归档

#### 8.3.1 需求

1. 按业务时区（Asia/Shanghai）计算前一天的归档日期
2. 扫描源目录下修改时间为归档日期的文件
3. 将文件复制到归档目录，按日期分子目录
4. 处理同名文件、路径越界、权限不足
5. 记录归档结果与失败文件明细

#### 8.3.2 代码实现

```

```
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class FileArchiveDemo {

    private static final ZoneId BIZ_ZONE = ZoneId.of("Asia/Shanghai");
    
    /**
     * 归档前一天的文件
     * @param sourceDir 源目录
     * @param archiveRootDir 归档根目录
     * @return 失败文件列表
     */
    public List<String> archiveYesterdayFiles(String sourceDir, String archiveRootDir) throws IOException {
        Path sourcePath = Paths.get(sourceDir).toAbsolutePath().normalize();
        Path archiveRootPath = Paths.get(archiveRootDir).toAbsolutePath().normalize();
    
        // 校验源目录
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            throw new IllegalArgumentException("源目录无效: " + sourceDir);
        }
    
        // 计算归档日期：业务时区的前一天
        LocalDate archiveDate = ZonedDateTime.now(BIZ_ZONE).toLocalDate().minusDays(1);
        Path targetDir = archiveRootPath.resolve(archiveDate.toString());
        Files.createDirectories(targetDir);
    
        List<String> failedFiles = new ArrayList<>();
    
        // 遍历源目录文件
        try (Stream<Path> fileStream = Files.walk(sourcePath, 1)) {
            Iterable<Path> files = fileStream
                    .filter(Files::isRegularFile)
                    ::iterator;
    
            for (Path file : files) {
                try {
                    // 获取文件修改时间，转为业务时区的日期
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    LocalDate modifyDate = attrs.lastModifiedTime()
                            .toInstant()
                            .atZone(BIZ_ZONE)
                            .toLocalDate();
    
                    // 匹配归档日期
                    if (archiveDate.equals(modifyDate)) {
                        Path targetFile = targetDir.resolve(file.getFileName());
                        // 同名文件追加时间戳避免覆盖
                        if (Files.exists(targetFile)) {
                            String fileName = file.getFileName().toString();
                            int dotIndex = fileName.lastIndexOf('.');
                            String newName = dotIndex > 0
                                    ? fileName.substring(0, dotIndex) + "_" + System.currentTimeMillis() + fileName.substring(dotIndex)
                                    : fileName + "_" + System.currentTimeMillis();
                            targetFile = targetDir.resolve(newName);
                        }
                        Files.copy(file, targetFile, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (IOException e) {
                    failedFiles.add(file.toString() + " -> " + e.getMessage());
                }
            }
        }
    
        return failedFiles;
    }
    
    public static void main(String[] args) throws IOException {
        FileArchiveDemo demo = new FileArchiveDemo();
        List<String> failed = demo.archiveYesterdayFiles("E:/source", "E:/archive");
        System.out.println("归档完成，失败文件数：" + failed.size());
        failed.forEach(System.out::println);
    }
}

#### 8.3.3 验收标准

- 日期计算使用指定业务时区，不依赖服务器默认时区
- 路径规范化，防止路径遍历攻击
- 同名文件自动重命名，避免静默覆盖
- 失败文件记录明细，不中断整体归档流程
- 所有流资源正确关闭，无句柄泄漏

---

## 九、常见错误代码与修复方案

### 错误 1：Stream 多次消费

```java
// 错误：stream 只能消费一次，第二次调用抛出 IllegalStateException
Stream<String> stream = list.stream();
stream.count();
stream.forEach(System.out::println);

// 修复：每次使用重新创建流
list.stream().count();
list.stream().forEach(System.out::println);
```


### 错误 2：orElse 性能浪费

```
// 错误：用户存在时也会执行数据库查询，产生不必要开销
User user = Optional.ofNullable(userDao.selectById(id))
        .orElse(getDefaultUserFromDb());

// 修复：用 orElseGet 延迟求值
User user = Optional.ofNullable(userDao.selectById(id))
        .orElseGet(() -> getDefaultUserFromDb());

```


### 错误 3：toMap 未处理重复键

```
// 错误：key 重复时直接抛出 IllegalStateException，生产必出 bug
Map<String, User> map = list.stream()
        .collect(Collectors.toMap(User::getDeptId, Function.identity()));

// 修复：指定合并策略
Map<String, User> map = list.stream()
        .collect(Collectors.toMap(
                User::getDeptId,
                Function.identity(),
                (oldVal, newVal) -> newVal
        ));

```


### 错误 4：SimpleDateFormat 多线程共享

```
// 错误：多线程共享，线程不安全，格式错乱、异常
private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd");

// 修复：使用 DateTimeFormatter，线程安全，可全局单例
private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

```


### 错误 5：资源型 Stream 未关闭

```
// 错误：流未关闭，文件句柄泄漏，长时间运行耗尽系统资源
Files.lines(path).forEach(System.out::println);

// 修复：try-with-resources 自动关闭
try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
    lines.forEach(System.out::println);
}



```
### 错误 6：LocalDateTime 当作绝对时间传输

// 错误：跨系统传输 LocalDateTime 无时区信息，接收方时区歧义
// 修复：跨系统传输使用 Instant 或 OffsetDateTime

---

## 十、面试高频问题与参考答案

1. **Q：Lambda 表达式和匿名内部类有什么核心区别？**
   A：
   - 编译本质不同：匿名内部类编译后生成独立 class 文件，Lambda 通过 invokedynamic 指令实现，无额外类文件，创建开销更低。
   - this 指向不同：匿名内部类的 this 指向自身实例，Lambda 的 this 指向外部类实例。
   - 适用范围不同：匿名内部类可实现多方法接口、抽象类；Lambda 仅能实现函数式接口。
   - 变量捕获规则一致：局部变量都要求是 final 或 effectively final。
2. **Q：Stream 的惰性求值是什么？有什么优势？**
   A：
   Stream 的中间操作只构建操作管道，不立即执行，只有遇到终止操作时才触发真正的计算，这就是惰性求值。
   优势：
   - 可以对整个操作链进行全局优化，短路操作提前终止，减少不必要计算。
   - 避免中间结果的额外存储，减少内存开销。
   - 声明式编程，业务逻辑更清晰，关注点分离。
3. **Q：Optional 能彻底避免空指针吗？为什么？**
A：
不能。Optional 只是显式表达了空值语义，强制开发者处理空场景，减少人为遗漏。但如果错误使用，比如直接调用 `get()` 不判断、Optional 本身为 null，依然会出现空指针。它是降低 NPE 风险的工具，不是银弹。
4. **Q：为什么 LocalDateTime 不能唯一确定全球某一瞬间？**
A：
LocalDateTime 只包含年月日时分秒，没有时区信息。同一本地时间在不同时区对应的绝对时间完全不同，比如 2025-10-01 12:00，在北京时区和纽约时区对应的真实时间完全不一样。必须加上时区或偏移量，才能确定唯一的绝对瞬间。
5. **Q：try-with-resources 的关闭顺序是怎样的？抑制异常是什么？**
A：
关闭顺序与声明顺序相反，先声明的资源后关闭，符合依赖关系（外层资源依赖内层，先关外层再关内层）。
抑制异常：当 try 代码块抛出主异常，关闭资源时也抛出异常，关闭异常不会覆盖主异常，而是作为抑制异常添加到主异常中，通过 `Throwable.getSuppressed()` 可以获取，保证异常信息不丢失。

---

## 十一、自测题与评分标准

### 11.1 自测题（满分 100 分）

#### 一、选择题（每题 5 分，共 30 分）

1. 以下哪个接口不是函数式接口？（ ）
A. Runnable B. Comparator C. ArrayList D. Supplier
2. Stream 的以下哪个操作属于终止操作？（ ）
A. filter B. map C. collect D. peek
3. JDK16+ 中，`Stream.toList()` 返回的 List 特点是？（ ）
A. 可修改，允许 null B. 不可修改，允许 null
C. 可修改，不允许 null D. 不可修改，不允许 null
4. Optional 中，仅当值不存在时才执行默认值生成的方法是？（ ）
A. orElse B. orElseGet C. of D. map
5. 以下哪个类型可以唯一确定全球某一瞬间？（ ）
A. LocalDateTime B. LocalDate C. Instant D. LocalTime
6. 以下哪个 Stream 必须手动关闭资源？（ ）
A. `list.stream()` B. `Stream.of()` C. `Files.lines()` D. `Arrays.stream()`

#### 二、简答题（每题 10 分，共 40 分）

1. 简述 `map` 和 `flatMap` 的核心区别，各举一个适用场景。
2. 简述 `orElse` 和 `orElseGet` 的求值差异，以及选型原则。
3. 简述 `SimpleDateFormat` 和 `DateTimeFormatter` 的核心区别。
4. 什么是路径遍历攻击？简述一种标准防范方式。

#### 三、代码题（每题 15 分，共 30 分）

1. 使用 Stream 将 `List<User>` 转为 `Map<Long, String>`，key 为 userId，value 为 userName，处理重复 key 保留后者。
2. 用 Optional 重构多层 null 判断代码，获取用户的公司名称，不存在返回「未知」。

### 11.2 参考答案与评分标准

#### 一、选择题

1.C  2.C  3.D  4.B  5.C  6.C

#### 二、简答题

1. map 是一对一转换，每个输入对应一个输出，适用于对象字段提取、类型转换；flatMap 是一对多再扁平化，将每个元素转为流再合并，适用于嵌套集合展开。（概念 6 分，场景 4 分）
2. orElse 无论值是否存在都会求值默认值；orElseGet 仅值不存在时才通过 Supplier 生成默认值，延迟求值。选型：默认值有开销时用 orElseGet，简单常量可用 orElse。（概念 6 分，选型 4 分）
3. SimpleDateFormat 线程不安全，多线程共用会出错；DateTimeFormatter 不可变，线程安全，可全局单例。前者对应旧 Date API，后者对应 java.time 新体系。（线程安全 6 分，其他 4 分）
4. 路径遍历攻击是用户通过 `../` 等特殊字符，越过允许的目录访问系统敏感文件。防范：路径规范化后校验是否在根目录内，禁止直接字符串拼接路径，使用 Path.resolve。（概念 5 分，防范 5 分）

#### 三、代码题

1. 参考代码：

```
Map<Long, String> userMap = userList.stream()
        .collect(Collectors.toMap(
                User::getUserId,
                User::getUserName,
                (oldVal, newVal) -> newVal
        ));
```
（toMap 结构 8 分，合并函数 5 分，格式 2 分）
2. 参考代码：

```
String companyName = Optional.ofNullable(user)
        .map(User::getCompany)
        .map(Company::getName)
        .orElse("未知");

```
（链式结构 8 分，orElse 兜底 5 分，格式 2 分）

---

## 十二、学习任务清单与验收证据

### 阶段 A：Lambda 与函数式接口

- 能解释 Lambda 的语法、目标类型和适用条件
- 能识别并使用四大核心函数式接口（Predicate/Function/Consumer/Supplier）
- 能编写四种形式的方法引用
- 能分析变量捕获和副作用问题
**验收证据**：5 个可运行示例 + 常见误区对照表

### 阶段 B：Stream 与数据处理

- 能区分中间操作和终止操作，解释惰性求值原理
- 能完成过滤、映射、扁平化、分组、统计和聚合
- 能正确处理 toMap 重复键问题
- 能解释 reduce 与 collect 的区别与选型
- 能分析顺序流和并行流的区别与风险
**验收证据**：8 种不同 Stream 操作练习 + 结果验证

### 阶段 C：Optional 与空值语义

- 能正确选择 of、ofNullable 和 empty
- 能解释 map、flatMap、orElse、orElseGet 的核心区别
- 能重构多层空值判断为链式调用
- 能识别过度使用 Optional 的场景
**验收证据**：5 个示例（含 1 个错误用法 + 1 个重构案例）

### 阶段 D：日期时间

- 能根据业务场景正确选择日期时间类型
- 能完成解析、格式化、时区转换和时间计算
- 能处理闰年、月末与跨时区边界场景
- 能解释 Duration 和 Period 的区别
**验收证据**：6 个可运行案例 + 边界说明

### 阶段 E：IO 与资源管理

- 能区分字节流与字符流，明确选型依据
- 能显式指定字符集完成文本读写
- 能使用 Path 和 Files 完成常见文件操作
- 能正确使用 try-with-resources，解释抑制异常
- 能识别大文件处理、资源泄漏和路径遍历风险
**验收证据**：6 个可运行案例（读写/遍历/异常/资源关闭）

### 阶段 F：综合工程实践

- 完成订单数据统计案例
- 完成日志文件分析案例
- 完成日期驱动的文件归档案例
- 每个案例配套运行说明、测试数据和结果验证
**验收证据**：3 个综合案例 + 完整技术说明

---

## 十三、官方参考资料

1. Java Language Specification, Java SE 21, Chapter 15: Expressions | Java SE 21《Java 语言规范》第 15 章：表达式：[https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html)
2. Package java.util.function, Java SE 21 | Java SE 21 `java.util.function` 函数式接口包：[https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/function/package-summary.html](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/function/package-summary.html)
3. Package java.util.stream, Java SE 21 | Java SE 21 `java.util.stream` 流处理 API：[https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html)
4. Class Optional, Java SE 21 | Java SE 21 `Optional` 可选值容器：[https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Optional.html](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Optional.html)
5. Package java.time, Java SE 21 | Java SE 21 `java.time` 日期时间 API：[https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/package-summary.html](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/package-summary.html)
6. Package java.nio.file, Java SE 21 | Java SE 21 `java.nio.file` 文件系统 API：[https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/package-summary.html](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/package-summary.html)
7. Package java.io, Java SE 21 | Java SE 21 `java.io` 输入输出 API：[https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/package-summary.html](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/package-summary.html)
8. Package java.nio, Java SE 21 | Java SE 21 `java.nio` 缓冲区与 NIO 基础 API：[https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/package-summary.html](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/package-summary.html)
9. The Java Tutorials: Essential Java Classes | Java 教程：Java 核心类库：[https://docs.oracle.com/javase/tutorial/essential/index.html](https://docs.oracle.com/javase/tutorial/essential/index.html)

---

需要我把这份笔记导出为可直接保存的 Markdown 文件格式，或者补充某一模块的更多代码示例吗？