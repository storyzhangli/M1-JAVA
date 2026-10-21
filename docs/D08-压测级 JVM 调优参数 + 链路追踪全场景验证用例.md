# 压测级JVM调优参数 + 链路追踪全场景验证用例
> 技术栈对齐：JDK 21 LTS + ZGC 分代回收 + SpringBoot 3.5.16；参数面向高并发压测场景设计，验证用例覆盖功能、并发、透传、异常全场景。

---

## 一、压测场景JVM调优参数（JDK21 + 分代ZGC）
### 1. 调优核心原则
压测场景的核心目标：**消除启动预热抖动、控制GC停顿阈值、保障吞吐稳定性、保留全量诊断数据**。所有参数严格遵循JDK21官方规范，淘汰JDK8废弃参数；按「内存基础 → GC低延迟调优 → 性能预热优化 → 诊断日志 → 稳定性防护」分层配置。

### 2. 完整压测级参数配置（逐行说明）
```bash
# ========== 1. 内存基础配置（压测强制固定大小） ==========
-Xms4g
-Xmx4g
-XX:MaxMetaspaceSize=512m
-XX:CompressedClassSpaceSize=256m
-Xss512k
```
| 参数                        | 作用与压测价值                                               |
| --------------------------- | ------------------------------------------------------------ |
| `-Xms4g` / `-Xmx4g`         | 初始堆与最大堆固定为4GB，**彻底避免运行时堆扩容带来的性能抖动与GC停顿**，压测/生产环境强制一致 |
| `-XX:MaxMetaspaceSize=512m` | 限制元空间最大值，防止动态类生成（如动态代理、CGLIB）导致的堆外内存泄漏 |
| `-Xss512k`                  | 线程栈大小从默认1MB降至512KB；高并发下压测可大幅节省内存，支撑更多线程，普通业务场景足够使用 |

```bash
# ========== 2. ZGC 分代回收低延迟调优（压测核心） ==========
-XX:+UseZGC
-XX:+ZGenerational
-XX:MaxGCPauseMillis=10
-XX:ConcGCThreads=4
-XX:ZCollectionInterval=30000
-XX:-ZUncommit
-XX:MaxTenuringThreshold=15
```
| 参数                            | 作用与压测价值                                               |
| ------------------------------- | ------------------------------------------------------------ |
| `-XX:+UseZGC`                   | 显式启用ZGC低延迟回收器；JDK21默认即为ZGC，显式声明保证环境一致性 |
| `-XX:+ZGenerational`            | 启用分代ZGC（JDK21默认开启）；年轻代回收频率高、停顿极短，是高并发场景的核心优化点 |
| `-XX:MaxGCPauseMillis=10`       | GC最大停顿目标设为10ms；默认值为200ms，压测低延迟场景强制收紧，保障P99延迟 |
| `-XX:ConcGCThreads=4`           | 并发GC线程数设为4；建议值为CPU核心数的1/4~1/2，延迟优先设高、吞吐优先设低 |
| `-XX:ZCollectionInterval=30000` | 强制最小GC间隔30秒；避免突发流量下短时间频繁触发GC，导致性能波动 |
| `-XX:-ZUncommit`                | 禁用ZGC内存归还操作系统；压测时保持内存占用稳定，避免系统内存调度带来的延迟尖刺 |
| `-XX:MaxTenuringThreshold=15`   | 对象晋升老年代阈值设为15；增加对象在年轻代的存活次数，减少老年代GC频率 |

```bash
# ========== 3. 性能预热与优化（消除压测冷启动偏差） ==========
-XX:+AlwaysPreTouch
-XX:-UseBiasedLocking
-XX:+DoEscapeAnalysis
-XX:+EliminateAllocations
-XX:MaxInlineLevel=15
```
| 参数                        | 作用与压测价值                                               |
| --------------------------- | ------------------------------------------------------------ |
| `-XX:+AlwaysPreTouch`       | **压测必开**：启动时预分配并触摸所有堆内存，避免运行时缺页中断导致的延迟尖刺，消除冷启动数据偏差 |
| `-XX:-UseBiasedLocking`     | **高并发必关**：禁用偏向锁；高并发锁竞争激烈时，偏向锁撤销会触发大量安全点停顿，严重影响压测吞吐 |
| `-XX:+DoEscapeAnalysis`     | 开启逃逸分析（默认开启，显式确认）；优化栈上分配，减少堆对象分配与GC压力 |
| `-XX:+EliminateAllocations` | 标量替换优化；将未逃逸的对象拆分为标量直接在栈上分配，大幅降低GC频率 |
| `-XX:MaxInlineLevel=15`     | 加大方法内联深度至15；优化热点方法调用开销，提升执行效率     |

