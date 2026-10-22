package com.zl.d09.provider.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 服务提供者接口。
 *
 * <p>提供三类端点，用于覆盖正常调用、超时、错误三种场景：</p>
 * <ul>
 *     <li>{@code /provider/echo}：正常快速返回；</li>
 *     <li>{@code /provider/slow}：按 delay 延迟返回，用于触发 Feign 读取超时与熔断；</li>
 *     <li>{@code /provider/error}：直接抛异常，用于触发错误降级。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/provider")
public class ProviderController {

    /**
     * 正常回显接口。
     */
    @GetMapping("/echo")
    public Map<String, Object> echo(@RequestParam(defaultValue = "default") String name) {
        log.info("Provider 收到 echo 请求, name={}", name);
        return Map.of(
                "service", "d09-provider",
                "name", name,
                "thread", Thread.currentThread().getName(),
                "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * 受控慢接口：休眠指定毫秒数，用于演示超时与熔断。
     */
    @GetMapping("/slow")
    public Map<String, Object> slow(@RequestParam(defaultValue = "5000") long delay) throws InterruptedException {
        log.info("Provider 收到 slow 请求, delay={}ms", delay);
        Thread.sleep(delay);
        log.info("Provider slow 处理完成, delay={}ms", delay);
        return Map.of(
                "service", "d09-provider",
                "slept", delay,
                "thread", Thread.currentThread().getName()
        );
    }

    /**
     * 受控错误接口：直接抛出运行时异常。
     */
    @GetMapping("/error")
    public Map<String, Object> error() {
        log.warn("Provider 收到 error 请求，准备抛出异常");
        throw new RuntimeException("Provider 模拟业务异常");
    }
}
