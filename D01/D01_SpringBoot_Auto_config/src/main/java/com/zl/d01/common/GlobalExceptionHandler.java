package com.zl.d01.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * <p>基于 {@link RestControllerAdvice} 统一拦截 Controller 层抛出的异常，
 * 将异常转换为统一返回体 {@link Result}，避免异常堆栈直接暴露给前端。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 兜底处理所有未被精确捕获的异常。
     *
     * @param e 异常对象
     * @return 500 错误结果
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("全局异常：", e);
        return Result.fail(500, "服务器内部异常：" + e.getMessage());
    }
}
