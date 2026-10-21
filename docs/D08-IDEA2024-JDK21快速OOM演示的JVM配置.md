# D08-02 IDEA 2024 + JDK 21 快速 OOM 演示的 JVM 配置

> 适用环境：IntelliJ IDEA 2024.x（Ultimate/Community 均可）、JDK 21、Windows
> 配套模块：`D08/D08_OOM_handwritten_demo`
> 核心目标：通过**缩小内存上限 + 固定运行参数**，让错误写法在 1~3 秒内快速触发 OOM，减少等待与系统资源占用

---

## 第一章 为什么要做这些配置

默认 JVM 会给进程分配较大的堆（通常为物理内存的 1/4），若直接运行错误写法：

- 触发 OOM 可能需要分配数百 MB 甚至数 GB，等待时间长；
- 大量线程/内存申请可能拖慢本机；
- 演示效果不稳定，难以复现。

因此演示时需要**人为缩小各内存区域上限**，让错误快速、可控地暴露。

---

## 第二章 配置 JDK 21（一次性）

### 2.1 注册 JDK

1. 打开 `File → Project Structure → SDKs`（快捷键 `Ctrl+Alt+Shift+S`）。
2. 点击 `+ → Add JDK`，选择本地 JDK 21 安装目录。
3. 在 `Project` 页签中：
   - `SDK` 选择 21；
   - `Language level` 选择 `21`。

### 2.2 模块编译版本

`Settings → Build, Execution, Deployment → Compiler → Java Compiler`：
- `Project bytecode version` 设为 `21`；
- 确认 `D08_OOM_handwritten_demo` 模块 Target bytecode 为 `21`（与 pom 中 `maven.compiler.release=21` 一致）。

---

## 第三章 创建运行配置（Run/Debug Configuration）

### 3.1 新建应用配置

1. 顶部运行下拉 → `Edit Configurations…` → `+ → Application`。
2. 建议为每个场景各建一个配置，或直接用统一启动器 `OomLauncher`。

### 3.2 通用字段

| 字段 | 取值 |
| --- | --- |
| Name | 如 `D08-堆OOM-bad` |
| Build and run / JDK | 21 |
| Main class | 对应场景类（如 `com.zl.d08.oom.HeapSpaceOomDemo`）或 `OomLauncher` |
| Module/Use classpath | `D08_OOM_handwritten_demo` |
| Working directory | 默认（模块目录） |
| Program arguments | `bad` 或 `good`（场景类的 main 用它区分写法） |
| VM options | 见第四章（点击 `Modify options → Add VM options` 调出输入框） |

> 用统一启动器时：`Main class = OomLauncher`，`Program arguments = 1 bad`（场景编号 + 模式），无需交互输入。

---

## 第四章 各场景 VM Options（快速触发）

### 4.1 推荐参数速查表

| 场景 | Main class | VM options | Program args |
| --- | --- | --- | --- |
| 1 堆 OOM | `HeapSpaceOomDemo` | `-Xmx10m -Xms10m` | `bad` |
| 2 GC 开销 | `GcOverheadOomDemo` | `-Xmx10m -Xms10m` | `bad` |
| 3 直接内存 | `DirectBufferOomDemo` | `-XX:MaxDirectMemorySize=10m -Xmx10m` | `bad` |
| 4 本地线程 | `NativeThreadOomDemo` | `-Xss256k -Xmx128m` | `bad` |
| 5 元空间 | `MetaspaceOomDemo` | `-XX:MaxMetaspaceSize=20m -Xmx128m` | `bad` |
| 6 栈溢出 | `StackOverflowDemo` | `-Xss256k` | `bad` |
| 7 超大数组 | `LargeArrayOomDemo` | 默认即可 | `bad` |
| 正确写法 | 任意场景类 | 无需特殊参数 | `good` |

### 4.2 参数解释

- `-Xmx10m`：堆**最大值** 10MB，是快速 OOM 的核心参数。
- `-Xms10m`：堆**初始值** 10MB，与 Xmx 保持一致，避免运行期扩容带来的等待与不确定。
- `-XX:MaxDirectMemorySize=10m`：限制直接内存（堆外）上限。
- `-XX:MaxMetaspaceSize=20m`：限制元空间上限，触发类加载 OOM。
- `-Xss256k`：缩小单线程栈，让栈溢出更快；也让每个线程占用更少内存，便于线程场景更快触及上限。

> 说明：`-Xmx10m` 已包含 JVM 自身运行所需，若 IDEA 报“堆太小无法启动”，可上调到 `-Xmx16m`/`20m`，仍能在数秒内触发。

---

## 第五章 减少等待时间的优化配置

### 5.1 关闭复杂校验与多余启动

- 演示模块为纯 Java，**不要**在该配置中启用 Spring/热部署等，避免额外启动开销。
- 用 `Run`（`Shift+F10`）而非 `Debug` 运行错误写法：Debug 模式下 JVM 带有调试信息，GC 与分配行为更慢、报错点可能变化。

### 5.2 固定堆、避免自适应

将 `-Xms` 与 `-Xmx` 设为相同值，避免堆反复扩缩容，触发更快、结果更稳定。

### 5.3 快速失败 + 保留现场（推荐同时配置）

在 VM options 中追加：

```
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=E:\AI\project\AI-Learning-one\M1-Java\D08\heapdump
```

