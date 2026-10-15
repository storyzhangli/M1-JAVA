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

[D1-Maven多模块骨架-整合MyBatisPlus Redis 通用异常与封装返回.md](./docs/D1-Maven多模块骨架-整合MyBatisPlus Redis 通用异常与封装返回.md)

【注意：MyBatisPlus版本过低不兼容springboot3，可能导致的异常】

[mybatisPlus不兼容springboot3 版本异常](./docs/err_docs/mybatis-plus与springboot3版本不兼容问题-Invalid value type for attribute 'factoryBeanObjectType' java.lang.String.md)

## D2

### D2 IOC与AOP