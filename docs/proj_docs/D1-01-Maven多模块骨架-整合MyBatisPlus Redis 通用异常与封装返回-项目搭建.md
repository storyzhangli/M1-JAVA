# JDK21 + SpringBoot3.5.x + Maven3.9.9 Demo

>
> 技术栈：JDK21 | SpringBoot 3.5.0 | Maven3.9.9 | MyBatis-Plus | Redis | 统一返回结果封装
> 配套：Maven Wrapper(3.9.9)，适合你阶段1项目骨架搭建，直接复制可用

## 1. pom.xml

```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="[http://maven.apache.org/POM/4.0.0](http://maven.apache.org/POM/4.0.0)" xmlns:xsi="[http://www.w3.org/2001/XMLSchema-instance](http://www.w3.org/2001/XMLSchema-instance)"
         xsi:schemaLocation="[http://maven.apache.org/POM/4.0.0](http://maven.apache.org/POM/4.0.0) [https://maven.apache.org/xsd/maven-4.0.0.xsd](https://maven.apache.org/xsd/maven-4.0.0.xsd)">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.16</version>
        <relativePath/>
    </parent>
    <groupId>com.aijava</groupId>
    <artifactId>ai-knowledge-base</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>ai-knowledge-base</name>
    <description>JavaAI项目骨架-阶段1</description>

    <properties>
        <java.version>21</java.version>
        <mybatis-plus.version>3.5.12</mybatis-plus.version>
    </properties>

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

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                    <encoding>UTF-8</encoding>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

## 2. application.yml

`src/main/resources/application.yml`

```
server:
  port: 8080

spring:
  # mysql
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://127.0.0.1:3306/ai_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 你的数据库密码
  # redis
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password: #无密码留空
      database: 0

# MyBatis-Plus配置
mybatis-plus:
  mapper-locations: classpath:mapper/**/*.xml
  type-aliases-package: com.aijava.entity
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto
```

## 3. 统一返回封装（通用Result）

`com.aijava.common.Result.java`

```
package com.aijava.common;

import lombok.Data;

@Data
public class Result<T> {
    private Integer code;
    private String msg;
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMsg("success");
        r.setData(data);
        return r;
    }
    public static <T> Result<T> success() {
        return success(null);
    }
    public static <T> Result<T> fail(Integer code, String msg) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }
}
```

## 4. 全局异常处理器（配套统一返回）

`com.aijava.common.GlobalExceptionHandler.java`

```
package com.aijava.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e){
        log.error("全局异常：",e);
        return Result.fail(500, "服务器内部异常："+e.getMessage());
    }
}
```

## 5. 实体类 User

`com.aijava.entity.User.java`

```
package com.aijava.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String phone;
}
```

## 6. Mapper接口

`com.aijava.mapper.UserMapper.java`

```
package com.aijava.mapper;

import com.aijava.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

## 7. Service层

`com.aijava.service.UserService.java`

```
package com.aijava.service;

import com.aijava.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

public interface UserService extends IService<User> {
}
```

`com.aijava.service.impl.UserServiceImpl.java`

```
package com.aijava.service.impl;

import com.aijava.entity.User;
import com.aijava.mapper.UserMapper;
import com.aijava.service.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
```

## 8. Controller（演示DB查询 + Redis缓存）

`com.aijava.controller.UserController.java`

```
package com.aijava.controller;

import com.aijava.common.Result;
import com.aijava.entity.User;
import com.aijava.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final RedisTemplate<String,Object> redisTemplate;

    @GetMapping("/{id}")
    public Result<User> getUserById(@PathVariable Long id){
        String key = "user:info:"+id;
        //1.查询redis缓存
        User cacheUser = (User) redisTemplate.opsForValue().get(key);
        if(cacheUser != null){
            return Result.success(cacheUser);
        }
        //2.缓存未命中，查数据库
        User dbUser = userService.getById(id);
        if(dbUser == null){
            return Result.fail(404,"用户不存在");
        }
        //3.写入redis，过期10分钟
        redisTemplate.opsForValue().set(key,dbUser,10, TimeUnit.MINUTES);
        return Result.success(dbUser);
    }
}
```

## 9. 启动类（MyBatisPlus扫描Mapper）

`com.aijava.AiKnowledgeBaseApplication.java`

```
package com.aijava;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.aijava.mapper")
public class AiKnowledgeBaseApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiKnowledgeBaseApplication.class, args);
    }
}
```

## 10. 数据库建表SQL

```
CREATE DATABASE IF NOT EXISTS ai_db DEFAULT CHARACTER SET utf8mb4;
USE ai_db;
CREATE TABLE t_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50),
    phone VARCHAR(20)
);
INSERT INTO t_user(username,phone) VALUES ("zhangsan","13800138000");
```

## 11. Redis序列化配置【重要！SpringBoot3 Redis默认Jdk序列化，需要替换】

`com.aijava.config.RedisConfig.java`

```
package com.aijava.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory){
        RedisTemplate<String,Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashKeySerializer(keySerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
```

# 项目目录结构

```
ai-knowledge-base
├── pom.xml
├── .mvn/
├── mvnw / mvnw.cmd
└── src/main/java/com/aijava
    ├── AiKnowledgeBaseApplication.java
    ├── common/
    │   ├── Result.java
    │   └── GlobalExceptionHandler.java
    ├── config/
    │   └── RedisConfig.java
    ├── entity/
    │   └── User.java
    ├── mapper/
    │   └── UserMapper.java
    ├── service/
    │   ├── UserService.java
    │   └── impl/UserServiceImpl.java
    └── controller/
        └── UserController.java
```

## ✅ 测试接口

GET `[http://127.0.0.1:8080/user/1](http://127.0.0.1:8080/user/1)`
返回样例：

```
{
    "code": 200,
    "msg": "success",
    "data": {
        "id": 1,
        "username": "zhangsan",
        "phone": "13800138000"
    }
}
```

## 📝 记忆简语

Boot3.5 JDK21，MP3.5.7，Redis配Json序列化；
统一Result加全局异常，缓存先查Redis再查库；
mapper扫描不要忘，wrapper锁定Maven3.9.9。
