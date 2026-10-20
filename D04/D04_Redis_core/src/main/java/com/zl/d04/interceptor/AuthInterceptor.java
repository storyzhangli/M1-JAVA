package com.zl.d04.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zl.d04.common.Result;
import com.zl.d04.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * 认证拦截器。
 *
 * <p>在请求处理前校验请求头中的 token：存在于 Redis 即放行，并将用户 ID 写入 {@link UserContext}；
 * 校验失败直接返回 401。请求结束后清理用户上下文。</p>
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 前置校验：取 token → 校验 Redis → 写入用户上下文并续期。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 从请求头取token
        String token = request.getHeader("Authorization");
        if (token == null || token.isBlank()) {
            writeError(response, 401, "未登录，缺少token");
            return false;
        }

        // 2. 校验Redis中是否存在
        String key = "auth:token:" + token;
        Object userId = redisTemplate.opsForValue().get(key);
        if (userId == null) {
            writeError(response, 401, "token无效或已过期");
            return false;
        }

        // 3. 存入用户上下文，续期2小时
        UserContext.setUserId(Long.valueOf(userId.toString()));
        redisTemplate.expire(key, 2, TimeUnit.HOURS);
        return true;
    }

    /**
     * 请求完成后清理用户上下文，避免线程复用造成数据串号。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    /**
     * 向响应体写入 JSON 格式的错误信息。
     */
    private void writeError(HttpServletResponse response, int code, String msg) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, msg)));
    }
}
