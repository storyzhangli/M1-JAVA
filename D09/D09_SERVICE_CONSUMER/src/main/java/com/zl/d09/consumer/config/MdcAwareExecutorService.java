package com.zl.d09.consumer.config;

import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * MDC 感知的 ExecutorService 包装器：解决「跨线程 MDC 丢失」。
 *
 * <p>「原理」MDC 基于 ThreadLocal，线程池工作线程默认拿不到调用方的 MDC。
 * 本包装器在「提交任务的线程」（即请求线程）中快照 MDC（{@code MDC.getCopyOfContextMap()}），
 * 并在「工作线程」执行前恢复、执行后清理：</p>
 * <ul>
 *     <li>提交时：捕获当前 MDC 快照（调用方线程）；</li>
 *     <li>执行前：将快照 set 到工作线程；</li>
 *     <li>执行后：恢复工作线程原 MDC，避免线程池复用导致串号。</li>
 * </ul>
 */
public class MdcAwareExecutorService implements ExecutorService {

    private final ExecutorService delegate;

    public MdcAwareExecutorService(ExecutorService delegate) {
        this.delegate = delegate;
    }

    /** 包装 Runnable：捕获调用方 MDC，工作线程执行时恢复。 */
    private Runnable wrap(Runnable runnable) {
        Map<String, String> callerContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> workerContext = MDC.getCopyOfContextMap();
            applyContext(callerContext);
            try {
                runnable.run();
            } finally {
                applyContext(workerContext);
            }
        };
    }

    /** 包装 Callable：捕获调用方 MDC，工作线程执行时恢复。 */
    private <V> Callable<V> wrap(Callable<V> callable) {
        Map<String, String> callerContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> workerContext = MDC.getCopyOfContextMap();
            applyContext(callerContext);
            try {
                return callable.call();
            } finally {
                applyContext(workerContext);
            }
        };
    }

    /** 将上下文应用到当前线程；为 null 则清空，保证不残留。 */
    private void applyContext(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(wrap(command));
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(wrap(task), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        Collection<Callable<T>> wrapped = new ArrayList<>(tasks.size());
        for (Callable<T> t : tasks) {
            wrapped.add(wrap(t));
        }
        return delegate.invokeAll(wrapped);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        Collection<Callable<T>> wrapped = new ArrayList<>(tasks.size());
        for (Callable<T> t : tasks) {
            wrapped.add(wrap(t));
        }
        return delegate.invokeAll(wrapped, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        Collection<Callable<T>> wrapped = new ArrayList<>(tasks.size());
        for (Callable<T> t : tasks) {
            wrapped.add(wrap(t));
        }
        return delegate.invokeAny(wrapped);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        Collection<Callable<T>> wrapped = new ArrayList<>(tasks.size());
        for (Callable<T> t : tasks) {
            wrapped.add(wrap(t));
        }
        return delegate.invokeAny(wrapped, timeout, unit);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
}
