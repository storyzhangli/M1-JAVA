package com.zl.d09.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 服务消费者启动类。
 *
 * <p>通过 {@link EnableFeignClients} 开启 OpenFeign 客户端扫描，
 * 启动后向 Nacos 注册服务名 {@code d09-consumer}，并按服务名调用 {@code d09-provider}。</p>
 */
@SpringBootApplication
@EnableFeignClients
public class ConsumerApp {

    public static void main(String[] args) {
        SpringApplication.run(ConsumerApp.class, args);
    }
}
