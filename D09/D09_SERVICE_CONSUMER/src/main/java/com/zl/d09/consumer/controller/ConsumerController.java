package com.zl.d09.consumer.controller;

import com.zl.d09.consumer.feign.ProviderFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 消费者入口：通过 Feign 调用 Provider，覆盖正常、超时、错误三类场景。
 */
@Slf4j
@RestController
@RequestMapping("/consumer")
@RequiredArgsConstructor
public class ConsumerController {

    private final ProviderFeignClient providerFeignClient;

    /**
     * 正常调用：返回 Provider 回显结果。
     */
    @GetMapping("/echo")
    public Map<String, Object> echo(@RequestParam(defaultValue = "zl") String name) {
        log.info("Consumer 调用 Provider echo, name={}", name);
        return providerFeignClient.echo(name);
    }

    /**
     * 慢调用：Provider 延迟 delay 毫秒；超过 Feign readTimeout 即触发降级。
     */
    @GetMapping("/slow")
    public Map<String, Object> slow(@RequestParam(defaultValue = "5000") long delay) {
        log.info("Consumer 调用 Provider slow, delay={}ms", delay);
        return providerFeignClient.slow(delay);
    }

    /**
     * 错误调用：Provider 抛异常，触发错误降级。
     */
    @GetMapping("/error")
    public Map<String, Object> error() {
        log.info("Consumer 调用 Provider error");
        return providerFeignClient.error();
    }
}
