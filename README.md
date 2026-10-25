# M1-Java 

统一环境：

| 项目       | 本计划采用的基线           |
| ---------- | -------------------------- |
| Java       | JDK 21                     |
| 构建       | Maven                      |
| 后端       | Spring Boot                |
| AI 主框架  | Spring AI                  |
| 业务数据库 | PostgreSQL \| MySQL        |
| 向量检索   | PostgreSQL + pgvector      |
| 自动化测试 | JUnit、Spring Boot Test    |
| 环境部署   | Docker Compose             |
| 代码管理   | Git + GitHub               |
| 文档       | Markdown、架构图、测试报告 |

## D1

Springboot自动装配 最简demo: D01_SpringBoot_Auto_config

10/14 PM完成

### D1 Maven多模块骨架

【参考文档】

[Maven多模块骨架-整合MyBatisPlus Redis 通用异常与封装返回.md](./docs/proj_docs/D1-01-Maven多模块骨架-整合MyBatisPlus Redis 通用异常与封装返回-项目搭建.md)

【注意：MyBatisPlus版本过低不兼容springboot3，可能导致的异常】

[mybatisPlus不兼容springboot3 版本异常](./docs/err_docs/D1-mybatis-plus与springboot3版本不兼容问题-Invalid value type for attribute 'factoryBeanObjectType' java.lang.String.md)





## D2

### D2 springboot自动装配-starter机制-bean生命周期

[springboot自动装配 starter机制 bean生命周期](./docs/D2-springboot3.X 核心：自动配置原理，starter 机制，bean 生命周期.md)



### D2 IOC与AOP原理

[Spring-IOC AOP原理-动态代理、事务传播、失效场景](./docs/D2-Spring IOC AOP原理-动态代理、事务传播、失效场景.md)

[aop-log-aop切面日志](./docs/D2-aop-log aop切面日志.md)



### D2 RBAC权限

[RBAC经典权限五张表-基础项目搭建](./docs/proj_docs/D2-01-RBAC经典权限五张表-基础项目搭建.md)



### D2 BCrypt加密改造

[BCrypt加密改造-项目搭建](./docs/proj_docs/D2-02-BCrypt加密改造-项目搭建.md)

## D3

### D3.Mysql核心：索引 最左前缀 ...

[MySQL核心：B+ 树索引底层原理、最左前缀原则、`EXPLAIN` 关键字段、慢查询定位与优化 02](./docs/D3-MySQL 核心知识：MySQL InnoDB B+Tree index leftmost prefix EXPLAIN，B+ 02.md)

[B+ 树索引底层原理、最左前缀原则、`EXPLAIN` 关键字段、慢查询定位与优化 01](./docs/D3-MySQL 核心知识：MySQL InnoDB B+Tree index leftmost prefix EXPLAIN，B+.md)



### D3.Proj-Mysql、Knife4j

[项目迭代：MySQL核心-Knife4j](./docs/proj_docs/D3-00-项目迭代：MySQL核心-Knife4j借口文档.md)

[项目迭代-EXPALIN示例用法](./docs/proj_docs/D3-01-项目迭代-EXPALIN示例用法.md)



### D3.ERR-Knife4j-兼容问题

[Knife4j 集成遇到兼容性问题](./docs/err_docs/D3-Knife4j 集成遇到兼容性问题，(豆包一直没能解决)GPT解决.md)





## D4

### D4.Redis核心知识

[Redis 核心知识-融合文档](./docs/D4-Redis 核心知识-融合文档.md)



### D4.Redis核心 迭代项目文档

[D4-00-Redis核心 项目搭建](./docs/proj_docs/D4-00-Redis核心 项目搭建.md)

[D4-01-Redis核心 项目搭建](./docs/proj_docs/D4-01-Redis核心 项目搭建.md)



## D5

### D05.JUC核心知识

[D05-Java并发核心知识](./docs/D05-Java并发核心知识-融合文档.md)

### D05.JUC demo

[自定义线程池 demo](./docs/proj_docs/D05-手写自定义线程池.md)



### D05 JUC项目迭代

[JUC核心 项目搭建说明](./docs/proj_docs/D05-00-JUC核心 项目搭建.md)

[异步线程池与接口并发测试](./docs/proj_docs/D05-01-异步线程池与接口并发测试.md)





## D6

### D06 分布式基础与Redis分布式锁

[D6-Java分布式基础 Redis分布式锁 核心知识-融合文档](./docs/D6-Java分布式基础 Redis分布式锁 核心知识-融合文档.md)

### D06 代码练习：Redis分布式锁

