package com.zl.d09.trace;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * MDC 上下文任务装饰器：解决「异步线程丢失 traceId」与「线程池复用串号」两个问题。
 *
 * <p>「原理」MDC 基于 {@link ThreadLocal}，线程池中的工作线程与提交任务的请求线程不是同一个线程，
 * 因此默认情况下异步任务读不到 traceId。{@link TaskDecorator} 是 Spring 提供的任务执行切面：
 * 在任务被「提交」时（仍在请求线程）复制 MDC 快照，在任务被「执行」时（工作线程）还原，
 * 执行结束后恢复/清理工作线程原有的 MDC。</p>
 *
 * <p>「串号防护」工作线程会被复用，若任务执行后不清理，下一个复用该线程的任务可能读到
 * 上一个任务的 traceId。因此在 finally 中恢复工作线程执行前的 MDC 快照（通常为空）。</p>
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // 提交时刻：在请求线程中快照 MDC（此时含 traceId）
        final Map<String, String> callerContext = MDC.getCopyOfContextMap();

        return () -> {
            // 执行时刻：记录工作线程原有 MDC，便于执行后还原
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (callerContext != null) {
                    // 还原调用方上下文（含 traceId）
                    MDC.setContextMap(callerContext);
                } else {
                    MDC.clear();
                }
                runnable.run();
            } finally {
                // 还原工作线程原有上下文，避免线程复用串号；原本为空则清空
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}
