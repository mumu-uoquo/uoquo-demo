/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.demo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uoquo.annotation.web.IgnoreAuth;
import com.uoquo.utils.spring.RedisUtil;
import com.uoquo.web.ReturnData;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Redis 基础操作演示控制器。
 *
 * <p>通过框架 Redis 工具 {@link RedisUtil} 暴露 set/get/delete/exists 四个基础操作，
 * 演示 {@code app-core} 与 Redis 的集成。全部接口 {@code @IgnoreAuth(all = true)}，
 * 免认证即可访问（需求 12.2）。
 *
 * <p>异常处理采用嵌套 try-catch：外层捕获 Redis 操作异常并记录日志后返回
 * {@code ReturnData<>(false)}（需求 6.5）；内层捕获日志记录自身可能抛出的异常并吞掉，
 * 仍然返回 {@code ReturnData<>(false)}（需求 6.6）。这样可保证 demo 接口永不让异常逃逸为 5xx。
 */
@Tag(name = "redis-demo")
@RestController
@RequestMapping("/demo/redis")
@IgnoreAuth(all = true)
public class RedisDemoController {

    private static final Logger log = LoggerFactory.getLogger(RedisDemoController.class);

    /** 演示用 Redis key。 */
    private static final String DEMO_KEY = "demo-key";

    /** 演示用 Redis value。 */
    private static final String DEMO_VALUE = "demo-value";

    /** 演示用默认过期时间（秒）。 */
    private static final int DEFAULT_TTL_SECONDS = 300;

    /**
     * 将演示值存储到 Redis，返回布尔结果。
     */
    @GetMapping("/set")
    @Operation(operationId = "userRedisSet", description = "Redis set 演示")
    public ReturnData<Boolean> set() {
        try {
            RedisUtil.put(DEMO_KEY, DEMO_VALUE, DEFAULT_TTL_SECONDS);
            return new ReturnData<>(true);
        } catch (Exception e) {
            try {
                log.error("Redis set 失败, key={}", DEMO_KEY, e);
            } catch (Exception ignore) {
                // 日志记录失败也吞掉
            }
            return new ReturnData<>(false);
        }
    }

    /**
     * 读取演示 key 对应的 value，返回该 value。
     */
    @GetMapping("/get")
    @Operation(operationId = "userRedisGet", description = "Redis get 演示")
    public ReturnData<String> get() {
        try {
            String value = RedisUtil.get(DEMO_KEY, String.class);
            return new ReturnData<>(value);
        } catch (Exception e) {
            try {
                log.error("Redis get 失败, key={}", DEMO_KEY, e);
            } catch (Exception ignore) {
                // 日志记录失败也吞掉
            }
            return new ReturnData<>((String) null);
        }
    }

    /**
     * 删除演示 key，返回布尔结果。
     */
    @DeleteMapping("/delete")
    @Operation(operationId = "userRedisDelete", description = "Redis delete 演示")
    public ReturnData<Boolean> delete() {
        try {
            RedisUtil.remove(DEMO_KEY);
            return new ReturnData<>(true);
        } catch (Exception e) {
            try {
                log.error("Redis delete 失败, key={}", DEMO_KEY, e);
            } catch (Exception ignore) {
                // 日志记录失败也吞掉
            }
            return new ReturnData<>(false);
        }
    }

    /**
     * 检查演示 key 是否存在，返回布尔结果。
     */
    @GetMapping("/exists")
    @Operation(operationId = "userRedisExists", description = "Redis exists 演示")
    public ReturnData<Boolean> exists() {
        try {
            boolean present = RedisUtil.exist(DEMO_KEY);
            return new ReturnData<>(present);
        } catch (Exception e) {
            try {
                log.error("Redis exists 失败, key={}", DEMO_KEY, e);
            } catch (Exception ignore) {
                // 日志记录失败也吞掉
            }
            return new ReturnData<>(false);
        }
    }
}