```bash
# ========== 4. 压测诊断日志（全量可追溯） ==========
-Xlog:gc*,gc+age=trace,gc+heap=debug,gc+pause=trace,gc+ergo=info,gc+stats=info:file=logs/gc-pressure.log:time,uptime,level,tags:filecount=20,filesize=200M
-XX:NativeMemoryTracking=summary
-XX:+PrintCommandLineFlags
-XX:+PrintFlagsFinal
```
| 参数                               | 作用与压测价值                                               |
| ---------------------------------- | ------------------------------------------------------------ |
| `-Xlog:gc*...`                     | 压测专用GC日志；输出分代年龄、堆内存变化、停顿时间、GC统计等全量信息；滚动保留20个文件，每个200MB |
| `-XX:NativeMemoryTracking=summary` | 开启本地内存跟踪；可通过 `jcmd` 查看堆外内存分布，排查压测中的堆外内存泄漏 |
| `-XX:+PrintCommandLineFlags`       | 启动时打印实际生效的JVM参数，验证配置是否正确加载            |

```bash
# ========== 5. 稳定性与故障兜底 ==========
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=logs/heapdump-pressure.hprof
-XX:+ExitOnOutOfMemoryError
-Dfile.encoding=UTF-8
```
| 参数                              | 作用与压测价值                                             |
| --------------------------------- | ---------------------------------------------------------- |
| `-XX:+HeapDumpOnOutOfMemoryError` | OOM时自动生成堆转储文件，定位内存泄漏根因                  |
| `-XX:+ExitOnOutOfMemoryError`     | OOM后直接退出进程；避免OOM后进程假死、服务不可用的拖尾状态 |

### 3. 两套场景化调优模板
| 调优方向   | 堆内存 | 停顿目标 | 并发GC线程    | 核心侧重                         | 适用场景                        |
| ---------- | ------ | -------- | ------------- | -------------------------------- | ------------------------------- |
| 低延迟优先 | 4GB    | 5ms      | CPU核心数×1/2 | 最小化GC停顿，保障P99/P999延迟   | 接口压测、在线业务、实时链路    |
| 高吞吐优先 | 8GB    | 50ms     | CPU核心数×1/4 | 最大化GC吞吐量，减少GC总耗时占比 | 批量处理、离线任务、极限QPS压测 |

### 4. 面试高频考点
1. **压测为什么必须开AlwaysPreTouch？**
   答：JVM默认按需分配内存，运行时首次访问内存页会触发缺页中断，产生延迟尖刺，导致压测前几百次请求数据失真。预触摸后内存全部映射到物理内存，压测数据稳定可复现。
2. **高并发下为什么禁用偏向锁？**
   答：偏向锁优化单线程无竞争场景，但高并发锁竞争时，偏向锁撤销需要进入全局安全点，批量撤销会产生几十到上百毫秒停顿，严重影响吞吐与延迟。
3. **ZGC并发线程数设置规则？**
   答：延迟优先场景设为CPU核心数的1/2，让GC更快完成；吞吐优先设为1/4，避免GC线程抢占业务CPU资源。

---

## 二、链路追踪效果验证测试用例
覆盖**同步链路、异步链路、上游透传、异常场景、高并发串号排查**5大核心验证点，配套可运行代码与验证命令。

### 1. 测试接口代码实现
#### （1）测试控制器 `TraceTestController.java`
```java
package com.example.demo.controller;

import com.example.demo.service.TraceTestService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/trace")
public class TraceTestController {

    private static final Logger log = LoggerFactory.getLogger(TraceTestController.class);
    @Resource
    private TraceTestService traceTestService;

    /**
     * 场景1：同步请求验证
     * 验证点：同一次请求两条日志traceId完全一致
     */
    @GetMapping("/sync")
    public String syncTest() {
        log.info("【同步请求】进入控制器，开始处理业务");
        String result = traceTestService.syncProcess();
        log.info("【同步请求】业务处理完成，返回结果");
        return result;
    }

    /**
     * 场景2：异步链路验证
     * 验证点：异步线程日志traceId与主线程完全一致
     */
    @GetMapping("/async")
    public String asyncTest() {
        log.info("【异步请求】主线程发起异步任务");
        traceTestService.asyncProcess();
        log.info("【异步请求】主线程提交异步任务完成");
        return "异步任务已提交";
    }

    /**
     * 场景3：上游traceId透传验证
     * 验证点：请求头携带X-Trace-Id时，服务端沿用不重新生成
     */
    @GetMapping("/transmit")
    public String transmitTest(@RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        log.info("【透传验证】接收到上游traceId: {}", traceId);
        return "透传验证完成";
    }

    /**
     * 场景4：异常场景验证
     * 验证点：异常堆栈日志携带对应traceId
     */
    @GetMapping("/error")
    public String errorTest() {
        log.info("【异常测试】准备抛出业务异常");
        int i = 1 / 0;
        return "不会执行到";
    }
}
```

