package com.zl.d10.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    /**
     * BCrypt密码编码器
     * 工作特点：自动加盐；每次加密结果不一样，比对用matches方法
     */
    @Bean
    public PasswordEncoder passwordEncoder(){
        // 参数strength 4~31，默认10；越大加密越耗CPU，项目默认10足够
        return new BCryptPasswordEncoder(10);
    }
}