[手写Redis分布式锁-Lua原子释放与看门狗续期 demo](./docs/proj_docs/D06-01-手写Redis分布式锁-Lua原子释放与看门狗续期.md)



## D7

### D07-本周知识主线复盘

[D07-本周知识主线复盘](./docs/D07-本周知识主线复盘.md)

### D07-思维导图

[D07-思维导图串讲](./docs/D07-思维导图串讲.md)



### D07项目迭代

[D07-M1-Java命名规范化与注释文档完善](./docs/proj_docs/D07-M1-Java_命名规范化与注释文档完善.md)

[D07-LeetCode 数组 + 链表 经典 5 题 手写代码 01](./docs/proj_docs/D07-LeetCode 数组 + 链表 经典 5 题 手写代码 01.md)

[D07-LeetCode 数组 + 链表 经典 5 题 手写代码 02](./docs/proj_docs/D07-LeetCode 数组 + 链表 经典 5 题 手写代码 02.md)



## D8

### D08 知识笔记

[D08-JVM 核心知识与 OOM 排查](./docs/D08-JVM 核心知识与 OOM 排查.md)

[快速OOM演示的JVM配置](./docs/D08-IDEA2024-JDK21快速OOM演示的JVM配置.md)

[D08-压测级 JVM 调优参数 + 链路追踪全场景验证用例](./docs/D08-压测级 JVM 调优参数 + 链路追踪全场景验证用例.md)



### D08项目迭代

[JVM参数配置与启停脚本](./docs/proj_docs/D08-03-JVM参数配置与启停脚本.md)

[日志链路追踪traceId与MDC异步传递](./docs/proj_docs/D08-04-日志链路追踪traceId与MDC异步传递.md)



## D9

### D09知识笔记

[D09-SpringCloud核心：Gateway网关、Feign调用、熔断降级  代码练习：手写Feign拦截器](./docs/D09-SpringCloud核心：Gateway网关、Feign调用、熔断降级  代码练习：手写Feign拦截器.md)



### D09项目迭代

[微服务三件套网关Feign熔断-项目迭代全量](./docs/proj_docs/D09-01-微服务三件套网关Feign熔断-项目迭代全量.md)

[网关限流Feign链路追踪熔断降级-技术说明与实操指南](./docs/proj_docs/D09-02-网关限流Feign链路追踪熔断降级-技术说明与实操指南.md)



## D10

### D10.知识笔记

[幂等性与代码练习](./docs/proj_docs/D10. 接口设计：幂等性、限流、防重放   2. 代码练习：手写接口幂等实现.md)



### D10.项目迭代

[幂等与限流-项目迭代全量](./docs/proj_docs/D10-01-幂等与限流-项目迭代全量.md)

[幂等与令牌桶限流-技术说明与实操指南](./docs/proj_docs/D10-02-幂等与令牌桶限流-技术说明与实操指南.md)

[幂等与限流](./docs/proj_docs/D10-03 幂等与限流 — 交付报告.md)

[D10-04-接口幂等项目需求设计文档](./docs/proj_docs/D10-04-接口幂等项目需求设计文档.md)

[D10-05-数据库唯一约束幂等-项目迭代全量](./docs/proj_docs/D10-05-数据库唯一约束幂等-项目迭代全量.md)

[D10-06-数据库唯一约束幂等-技术说明与实操指南](./docs/proj_docs/D10-06-数据库唯一约束幂等-技术说明与实操指南.md)

[D10-07-安全并发三项整改-项目迭代全量](./docs/proj_docs/D10-07-安全并发三项整改-项目迭代全量.md)

[D10-08-幂等所有权重试竞态与IP限流防伪造-技术说明与实操指南](./docs/proj_docs/D10-08-幂等所有权重试竞态与IP限流防伪造-技术说明与实操指南.md)



## D11

### D11.知识笔记

[D11-1. 八股高频题串讲：场景题思路梳理   2. LeetCode 5道（树+动态规划）](./docs/proj_docs/D11-1. 八股高频题串讲：场景题思路梳理   2. LeetCode 5道（树+动态规划）.md)



## D12

### D12.知识笔记

[D12-Java工程阶段总复盘知识笔记](./docs/proj_docs/D12-Java工程阶段总复盘知识笔记.md)



### D12.项目迭代

[D12-01-D10-IARL幂等限流服务V1.0-项目迭代全量](./docs/proj_docs/D12-01-D10_IARL幂等限流服务V1.0-项目迭代全量.md)

[D12-02-D10-IARL幂等限流缓存并发治理-技术说明与实操指南](./docs/proj_docs/D12-02-D10_IARL幂等限流缓存并发治理-技术说明与实操指南.md)