#### （2）异步业务服务 `TraceTestService.java`
```java
package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class TraceTestService {

    private static final Logger log = LoggerFactory.getLogger(TraceTestService.class);

    public String syncProcess() {
        log.info("【同步业务层】执行业务逻辑");
        return "sync ok";
    }

    /**
     * 使用已配置的asyncExecutor线程池，自动继承MDC上下文
     */
    @Async("asyncExecutor")
    public void asyncProcess() {
        log.info("【异步业务层】异步线程执行业务逻辑");
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("【异步业务层】异步任务执行完成");
    }
}
```

#### （3）补充：跨服务调用透传拦截器（生产必备）
> 解决RestTemplate调用下游服务时，traceId自动透传问题，实现全链路贯通。
```java
package com.example.demo.interceptor;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TraceIdRestInterceptor implements ClientHttpRequestInterceptor {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            request.getHeaders().add(TRACE_ID_HEADER, traceId);
        }
        return execution.execute(request, body);
    }
}
```

### 2. 单接口功能验证（curl命令 + 预期结果）
#### 场景1：同步链路验证
```bash
curl -v [http://localhost:8080/test/trace/sync](http://localhost:8080/test/trace/sync)
```
**验收标准**：
1. 响应头中包含 `X-Trace-Id: xxx`
2. 日志中两条「同步请求」+ 一条「同步业务层」日志，**三条日志traceId完全相同**

#### 场景2：异步链路验证
```bash
curl [http://localhost:8080/test/trace/async](http://localhost:8080/test/trace/async)
```
**验收标准**：
1. 主线程两条日志traceId一致
2. 异步线程两条日志traceId与主线程完全一致
3. 异步线程名前缀为 `async-task-`

#### 场景3：上游traceId透传验证
```bash
curl -H "X-Trace-Id: test-abc-123456" [http://localhost:8080/test/trace/transmit](http://localhost:8080/test/trace/transmit)
```
**验收标准**：
1. 日志中traceId为 `test-abc-123456`，未生成新UUID
2. 响应头返回同样的 `X-Trace-Id: test-abc-123456`

#### 场景4：异常场景验证
```bash
curl [http://localhost:8080/test/trace/error](http://localhost:8080/test/trace/error)
```
**验收标准**：
1. 异常堆栈日志中包含对应traceId
2. 异常发生后，MDC正常清理，不影响后续请求

### 3. 高并发压测验证（线程池串号专项验证）
#### （1）压测命令（wrk工具，高并发首选）
```bash
# 4线程、100并发、持续压测30秒
wrk -t4 -c100 -d30s [http://localhost:8080/test/trace/sync](http://localhost:8080/test/trace/sync)
```
> 无wrk可用ab替代：`ab -n 10000 -c 200 [http://localhost:8080/test/trace/sync](http://localhost:8080/test/trace/sync)`

#### （2）串号验证方法
```bash
# 统计日志中不同traceId的数量，应等于请求总数
grep -o '\[.*\] INFO.*进入控制器' logs/demo-app.log | awk -F'[][]' '{print $2}' | sort | uniq | wc -l

# 检查是否存在单个traceId对应多次"进入控制器"（串号特征）
grep -o '\[.*\] INFO.*进入控制器' logs/demo-app.log | awk -F'[][]' '{print $2}' | sort | uniq -c | awk '$1>1'
```

#### （3）压测验收标准
1. 所有请求日志均携带traceId，无空值占位符 `-`
2. traceId总数 = 请求总数，无重复、无串用
3. 线程池复用场景下，未出现前后请求traceId混淆
4. 异步任务压测下，traceId传递准确率100%

### 4. 最终验收证据清单
1. 启动日志截图：包含JVM参数打印、SpringBoot启动成功标识
2. GC日志文件：`logs/gc-pressure.log` 有连续GC记录
3. 链路日志截图：至少2条同traceId的同步日志 + 1条异步线程日志
4. 压测验证结果：traceId唯一性统计命令输出

---

需要我生成对应的**JMeter压测脚本（.jmx）**，或者补充**GC日志分析命令与调优判断标准**吗？