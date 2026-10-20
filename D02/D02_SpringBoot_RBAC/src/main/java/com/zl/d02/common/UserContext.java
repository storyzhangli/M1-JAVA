package com.zl.d02.common;

/**
 * 当前登录用户上下文。
 *
 * <p>基于 {@link ThreadLocal} 在当前请求线程内保存用户 ID，实现线程隔离。
 * 「坑点提示」请求结束后必须调用 {@link #clear()} 清理，否则在线程池复用线程时会造成数据串号与内存泄漏。</p>
 */
public class UserContext {
    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();

    /**
     * 保存当前登录用户 ID。
     *
     * @param userId 用户 ID
     */
    public static void setUserId(Long userId) {
        CURRENT_USER.set(userId);
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 用户 ID，未登录时为 {@code null}
     */
    public static Long getUserId() {
        return CURRENT_USER.get();
    }

    /**
     * 清除当前线程的用户信息，请求结束时必须调用。
     */
    public static void clear() {
        CURRENT_USER.remove();
    }
}
