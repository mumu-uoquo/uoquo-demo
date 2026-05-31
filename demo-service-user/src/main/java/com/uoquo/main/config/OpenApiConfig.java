/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.main.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    /**
     * 配置过滤规则
     * 若不配置该GroupedOpenApi, 默认扫描所有接口并生成文档
     * @return
     */
    @Bean
    public GroupedOpenApi testMyApi() {
        return GroupedOpenApi.builder()
                // 接口过滤，据此增加接口扫描规则（扫描@Operation注解标注的接口）。想皮一下的话，亦可自定义注解
                .addOpenApiMethodFilter(method -> method.isAnnotationPresent(Operation.class))
                .group("my") // 分组名，可建多个不同分组，分别扫描不同位置接口
                .pathsToMatch("/my/**")
                .build();
    }
    /**
     * 配置过滤规则
     * 若不配置该GroupedOpenApi, 默认扫描所有接口并生成文档
     * @return
     */
    @Bean
    public GroupedOpenApi defaultApi() {
        return GroupedOpenApi.builder()
                // 接口过滤，据此增加接口扫描规则（扫描@Operation注解标注的接口）。想皮一下的话，亦可自定义注解
                .addOpenApiMethodFilter(method -> method.isAnnotationPresent(Operation.class))
                .group("default") // 分组名，可建多个不同分组，分别扫描不同位置接口
//                .pathsToMatch("/admin/category/**")
                .pathsToExclude("/my/**")
                .build();
    }

    @Bean
    public OpenAPI springShopOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("SpringShop API")
                        .description("Spring shop sample application")
                        .version("v0.0.1")
                        .license(new License().name("Apache 2.0").url("http://springdoc.org")))
                .externalDocs(new ExternalDocumentation()
                        .description("SpringShop Wiki Documentation")
                        .url("https://springshop.wiki.github.org/docs"));
    }
}
