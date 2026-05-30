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
import org.springframework.web.bind.annotation.RequestParam;
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
public class RedisDemoController {

    private static final Logger log = LoggerFactory.getLogger(RedisDemoController.class);

    /** 演示用默认过期时间（秒）。 */
    private static final int DEFAULT_TTL_SECONDS = 300;

    /**
     * 将 value 存储到 key 下，返回布尔结果（需求 6.1）。
     */
    @RequestMapping("/set")
    @IgnoreAuth(all = true)
    @Operation(description = "Redis set", operationId = "redisSet")
    public ReturnData<Boolean> set(@RequestParam String key, @RequestParam String value) {
        try {
            RedisUtil.put(key, value, DEFAULT_TTL_SECONDS);
            return new ReturnData<>(true);
        } catch (Exception e) {
            try {
                log.error("Redis set 失败, key={}", key, e);   // 需求 6.5：记录异常
            } catch (Exception ignore) {
                // 需求 6.6：记录日志本身失败也吞掉，仍返回 false
            }
            return new ReturnData<>(false);
        }
    }

    /**
     * 读取 key 对应的 value，返回该 value（需求 6.2）；异常时返回包装 null 的 ReturnData。
     */
    @GetMapping("/get")
    @IgnoreAuth(all = true)
    @Operation(description = "Redis get", operationId = "redisGet")
    public ReturnData<String> get(@RequestParam String key) {
        try {
            String value = RedisUtil.get(key, String.class);
            return new ReturnData<>(value);
        } catch (Exception e) {
            try {
                log.error("Redis get 失败, key={}", key, e);   // 需求 6.5：记录异常
            } catch (Exception ignore) {
                // 需求 6.6：记录日志本身失败也吞掉，仍返回 null 值
            }
            return new ReturnData<>((String) null);
        }
    }

    /**
     * 删除 key，返回布尔结果（需求 6.3）。
     */
    @DeleteMapping("/delete")
    @IgnoreAuth(all = true)
    @Operation(description = "Redis delete", operationId = "redisDelete")
    public ReturnData<Boolean> delete(@RequestParam String key) {
        try {
            RedisUtil.remove(key);
            return new ReturnData<>(true);
        } catch (Exception e) {
            try {
                log.error("Redis delete 失败, key={}", key, e);   // 需求 6.5：记录异常
            } catch (Exception ignore) {
                // 需求 6.6：记录日志本身失败也吞掉，仍返回 false
            }
            return new ReturnData<>(false);
        }
    }

    /**
     * 报告 key 是否存在，返回布尔结果（需求 6.4）。
     */
    @GetMapping("/exists")
    @IgnoreAuth(all = true)
    @Operation(description = "Redis exists", operationId = "redisExists")
    public ReturnData<Boolean> exists(@RequestParam String key) {
        try {
            boolean present = RedisUtil.exist(key);
            return new ReturnData<>(present);
        } catch (Exception e) {
            try {
                log.error("Redis exists 失败, key={}", key, e);   // 需求 6.5：记录异常
            } catch (Exception ignore) {
                // 需求 6.6：记录日志本身失败也吞掉，仍返回 false
            }
            return new ReturnData<>(false);
        }
    }
}
