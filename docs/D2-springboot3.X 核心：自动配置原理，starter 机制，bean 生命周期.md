### ✅ 核心结论

SpringBoot 3.5.16 自动配置基于「**@EnableAutoConfiguration + SPI 导入文件 + 条件注解**」三级机制，相比SpringBoot2 正式废弃了`spring.factories`自动配置方式，改用`AutoConfiguration.imports`文件；Starter是「**依赖聚合 + 自动配置封装**」的场景化启动组件，是“约定优于配置”的核心载体；Bean生命周期在Spring6框架下整体流程不变，核心变更为全面迁移Jakarta注解、优化BeanPostProcessor执行链，并原生支持虚拟线程场景下的作用域管理。

---

## 一、自动配置原理

### 1. 核心入口：@SpringBootApplication

SpringBoot启动注解是标准的三合一组合注解：

- `@SpringBootConfiguration`：标记为配置类，本质是`@Component`
- `@EnableAutoConfiguration`：**自动配置核心开关**
- `@ComponentScan`：默认扫描启动类所在包及其子包

### 2. 自动配置完整执行流程

核心由`AutoConfigurationImportSelector`驱动，执行链路如下：

1. 项目启动时，通过Spring SPI机制读取类路径下 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件
2. 文件中每行一个自动配置类的全限定名，批量加载所有候选配置
3. 结合`@Conditional`系列条件注解进行按需装配，不符合条件的配置类被过滤排除
4. 支持通过`exclude`、`excludeName`属性手动排除指定自动配置类

>
> **SpringBoot2→3 核心断层变更**：SpringBoot 2.x 用`META-INF/spring.factories`存储自动配置类；SpringBoot 3.0开始废弃该方式，**3.5.16已完全移除对spring.factories自动配置的支持**，旧版starter迁移必须改用新的imports文件。

### 3. 核心条件注解（条件装配基石）

| 注解                         | 作用                       | 典型场景                     |
| ---------------------------- | -------------------------- | ---------------------------- |
| @ConditionalOnClass          | 类路径存在指定类时生效     | 适配可选依赖，缺包不报错     |
| @ConditionalOnMissingBean    | 容器中不存在指定Bean时生效 | 兜底装配，允许用户自定义覆盖 |
| @ConditionalOnBean           | 容器中存在指定Bean时生效   | 依赖前置Bean的扩展装配       |
| @ConditionalOnProperty       | 配置文件属性匹配时生效     | 开关式功能控制               |
| @ConditionalOnWebApplication | Web应用环境时生效          | Web场景专属配置              |

### 4. SpringBoot 3.5.16 新增特性

- AOT编译阶段自动生成自动配置元数据，原生镜像启动速度提升30%+
- 内置`VirtualThreadAutoConfiguration`，配置`spring.threads.virtual.enabled=true`即可全局开启虚拟线程
- 新增`@AutoConfiguration`注解，显式指定自动配置的优先级、前后依赖关系，替代原有@Configuration+排序方案

---

## 二、Starter 机制

### 1. Starter 本质

Starter = **依赖坐标聚合 + 自动配置类 + 配置属性绑定**，是SpringBoot“约定优于配置”的核心体现。引入一个starter即可获得对应场景的全部能力，无需手动导入零散依赖、编写重复配置类。

### 2. 命名规范

- 官方starter：`spring-boot-starter-xxx`（如spring-boot-starter-web）
- 第三方/自定义starter：`xxx-spring-boot-starter`（如mybatis-spring-boot-starter）

### 3. Starter 标准组成结构

1. pom.xml：聚合该场景所需的所有依赖
2. 自动配置类：@AutoConfiguration + 条件注解，按需装配Bean
3. 配置属性类：@ConfigurationProperties 绑定application.yml配置项
4. SPI注册文件：`AutoConfiguration.imports` 注册自动配置类

### 💻 自定义Starter代码示例（SpringBoot3.5.16 合规版）

#### 1. pom.xml

