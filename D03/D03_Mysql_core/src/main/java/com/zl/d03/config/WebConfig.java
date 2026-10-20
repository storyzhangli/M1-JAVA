package com.zl.d03.config;

import com.zl.d03.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 *
 * <p>注册 {@link AuthInterceptor}，拦截所有请求并放行登录、注册以及 Knife4j/OpenAPI 相关路径。</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;

    /**
     * 注册认证拦截器并配置放行路径。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/loginForm",
                        "/auth/loginJson",
                        "/auth/loginCrypt",
                        "/auth/register",
                        "/error",
                        // ========== Knife4j / OpenAPI 全量放行 ==========
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/v3/api-docs",
                        "/swagger-resources/**",
                        "/swagger-ui/**",
                        "/favicon.ico"
                );
    }
}
