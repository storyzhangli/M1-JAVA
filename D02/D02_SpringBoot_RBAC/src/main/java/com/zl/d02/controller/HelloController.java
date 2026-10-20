package com.zl.d02.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 示例接口，用于快速验证 Spring Boot 环境是否正常。
 */
@RestController
public class HelloController {

    /**
     * 返回固定问候语。
     *
     * @return 问候字符串
     */
    @GetMapping("/hello")
    public String sayHello() {
        return "Hello World";
    }
}
