/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.main.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Import(DeveloperConfig.class)
@Order(0)
public class ApplicationConfig implements WebMvcConfigurer {

    /**
     * 添加跨域支持
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 允许跨域访问的域名，若有80，443外的端口，也需要带端口号，各个应用最好按实际进行固定，不要采用*来允许所有（如："http://cdn.yocaly.com:8080", "https://cdn.yocaly.com"）
                .allowedOriginPatterns("*") // 新版的去除了allowedOrigins
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")       // 允许的请求头header
                .allowCredentials(true)    // 允许前端发送cookie
                //.exposedHeaders("*")     // 允许的响应头header
                .maxAge(3600); // 单位秒，一次OPTIONS预校验的有效期，该期间内跨域请求，不需要再发送预校验请求
    }

}
