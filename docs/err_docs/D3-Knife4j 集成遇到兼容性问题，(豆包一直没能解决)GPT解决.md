## 先给出排查结论

你提供的地址是：

`http://localhost:8080/doc.html#/SwaggerModels/default`

这里有一个重要线索：`SwaggerModels/default` 指向 Knife4j 的 Swagger Models（模型结构）页面，而不是具体的 API 接口列表。因此，需要先区分两种情况：

- 只有 Models 页面为空：不一定是 Knife4j 集成失败。如果 OpenAPI 文档没有生成被接口引用的模型结构，模型页面就可能没有内容。
- 接口列表、Models 页面全部为空：需要重点检查 OpenAPI 文档 JSON 是否正常生成、接口扫描范围和 Springdoc 依赖是否冲突。

Spring Boot 3 应使用 Knife4j 的 `knife4j-openapi3-jakarta-spring-boot-starter`，该 Starter 已集成 Springdoc 相关依赖，官方明确提醒避免自行引入造成版本冲突。

[Knife4j版本参考](https://doc.xiaominfo.com/docs/quick-start/start-knife4j-version?utm_source=chatgpt.com)



不过，我目前遇到一个实际限制：本轮环境无法读取你的 GitHub 仓库源码文件，GitHub 连接也尚未完成。因此，我不能声称已经定位了 `D03/D03_Mysql_core` 中某个具体 Java 文件的确定性 Bug。以下先给出可以直接验证的排查路径。

## 一、先验证这三个接口

1\. 获取 OpenAPI 配置

直接在浏览器中访问：

[http://localhost:8080/v3/api-docs/swagger-config](http://localhost:8080/v3/api-docs/swagger-config?utm_source=chatgpt.com)

预期：返回 HTTP 200 和 JSON，包含文档 URL 或分组配置。

2\. 获取默认 OpenAPI 文档

[http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs?utm_source=chatgpt.com)

预期：返回包含 `openapi`、`paths` 等字段的 JSON。

3\. 检查分组对应的实际文档

查看第一个接口返回 JSON 中的 `url` 或 `urls`。如果它指向 `/v3/api-docs/某个分组`，还需要访问该 URL，而不能只检查默认文档。

## 二、根据返回结果定位根因

| 实际结果                                     | 首要排查方向                                                 |
| -------------------------------------------- | ------------------------------------------------------------ |
| `swagger-config` 返回 404                    | Springdoc 路由未注册、依赖不匹配或配置禁用了 API 文档        |
| `swagger-config` 返回 500                    | 查看服务端异常日志，重点检查 Springdoc/Knife4j 版本冲突及分组配置 |
| 配置正常，但其中的文档 URL 返回 404          | 分组名称、文档 URL 或上下文路径配置错误                      |
| 文档 JSON 返回 200，但 `paths` 为空          | 检查 Controller 是否被 Spring 扫描，以及 `packagesToScan` 是否覆盖实际 Controller 包 |
| `paths` 有数据，但 `components.schemas` 为空 | 检查是否有请求或响应模型被接口引用；单凭模型为空不能判定集成失败 |
| 文档 JSON 中有接口，但 Knife4j 界面仍为空    | 检查浏览器开发者工具的 Network/Console、分组选择，以及接口文档请求是否被安全拦截 |

## 三、D03 模块必须核验的代码位置

针对 `D03/D03_Mysql_core`，我会重点核对以下四处，而不是直接修改配置碰运气。

1. `pom.xml` — 依赖是否重复或冲突

   检查 Knife4j Starter、Springdoc、Swagger 注解库是否出现不兼容的版本组合。Spring Boot 3 应使用 Jakarta 版本 Starter，尤其要排查是否同时引入旧版 Springdoc 或 Springfox。
2. `application.yml` — 文档开关与分组配置

   检查 `springdoc.api-docs.enabled`、Knife4j 启用设置，以及是否把文档路径改成了非默认地址。
3. 配置类 — `GroupedOpenApi` 与 `packagesToScan`

   如果项目手动定义分组，扫描包名必须匹配实际 Controller 的包。包名写错可能导致配置正常、页面也能打开，但接口列表为空。
4. Controller — 是否真正注册为 Web 接口

   核查 `@RestController`、`@RequestMapping`、请求方法注解，以及主启动类的组件扫描范围。仅定义普通 Java 类或 Service 并不会自动生成 REST API 文档。

## 四、需要你提供的最小证据

为了真正找出仓库中的确定性 Bug，请连接 GitHub，或直接上传最新仓库 ZIP。这样才能对 `D03_Mysql_core` 的实际 POM、配置类、Controller 和启动日志逐一核查，而不是猜测。

另外，最快的辅助证据是打开浏览器开发者工具（按 `F12`），进入 Network，刷新 Knife4j 页面，查看 `swagger-config` 和实际 OpenAPI JSON 请求的状态码。将这两项的响应贴出来，就能先把问题收敛到依赖配置、接口扫描还是前端加载环节。

# D03_Mysql_core：Knife4j 接口文档空白问题审查

已经找到一个与现象高度吻合、并且能够从实际 POM 和 Knife4j 官方项目问题记录交叉验证的根因：Knife4j 4.5.0 与 Springdoc 2.8.13 的兼容性问题。

我已读取 GitHub `master` 分支中该模块的实际 `pom.xml`、`Knife4jConfig.java`、`D03App.java`、`WebConfig.java`、`AuthInterceptor.java`、`application.yml` 以及三个 Controller。你的接口扫描包名本身是正确的，项目里也确实存在有映射注解的接口，因此不能简单归因于“没有 Controller”。

## 一、最高优先级 Bug：Knife4j 与 Springdoc 方法签名不兼容

根因定位

P1 · 优先修复

源码位置：[`D03_Mysql_core/pom.xml`](https://github.com/storyzhangli/M1-JAVA/blob/master/D03/D03_Mysql_core/pom.xml?utm_source=chatgpt.com)

当前依赖组合是：

```
<artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
<version>4.5.0</version>
```

同时排除了 Knife4j 自带的 Springdoc UI 依赖，又手动加入：

```
<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
<version>2.8.13</version>
```

这不是一个可以直接认定为兼容的组合。

问题的关键在于：Knife4j 4.5.0 内部的 `Knife4jOpenApiCustomizer` 依赖了旧版 `SpringDocConfigProperties#getGroupConfigs()` 的方法签名。Springdoc 2.8.x 中该方法的返回类型已经改变。

Knife4j 官方仓库的 [Issue #823](https://github.com/xiaoymin/knife4j/issues/823?utm_source=chatgpt.com) 记录了同类错误，明确出现 `NoSuchMethodError`。在 [Issue #913](https://github.com/xiaoymin/knife4j/issues/913?utm_source=chatgpt.com) 中，针对 Spring Boot 3.4 及以上版本，维护者和用户讨论了升级 Springdoc 后需要修复 Knife4j 自定义器的问题。

[Knif4j 4.5 ，手动升级springdoc到1.8.0后报错](https://github.com/xiaoymin/knife4j/issues/823?utm_source=chatgpt.com)

[spring boot 3.4 以上版本/v3/api-docs 解决接口报错,依赖修复 #913](https://github.com/xiaoymin/knife4j/issues/913?utm_source=chatgpt.com)



这与你描述的现象完全吻合：

1. Knife4j 的静态前端页面可以打开。
2. 页面继续请求后端 OpenAPI JSON。
3. JSON 生成阶段执行不兼容的自定义器，可能返回 HTTP 500。
4. 页面没有成功加载接口描述和模型数据。

这里需要明确：代码和版本组合已经确认存在兼容性风险，但当前尚未获得你本机 `/v3/api-docs/default` 的实际响应，因此还不能把运行时异常认定为已经现场复现。

## 二、修复方案：保留 Spring Boot 3.5.16，修复 Knife4j 自定义器

不建议简单地把 Springdoc 降回 2.3.0。Springdoc 官方兼容矩阵明确将 Spring Boot 3.5.x 对应到 Springdoc 2.8.x，因此你当前选择的 2.8.13 符合该版本范围。

[springdoc-openapi v2.9.1](https://springdoc.org/v2/?trk=public_post_main-feed-card-text&utm_source=chatgpt.com)



更合适的处理方式是：保留现有版本组合，覆盖 Knife4j 4.5.0 中不兼容的 `Knife4jOpenApiCustomizer` 实现。

### 第 1 步：保留现有 POM 中的这两项依赖

先不改变 `D03/D03_Mysql_core/pom.xml` 里的 Knife4j 4.5.0 和 Springdoc 2.8.13 配置。它们的组合需要兼容性修复，而不是盲目换版本。

### 第 2 步：新增兼容自定义器

在以下目录新增文件：

`D03/D03_Mysql_core/src/main/java/com/zl/d03/config/Knife4jOpenApiCustomizer.java`

```
package com.zl.d03.config;

import com.github.xiaominfo.knife4j.core.conf.GlobalConstants;
import com.github.xiaominfo.knife4j.spring.configuration.Knife4jProperties;
import com.github.xiaominfo.knife4j.spring.configuration.Knife4jSetting;
import com.github.xiaominfo.knife4j.spring.extension.OpenApiExtensionResolver;
import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.Map;

/**
 * 原理说明：
 * Knife4j 4.5.0 的自定义器针对旧版 Springdoc 编译。
 * Springdoc 2.8.x 的相关方法签名发生变化。
 *
 * 这里覆盖 customise 方法，不调用父类原有实现，
 * 避免触发不兼容的 addOrderExtension 逻辑。
 */
@Primary
@Configuration
public class Knife4jOpenApiCustomizer
        extends com.github.xiaominfo.knife4j.spring.extension.Knife4jOpenApiCustomizer
        implements GlobalOpenApiCustomizer {

    private final Knife4jProperties knife4jProperties;

    public Knife4jOpenApiCustomizer(
            Knife4jProperties knife4jProperties,
            SpringDocConfigProperties springDocConfigProperties) {
        super(knife4jProperties, springDocConfigProperties);
        this.knife4jProperties = knife4jProperties;
    }

    @Override
    public void customise(OpenAPI openApi) {
        // 关闭增强配置时不添加 Knife4j 扩展信息。
        if (!knife4jProperties.isEnable()) {
            return;
        }

        Knife4jSetting setting = knife4jProperties.getSetting();

        OpenApiExtensionResolver resolver = new OpenApiExtensionResolver(
                setting,
                knife4jProperties.getDocuments()
        );

        resolver.start();

        Map<String, Object> extensions = new HashMap<>();
        extensions.put(
                GlobalConstants.EXTENSION_OPEN_SETTING_NAME,
                setting
        );
        extensions.put(
                GlobalConstants.EXTENSION_OPEN_MARKDOWN_NAME,
                resolver.getMarkdownFiles()
        );

        openApi.addExtension(
                GlobalConstants.EXTENSION_OPEN_API_NAME,
                extensions
        );
    }
}
```

注意： 这是针对该版本冲突的兼容修复方案，不是我已经在你的仓库中编译验证过的补丁。它保留 Knife4j 的设置和 Markdown 扩展，但不再执行父类的接口排序扩展逻辑。如果后续使用了 `@ApiSupport(order = ...)`，还需要补充与 Springdoc 2.8.x 兼容的排序实现。

这一覆盖方式与 Knife4j 官方 Issue #913 讨论的解决思路一致。

[spring boot 3.4 以上版本/v3/api-docs 解决接口报错,依赖修复 #913](https://github.com/xiaoymin/knife4j/issues/913?utm_source=chatgpt.com)



## 三、另外两个确定的代码问题

### 问题 2：HelloController 没有注册任何接口路由

文件：

[`HelloController.java`](https://github.com/storyzhangli/M1-JAVA/blob/master/D03/D03_Mysql_core/src/main/java/com/zl/d03/controller/HelloController.java?utm_source=chatgpt.com)

当前代码只有：

```
@RestController
public class HelloController {

    public String sayHello() {
        return "Hello World";
    }
}
```

`@RestController` 只表明该类是控制器；`sayHello()` 没有 `@GetMapping`、`@PostMapping` 或其他请求映射注解，因此 Spring MVC 不会将该方法注册为可访问的 HTTP 接口。

建议修改为：

```
package com.zl.d03.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/hello")
    @Operation(summary = "健康检查", description = "验证应用接口是否正常")
    public String sayHello() {
        return "Hello World";
    }
}
```

这会补上缺失的 `/hello` 接口，但它不是导致整个 Knife4j 页面空白的主要原因。

### 问题 3：文档访问注释没有反映分组路径

[`Knife4jConfig.java`](https://github.com/storyzhangli/M1-JAVA/blob/master/D03/D03_Mysql_core/src/main/java/com/zl/d03/config/Knife4jConfig.java?utm_source=chatgpt.com) 中创建了名为 `default` 的 `GroupedOpenApi` 分组：

```
.group("default")
.packagesToScan("com.zl.d03.controller")
.pathsToMatch("/**")
```

因此排查时，应该优先检查：

`http://localhost:8080/v3/api-docs/default`

而不是只根据 Controller 注释中的 `/v3/api-docs` 来判断。`packagesToScan("com.zl.d03.controller")` 与实际 Controller 包路径一致，这一项目前没有发现错误。

## 四、修复后如何验收

先在 IDEA 中保存代码，然后从 `D03` 目录执行：

```
mvn clean package -pl D03_Mysql_core -am
```

之后重新启动 `D03App`，按以下顺序检查。

验收 1：OpenAPI JSON 正常生成

打开 [http://localhost:8080/v3/api-docs/default](http://localhost:8080/v3/api-docs/default?utm_source=chatgpt.com)

预期 HTTP 200，返回 JSON，并包含 `openapi`、`paths` 等字段。`paths` 应包含认证接口和 `/user/{id}`，修复 HelloController 后还应包含 `/hello`。

验收 2：Knife4j 分组配置正常

打开 [http://localhost:8080/v3/api-docs/swagger-config](http://localhost:8080/v3/api-docs/swagger-config?utm_source=chatgpt.com)

核对响应中的文档 URL 是否与 `default` 分组一致。

验收 3：前端加载成功

打开 [http://localhost:8080/doc.html](http://localhost:8080/doc.html?utm_source=chatgpt.com)

确认接口列表不为空，能够展开 `/auth`、`/user` 和 `/hello`，并检查模型页面是否显示被接口引用的模型。

如果 JSON 仍返回 500，查看 IDEA 控制台是否出现：

```
java.lang.NoSuchMethodError:
org.springdoc.core.properties.SpringDocConfigProperties.getGroupConfigs()
```

如果出现，说明兼容性问题仍未被解决；如果没有，继续检查实际报错堆栈以及浏览器 Network 中失败的请求。

## 五、最终问题清单

| 优先级 | 文件                   | 结论                                                         |
| ------ | ---------------------- | ------------------------------------------------------------ |
| P1     | `pom.xml`              | Knife4j 4.5.0 与 Springdoc 2.8.13 存在已知方法签名兼容风险，是首要排查目标 |
| P2     | `Knife4jConfig.java`   | 分组配置和 Controller 扫描包目前正确                         |
| P2     | `HelloController.java` | 方法缺少请求映射注解，导致该接口不注册、不出现在文档中       |
| 待验证 | `WebConfig.java`       | 已排除 `/doc.html` 与 `/v3/api-docs/**` 等路径，目前没有发现会拦截文档请求的明显错误 |
| 待验证 | `application.yml`      | `knife4j.enable: true` 会启用增强配置，应结合实际 JSON 请求与服务端日志验证 |

当前最值得优先做的事情，是先核实 `/v3/api-docs/default` 是否因为 `NoSuchMethodError` 返回 500。 这能直接验证本次定位的核心问题，不需要先改动其他业务代码。