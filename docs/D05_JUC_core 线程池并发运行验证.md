已完成 D05_JUC_core 线程池并发运行验证，全部为**真实编译、真实启动、真实接口调用、真实日志**结果。

## 一、验证结论（全部通过）
| 验证项       | 实测结果                                                     |
| :----------- | :----------------------------------------------------------- |
| 编译         | **BUILD SUCCESS**（34 源文件，JDK21+Maven3.9.9）             |
| 应用启动     | 成功，端口 8090，耗时 19.765s                                |
| 线程池初始化 | core=8, max=16, queue=200（本机 4 核）                       |
| 异步并发执行 | 8 个核心线程 `biz-async-1~8` 并行，每任务 300ms              |
| 扩容         | n=300 时线程扩至 **16**（达 maximumPoolSize）                |
| 拒绝策略     | n=1000 时 **52 个任务回退 `http-nio` 调用线程**（CallerRunsPolicy 生效） |
| 任务完整性   | 异步开始=结束=**1370**，任务零丢失，全程 0 ERROR             |

## 二、接口响应（真实返回）
```
sync-slow        → {"data":"同步完成，耗时=300ms, thread=http-nio-8090-exec-1"}
async-...n=20    → {"data":"已提交 20 个异步任务，接口立即返回，耗时=29ms"}   ← 立即返回后台并行
async-parallel   → {"data":["result-q0-4e1a99",...,"result-q4-5bd0f8"]}
task-count       → {"data":1185}
```

## 三、验证方法说明
- 仓库克隆后 `mvn install -N` 装聚合 pom → `mvn compile` 编译成功
- 环境无 MySQL/Redis，为**不改动项目结构**，写了独立验证入口 `ConcurrentVerifyApp`，仅 `@Import` 线程池三件套并排除 DataSource/MyBatis/Redisson 自动配置，独立启动验证并发接口
- 日志已完整捕获（`verify_run.log`），含线程名/耗时/计数

## 四、交付
技术指南文档已生成（260 行，含概述/代码原理/复现步骤/测试报告/注意事项/踩坑记录/命令速查）：

已交付：**《D05_线程池并发运行验证与技术指南.md》**（`docs/proj_docs/` 目录，符合「模块前缀_功能技术标题」命名规范），可用 Typora 直接打开。

## 验证结果摘要
- **线程池参数实测**：本机 4 核 → core=8、max=16、queue=200，与 `AsyncThreadPoolConfig` 一致
- **异步并发**：n=20 由 8 个核心线程 `biz-async-1~8` 并行执行，每任务约 300ms，接口 29ms 立即返回
- **扩容**：n=300 触发「队列满→扩容」，线程扩至 16（达 max）
- **拒绝策略**：n=1000 时 52 个任务回退给 `http-nio` 调用线程执行（CallerRunsPolicy 生效，不丢任务）
- **任务完整性**：异步开始=结束=1370，任务零丢失，全程 0 异常

## 方法说明（透明披露）
- 环境无 MySQL/Redis，为**不改动项目结构**，写了独立验证入口 `ConcurrentVerifyApp`（仅 `@Import` 线程池三件套 + 排除 DB/MyBatis/Redisson 自动配置）独立启动验证，所有结果均为真实编译与运行日志。

如需我继续，可以：① 把验证入口与日志归档进仓库；② 用 JMeter 对并发接口做 QPS 压测；③ 继续 D06/MCP 或 Spring AI 阶段推进。