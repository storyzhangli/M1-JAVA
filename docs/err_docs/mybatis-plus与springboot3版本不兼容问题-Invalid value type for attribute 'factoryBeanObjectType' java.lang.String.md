# 报错原因

`Invalid value type for attribute 'factoryBeanObjectType': java.lang.String`

>
> 根因：**SpringBoot3.x（6.x Spring核心包）对FactoryBean元数据校验规则升级**，老的 `mybatis-plus-boot-starter`（SpringBoot2的starter）内置的mybatis-spring版本过低，注入Mapper代理Bean时，`factoryBeanObjectType`被传入String字符串，新版Spring要求必须是Class类型，直接抛出异常。
>
>
> ❌ 错误：`mybatis-plus-boot-starter`（这是Boot2用的！）
> ✅ 正确：**`mybatis-plus-spring-boot3-starter`**（专门给SpringBoot3）

## 修复pom.xml（直接替换MP依赖）

```
<!-- 删掉原来的这一行！！ -->
<!--
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>${mybatis-plus.version}</version>
</dependency>
-->

<!-- SpringBoot3专用starter -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.12</version>
</dependency>
```

>
> 修改后属性：

```
<properties>
    <java.version>21</java.version>
    <mybatis-plus.version>3.5.12</mybatis-plus.version>
</properties>
```

## 完整修复后的dependencies片段

```
<dependencies>
    <!-- SpringBoot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <!-- Redis -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <!-- mysql驱动 -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>
    <!-- ========== SpringBoot3 专用MP starter ========== -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        <version>${mybatis-plus.version}</version>
    </dependency>
    <!-- lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <!-- 测试 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

# 修改之后，IDE操作步骤（必须执行）

1. IDEA右侧Maven面板 → **Reload All Maven Projects**（刷新依赖）
2. 执行 `mvn clean` 清理旧的编译产物
3. 重启IDEA（可选，防止IDEA缓存旧依赖）
4. 重新启动项目，报错消失

>
> 代码层面完全不用改动：`@Mapper`、`@MapperScan`、entity、mapper、service、controller全部保留不变。只是换starter包。

## 备选兜底方案（如果上面方案你仍有冲突）

保留`mybatis-plus-boot-starter`，手动强制升级mybatis-spring版本：

```
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.mybatis</groupId>
            <artifactId>mybatis-spring</artifactId>
            <version>3.0.4</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

>
> 推荐优先使用**spring-boot3-starter**方案，是官方推荐，更稳妥。

# 补充注意点

1. MyBatis-Plus 3.5.12 是稳定版，适配 SpringBoot3.5.x + JDK21
2. `@MapperScan("com.aijava.mapper")` 保留，不要删除
3. Mapper接口上`@Mapper`注解可保留，也可以只依靠MapperScan，二选一都可以

📝记忆简语
Boot3不要boot-starter，改用spring-boot3-starter；
factoryBeanObjectType报错，根源是mybatis-spring版本低；
改pom、reload maven，代码一行不用改。

改完之后项目能正常启动，要不要继续写RBAC的三张表SQL和实体类？