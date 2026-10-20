package com.zl.d03;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * D03 MySQL 核心模块启动类。
 * 同时承担根路径健康检查，访问根路径返回模块标识。
 */
@RestController
@SpringBootApplication
public class D03App {

    /** 根路径健康检查，返回当前模块标识。 */
    @RequestMapping
    public String home() {
        return "Hello World | hello D03";
    }

    public static void main(String[] args) {
        SpringApplication.run(D03App.class, args);
    }
}
