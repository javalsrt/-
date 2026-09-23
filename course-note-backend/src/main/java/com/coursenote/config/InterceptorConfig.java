package com.coursenote.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/login",
                        "/user/account-login",
                        "/teacher/login",
                        "/ws/**",
                        "/uploads/**",
                        // aiStudy 鉴权模块的登录端点：/api/auth/** 纳入 Spring Security 鉴权，
                        // 其登录接口本身不可被 JwtInterceptor 二次拦截
                        "/auth/**"
                );
    }
}
