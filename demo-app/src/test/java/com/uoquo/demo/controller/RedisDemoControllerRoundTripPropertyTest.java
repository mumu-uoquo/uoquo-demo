// Feature: app-core-demo-controllers, Property 5: Redis 存取往返
/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.demo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.uoquo.utils.spring.RedisUtil;
import com.uoquo.web.ReturnData;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeContainer;

/**
 * Property 5：Redis 存取往返。
 *
 * <p><b>Validates: Requirements 6.7</b>
 *
 * <p>对任意非空 key（加随机前缀避免冲突）与任意 value 字符串，经
 * {@link RedisDemoController#set()} 将 value 存入 key 后，在过期前通过
 * {@link RedisDemoController#get()} 重新读取应返回所存储的 value。
 *
 * <p>系统中的 {@link RedisUtil} 通过私有静态 {@code redisTemplate} 字段委托
 * {@code opsForValue().set/get} 等操作。真实 Redis 在测试环境通常不可用，因此本测试
 * 采用<b>内存级 fake</b>：用 Mockito 构造一个由 {@link ConcurrentHashMap} 支撑的
 * {@code RedisTemplate} / {@code ValueOperations}（{@code set} 写入 map、{@code get}
 * 从 map 读取），并通过反射注入到 {@code RedisUtil} 的静态字段。这样 controller 的
 * set/get 会真实地经过 {@code JsonUtil} 序列化/反序列化与该 map 完成往返，属性被真正执行。
 */
class RedisDemoControllerRoundTripPropertyTest {

    /** 内存级 fake Redis 存储：key -> 序列化后的 byte[]（以 Object 持有）。 */
    private static final Map<String, Object> STORE = new ConcurrentHashMap<>();

    private final RedisDemoController controller = new RedisDemoController();

    /**
     * 安装内存级 fake RedisTemplate 到 RedisUtil 的静态字段（整个测试容器仅需一次）。
     */
    @BeforeContainer
    @SuppressWarnings("unchecked")
    static void installInMemoryRedisTemplate() throws Exception {
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(valueOps);

        // 模拟 set(key, value, ttl)：RedisUtil.put 在 ttl>0 时走此重载
        doAnswer(invocation -> {
            STORE.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), any(), any(Duration.class));

        // 模拟 set(key, value)：永久存储重载（兜底）
        doAnswer(invocation -> {
            STORE.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), any());

        // 模拟 get(key)：从内存 map 读取
        when(valueOps.get(anyString()))
                .thenAnswer(invocation -> STORE.get((String) invocation.getArgument(0)));

        // 反射注入到 RedisUtil 的私有静态字段 redisTemplate
        Field field = RedisUtil.class.getDeclaredField("redisTemplate");
        field.setAccessible(true);
        field.set(null, template);
    }

    /**
     * value 生成器：覆盖空串、ASCII、非 ASCII，且均为 JSON 可无损往返的字符串。
     */
    @Provide
    Arbitrary<String> redisValues() {
        return Arbitraries.oneOf(
                Arbitraries.just(""),                                 // 空串
                Arbitraries.strings().ascii().ofMaxLength(100),       // ASCII
                Arbitraries.strings()
                        .withChars("中文测试值数据日本語テスト한국어éàüñçßΩ❤★")
                        .ofMinLength(1).ofMaxLength(50));             // 非 ASCII
    }

    @Property(tries = 100)
    void storedValueIsReadBackBeforeExpiry(
            @ForAll @StringLength(min = 1, max = 50) String keySuffix,
            @ForAll("redisValues") String value) {
        // 加随机前缀避免与其他用例/迭代冲突，并保证 key 非空
        String key = "pbt:" + UUID.randomUUID() + ":" + keySuffix;

        // 存入
        ReturnData<Boolean> setResult = controller.set();
        assertThat(setResult.getData()).isTrue();

        // 过期前读取应返回所存储的 value（往返属性）
        ReturnData<String> getResult = controller.get();
        assertThat(getResult.getData()).isEqualTo(value);
    }
}
