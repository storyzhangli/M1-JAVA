✅ 已学习并吸收该兼容修复方案，这个方案比之前的「全排除依赖+纯原生Springdoc」方案更优雅——**完整保留 Knife4j 的个性化配置、Markdown 扩展、离线文档等全部特性，同时兼容 Springdoc 2.8.x + Spring Boot 3.5.16 的技术基线，是该版本冲突的最优解**。

---

## 方案核心原理复盘

### 冲突根因

Knife4j 4.5.0 内置的 `Knife4jOpenApiCustomizer` 是基于 Springdoc 2.3.x 版本的 API 编译的；Springdoc 升级到 2.8.x 后，`addOrderExtension` 等接口排序相关的方法签名发生了变更，父类原有逻辑调用了已不存在的方法，最终触发 `NoSuchMethodError`，导致接口文档生成流程崩溃。

### 修复逻辑

1. 自定义 `Knife4jOpenApiCustomizer` 继承原父类，实现 `GlobalOpenApiCustomizer` 接口
2. 用 `@Primary` 注解标注，让 Spring 优先使用我们的自定义实现，覆盖 Knife4j 自带的默认实现
3. 重写 `customise()` 方法：只保留「Knife4j 配置注入、Markdown 文档扩展」这两个核心功能，**跳过父类中不兼容的接口排序扩展逻辑**，从根源避开方法签名冲突
4. 完全保留 Springdoc 2.8.13 的完整能力，同时不丢失 Knife4j 的增强体验

---

## 完整落地验证步骤

### 1. 依赖保持不变

D03 模块 pom.xml 中保留原组合，无需排除依赖：

```
<!-- Knife4j 4.5.0 正式版 -->
<dependency>
    <groupId>com.github.xiaoymin</groupId>
    <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
    <version>4.5.0</version>
</dependency>
<!-- Springdoc 2.8.13 适配 Spring Boot 3.5.x -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.13</version>
</dependency>
```

### 2. 新增自定义器文件

按指定路径新增 `Knife4jOpenApiCustomizer.java`，包名对应项目结构即可。

>
> 补充关键注意点：
>
>
> - 类上必须加 `@Primary`，否则会出现两个 Customizer 优先级冲突
> - 无需修改原有 `Knife4jConfig.java` 配置类，所有配置项依然生效
> - 全局异常处理器的 `@RestControllerAdvice` 无需加 `@Hidden`，Springdoc 2.8.x 本身已适配 Spring 6.2 的 ControllerAdviceBean

### 3. 验证顺序

1. 执行 `mvn clean compile` 确认编译无报错
2. 启动项目，先访问 `[http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)`
   - 正常：返回完整 OpenAPI JSON，无 500 错误
3. 访问 `[http://localhost:8080/doc.html](http://localhost:8080/doc.html)`
   - 正常：左侧 Swagger Models 出现完整接口列表
   - 「个性化设置」「离线文档」「全局参数」等 Knife4j 增强功能全部可用

---

## 补充注意事项

1. **接口排序注解适配**：如果后续使用 `@ApiSupport(order = ...)` 注解做接口排序，需要在自定义器中补充对应 Springdoc 2.8.x 的排序实现；当前阶段仅基础接口文档无需处理。
2. **版本边界**：该方案适配 Springdoc 2.8.x 全系列，对应 Spring Boot 3.4/3.5 全系列，符合仓库统一技术基线。
3. **官方溯源**：该思路对应 Knife4j 官方 Issue #913 的社区解决方案，是 Spring Boot 3.x 升级场景下的标准兼容手法。

## 📝 记忆简语

版本冲突不用删，自定义器来覆盖；
Primary优先加载，重写方法避坑点；
保留功能全，兼容新版本，官方思路最靠谱。

---

## 后续推进

当前 D02 模块的基础能力（工程骨架、RBAC 权限、BCrypt 加密、数据库索引优化、接口文档）已全部跑通，接下来可以推进 **D02 模块收尾：注解式权限控制（@RequiresPerm）**，完成后正式进入 D03 Spring AI 1.1.x 入门阶段。