```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="[http://maven.apache.org/POM/4.0.0](http://maven.apache.org/POM/4.0.0)"
         xmlns:xsi="[http://www.w3.org/2001/XMLSchema-instance](http://www.w3.org/2001/XMLSchema-instance)"
         xsi:schemaLocation="[http://maven.apache.org/POM/4.0.0](http://maven.apache.org/POM/4.0.0) [https://maven.apache.org/xsd/maven-4.0.0.xsd](https://maven.apache.org/xsd/maven-4.0.0.xsd)">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.ai</groupId>
    <artifactId>ai-demo-spring-boot-starter</artifactId>
    <version>1.0.0</version>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
            <version>3.5.16</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <version>3.5.16</version>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

#### 2. 配置属性类（JDK21 Record写法）

```
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.demo")
public record AiDemoProperties(
        boolean enabled,
        String apiKey,
        String endpoint
) {}
```

#### 3. 自动配置类

```
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(AiDemoService.class)
@EnableConfigurationProperties(AiDemoProperties.class)
@ConditionalOnProperty(prefix = "ai.demo", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiDemoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AiDemoService aiDemoService(AiDemoProperties properties) {
        return new AiDemoService(properties);
    }
}
```

#### 4. SPI注册文件

路径：`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
com.ai.autoconfigure.AiDemoAutoConfiguration
```

>
> 使用方引入starter依赖后，只需在yml配置`ai.demo.api-key`即可直接注入`AiDemoService`使用。

---

## 三、Bean 生命周期

### 1. 完整生命周期四阶段

1. **实例化**：调用构造方法创建Bean原始实例
2. **属性填充**：依赖注入，填充@Autowired、@Value等属性
3. **初始化**：执行初始化回调，完成资源加载、初始逻辑
4. **销毁**：容器关闭时执行销毁回调，释放连接、资源

### 2. 核心扩展点执行顺序

```
实例化 → 属性填充
  → BeanNameAware.setBeanName()
  → BeanFactoryAware.setBeanFactory()
  → ApplicationContextAware.setApplicationContext()
  → BeanPostProcessor.postProcessBeforeInitialization()
  → @PostConstruct（Jakarta注解）
  → InitializingBean.afterPropertiesSet()
  → 自定义init-method
  → BeanPostProcessor.postProcessAfterInitialization()
→ Bean就绪可用
→ 容器关闭
  → @PreDestroy
  → DisposableBean.destroy()
  → 自定义destroy-method
```

### 3. SpringBoot3.5.16 / Spring6 核心变更

- 所有JEE注解从`javax.*`全面迁移为`jakarta.*`，如`jakarta.annotation.PostConstruct`
- BeanPostProcessor执行链优化，Ordered接口优先级控制更精准
- 新增虚拟线程场景下的请求作用域Bean上下文传递支持
- 移除了多个废弃的生命周期接口和遗留扩展点

### 💻 Bean生命周期演示代码

```
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class LifeCycleDemo implements BeanNameAware, BeanFactoryAware,
        ApplicationContextAware, InitializingBean, DisposableBean {

    // 1. 构造方法：实例化阶段
    public LifeCycleDemo() {
        System.out.println("1. Bean实例化：执行构造方法");
    }

    @Override
    public void setBeanName(String name) {
        System.out.println("2. BeanNameAware：beanName = " + name);
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        System.out.println("3. BeanFactoryAware：注入BeanFactory");
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        System.out.println("4. ApplicationContextAware：注入ApplicationContext");
    }

    @PostConstruct
    public void postConstruct() {
        System.out.println("5. @PostConstruct 初始化回调");
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        System.out.println("6. InitializingBean.afterPropertiesSet");
    }

    @PreDestroy
    public void preDestroy() {
        System.out.println("7. @PreDestroy 销毁回调");
    }

    @Override
    public void destroy() throws Exception {
        System.out.println("8. DisposableBean.destroy");
    }
}
```

---

### ⚠️ 坑点 & 面试高频考点

#### 面试高频八股

1. **SpringBoot自动配置原理？**

>
> 答题要点：@EnableAutoConfiguration驱动 → AutoConfigurationImportSelector读取SPI导入文件 → 条件注解按需装配 → 支持手动排除。SpringBoot3用AutoConfiguration.imports替代了spring.factories。
2. **BeanPostProcessor 和 BeanFactoryPostProcessor 的区别？**

>
> 答题要点：BeanPostProcessor是**Bean级别**扩展，在Bean实例化后初始化前后执行；BeanFactoryPostProcessor是**工厂级别**扩展，在Bean定义加载完成后、实例化前执行，可修改Bean定义。
3. **@PostConstruct、InitializingBean、init-method 执行顺序？**

>
> 答题要点：@PostConstruct → InitializingBean → init-method；@PostConstruct优先级最高，SpringBoot3必须使用jakarta包下的注解。

#### 避坑红线

1. **致命坑**：SpringBoot3全面迁移Jakarta，`javax.annotation.PostConstruct`已失效，必须用`jakarta.annotation.PostConstruct`，否则静默不生效。
2. 自定义starter必须使用`AutoConfiguration.imports`文件，写spring.factories在3.5.16中完全不生效。
3. `@ConditionalOnMissingBean`必须在自动配置类中使用，不能在普通业务配置类中，否则可能被@ComponentScan提前扫描导致条件判断失效。

### 📝 记忆简语

>
> 自动配置三步走：开启注解、导文件、条件装；三版废弃factories，改用imports文件。
> Starter聚合依赖加配置，官方在前第三方在后。
> Bean周期四阶段：实例、填充、初始化、销毁；注解先于接口，接口先于自定义方法。
