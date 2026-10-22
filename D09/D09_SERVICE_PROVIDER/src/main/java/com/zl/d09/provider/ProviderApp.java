package com.zl.d09.provider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 服务提供者启动类。
 *
 * <p>启动后向 Nacos 注册服务名 {@code d09-provider}，对外提供 /provider/** 接口。</p>
 */
@SpringBootApplication
public class ProviderApp {

    public static void main(String[] args) {
        SpringApplication.run(ProviderApp.class, args);
    }
}
