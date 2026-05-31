// Feature: service-refactor-remote-events, Property 1: CustomEvent 序列化往返一致
/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.user.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uoquo.cloud.events.RemoteEvent;
import com.uoquo.utils.json.JsonUtil;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import org.junit.jupiter.api.Test;

/**
 * Property 1：CustomEvent 序列化往返一致。
 *
 * <p>For any {@link RemoteEvent}（其 businessType、operationType、operationStatus 为长度 0–255
 * 的任意字符串（含空串），destinationService 为某服务名、{@code "**"} 或 {@code null}），
 * 经 {@link JsonUtil#serialize(Object)} 序列化为 String 再经
 * {@link JsonUtil#deserialize(String, Class)} 反序列化为 {@link RemoteEvent} 后，
 * 所得对象非 null，且其 businessType、operationType、operationStatus、destinationService
 * 均与原始事件逐字段相等。</p>
 *
 * <p><b>Validates: Requirements 14.2</b></p>
 */
class CustomEventSerializationPropertyTest {

    /**
     * 事件字符串字段（businessType/operationType/operationStatus）生成器：
     * 覆盖空串、ASCII 字符串（1–50 字符）与中文字符串（1–20 字符）。
     */
    @Provide
    Arbitrary<String> eventTexts() {
        return Arbitraries.oneOf(
                Arbitraries.just(""),
                Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(50),
                Arbitraries.strings()
                        .withCharRange('\u4e00', '\u9fa5')
                        .ofMinLength(1).ofMaxLength(20));
    }

    /**
     * destinationService 生成器：覆盖 null、"**"、"demo-cloud-book"、"demo-cloud-user"
     * 以及任意 ASCII 字符串。
     */
    @Provide
    Arbitrary<String> destinationServices() {
        return Arbitraries.oneOf(
                Arbitraries.just((String) null),
                Arbitraries.just("**"),
                Arbitraries.just("demo-cloud-book"),
                Arbitraries.just("demo-cloud-user"),
                Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(30));
    }

    /**
     * Property 1：对任意 CustomEvent，序列化再反序列化后 businessType、operationType、
     * operationStatus 与 destinationService 四项均与原始事件逐字段相等。
     *
     * <p><b>Validates: Requirements 14.2</b></p>
     */
    @Property(tries = 100)
    void customEventSerializationRoundTrip(
            @ForAll("eventTexts") String businessType,
            @ForAll("eventTexts") String operationType,
            @ForAll("eventTexts") String operationStatus,
            @ForAll("destinationServices") String destinationService) {

        RemoteEvent<String> original = destinationService != null
                ? new RemoteEvent<>(businessType, operationType, operationStatus, destinationService)
                : new RemoteEvent<>(businessType, operationType, operationStatus);

        String json = JsonUtil.serialize(original);
        RemoteEvent<String> restored = JsonUtil.deserialize(json, RemoteEvent.class, String.class);

        assertThat(restored).isNotNull();
        assertThat(restored.getBusinessType()).isEqualTo(original.getBusinessType());
        assertThat(restored.getOperationType()).isEqualTo(original.getOperationType());
        assertThat(restored.getOperationStatus()).isEqualTo(original.getOperationStatus());
        assertThat(restored.getDestinationService()).isEqualTo(original.getDestinationService());
    }

    /**
     * 非法 JSON 边界测试：反序列化非合法 JSON 时应抛出异常，而不返回 null 或字段不完整的对象。
     *
     * <p>对应需求 14.3。</p>
     */
    @Test
    void deserializeInvalidJsonThrowsException() {
        assertThatThrownBy(() -> JsonUtil.deserialize("not-valid-json", RemoteEvent.class))
                .isInstanceOf(Exception.class);
    }
}
