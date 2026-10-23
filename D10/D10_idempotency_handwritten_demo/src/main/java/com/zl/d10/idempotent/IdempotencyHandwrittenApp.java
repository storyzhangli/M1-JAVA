package com.zl.d10.idempotent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动类：数据库唯一约束 + 事务性幂等记录演示。
 */
@SpringBootApplication
public class IdempotencyHandwrittenApp {

    public static void main(String[] args) {
        SpringApplication.run(IdempotencyHandwrittenApp.class, args);
    }
}