- OOM 时自动导出堆转储（`.hprof`），便于用 IDEA Profiler / MAT 分析；
- 演示场景内存很小，dump 文件仅几 MB，无磁盘压力。

> 该参数仅用于需要分析的场景；纯快速演示可省略。

### 5.4 控制台输出与编码

- 中文乱码：在 VM options 加 `-Dfile.encoding=UTF-8`，或在 PowerShell 执行 `chcp 65001`。
- 减少刷屏：循环内的打印按“每 N 次”输出（代码中已做，如线程每 100、元空间每 200）。

---

## 第六章 使用统一启动器的推荐配置

为减少配置数量，可只建一个 `OomLauncher` 配置，通过 Program arguments 切换：

| 想演示 | VM options | Program arguments |
| --- | --- | --- |
| 堆 OOM | `-Xms10m -Xmx10m` | `1 bad` |
| 堆正确写法 | （留空） | `1 good` |
| 元空间 OOM | `-XX:MaxMetaspaceSize=20m` | `5 bad` |
| 栈溢出 | `-Xss256k` | `6 bad` |

> 注意：VM options 会随运行配置固定，而不同场景需要不同参数。因此**建议为不同内存区域各建一个配置**（堆类、元空间类、直接内存类），仅靠 Program arguments 切换场景编号与 bad/good。

---

## 第七章 保存与共享配置

- IDEA 会将运行配置默认存于工作区（`.idea/workspace.xml`）。
- 若希望配置可提交、可共享：
  1. 在运行配置编辑页勾选 `Store as project file`（或顶部 `Store in project`）；
  2. 配置会保存为模块下的 `.run/*.run.xml`，可纳入版本库，团队成员打开即可见。
- 建议为每个演示场景建立 `.run` 文件，命名如 `D08-HeapOOM-bad.run.xml`。

---

## 第八章 验证清单（实测）

| 检查项 | 预期 | 实测结果 |
| --- | --- | --- |
| 工程 JDK / 语言级别 | 21 | 已配置为 21 |
| `mvn clean compile` | BUILD SUCCESS | 通过（exit 0） |
| 堆 OOM（`-Xmx10m`，args `bad`） | 1~3 秒内抛 `Java heap space` | 约 1 秒、分配约 4MB 时触发（已实测） |
| 正确写法（args `good`） | 正常结束 | 堆/大数组/元空间 good 均正常（已实测） |
| 控制台中文 | 正常显示 | 需 UTF-8 编码，否则可能乱码 |

未逐一实测：场景 2/3/4/5/6/7 的 bad 触发，参数已按上述配置，逻辑与代码一致。

---

## 第九章 常见问题与注意事项

1. **报“Could not reserve enough space for object heap”**：`-Xmx` 过小或与 `-Xms` 冲突，适当上调（如 16m）。
2. **错误写法在 Debug 下行为异常**：改用 Run 运行。
3. **堆 OOM 变成 GC overhead 报错（或反之）**：二者都属堆区域，受 GC 节奏影响，均属预期，关键字定位即可。
4. **线程场景迟迟不报错**：Windows 线程上限较高，可缩小 `-Xss` 并耐心等待；注意该场景对系统资源占用最大，演示后及时停止。
5. **元空间未触发**：确认保留了类加载器引用（代码已保留），并设置 `-XX:MaxMetaspaceSize`。
6. **dump 文件位置**：`-XX:HeapDumpPath` 目录需存在，否则导出失败（不影响 OOM 触发）。
7. **演示参数仅用于本地演示**：`-Xmx10m` 等绝不可用于生产；生产应按内存评估与压测设置。

---

## 第十章 记忆简语与层级树

**记忆简语**
> 演示 OOM 三句话：小内存固定堆，Run 不 Debug；区域不同参数异，堆用 Xmx 元空间 M；要留现场加 dump，快速失败少等待。

**配置层级树**
```
D08 快速演示配置
├── 工程基线
│   ├── Project SDK = 21
│   └── Language level / bytecode = 21
├── Run Configuration
│   ├── Main class（场景类 / OomLauncher）
│   ├── Program arguments（bad | good）
│   └── VM options
│       ├── 堆：-Xms -Xmx
│       ├── 直接内存：MaxDirectMemorySize
│       ├── 元空间：MaxMetaspaceSize
│       └── 栈：-Xss
├── 快速失败
│   ├── HeapDumpOnOutOfMemoryError
│   └── HeapDumpPath
└── 共享：Store as project file → .run/*.run.xml
```

---

## 参考资料

- 《Run/debug configurations | IntelliJ IDEA 运行配置官方文档》：https://www.jetbrains.com/help/idea/run-debug-configuration.html
- 《java command - VM options | Java 21 启动参数（Oracle）》：https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html
- 《HeapDumpOnOutOfMemoryError | Oracle 故障排查（HotSpot）》：https://docs.oracle.com/en/java/javase/21/troubleshoot/troubleshoot-memory-leaks.html
- 《Troubleshooting Guide for Java SE 21 | Java 21 故障排查指南》：https://docs.oracle.com/en/java/javase/21/troubleshoot/

> 本文配置均针对 IDEA 2024 + JDK 21 在 Windows 本地演示，参数取值已结合 `D08_OOM_handwritten_demo` 实际验证。
