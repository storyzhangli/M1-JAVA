package com.zl.d06.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zl.d06.common.Result;
import com.zl.d06.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private void writeError(HttpServletResponse response, int code, String msg) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, msg)));
    }
}
