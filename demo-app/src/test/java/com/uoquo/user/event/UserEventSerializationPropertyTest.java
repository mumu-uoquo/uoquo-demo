// Feature: app-core-demo-controllers, Property 4: User_Event 序列化往返
/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.user.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import com.uoquo.user.model.pojo.UserInfo;
import com.uoquo.utils.json.JsonUtil;

import com.uoquo.web.events.AppEvent;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property 4：User_Event 序列化往返。
 *
 * <p>For any {@link AppEvent}（携带任意 businessType、operationType、operationStatus 与任意
 * {@link UserInfo} newData），通过 {@link JsonUtil} 序列化为 String 再反序列化回
 * {@link AppEvent} 后，所得事件的 businessType、operationType、operationStatus 与 newData
 * 都与原始事件相等。</p>
 *
 * <p><b>Validates: Requirements 9.1, 9.2, 9.5</b></p>
 *
 * <p>说明：</p>
 * <ul>
 *   <li>系统被测对象是 {@link JsonUtil#serialize(Object)} 后再 {@link JsonUtil#deserialize(String, Class)}
 *       回 {@link AppEvent} 的往返（与 {@code EventDemoController.resend} 一致）。
 *       {@code UserEvent} 具体绑定 {@code AppEvent<UserInfo>}，故 Jackson 可将泛型 newData 重建为
 *       {@link UserInfo}，无需三参重载。</li>
 *   <li>{@code JsonUtil} 将 {@link Date} 序列化为 epoch 毫秒字符串；仅 13 位正毫秒值可无损往返，
 *       故 createTime 生成于该区间（约公元 2001-2096），并覆盖 null。</li>
 *   <li>{@link UserInfo} 部分 setter 会 {@code trim()}，原始对象与还原对象均经 setter，故
 *       trim 幂等，逐字段比较成立。</li>
 * </ul>
 */
class UserEventSerializationPropertyTest {

    /**
     * 事件字符串字段（businessType/operationType/operationStatus）生成器：
     * 覆盖空串、ASCII 与非 ASCII（中文）字符。
     */
    @Provide
    Arbitrary<String> eventTexts() {
        return Arbitraries.oneOf(
                Arbitraries.just(""),
                Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(20),
                Arbitraries.strings()
                        .withCharRange('\u4e00', '\u9fa5')
                        .ofMinLength(1).ofMaxLength(10));
    }

    /**
     * UserInfo 字符串字段生成器：覆盖 null、空串、ASCII（含特殊字符）与非 ASCII（中文）。
     */
    private Arbitrary<String> nullableTexts() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just(""),
                Arbitraries.strings().ascii().ofMaxLength(30),
                Arbitraries.strings()
                        .withChars("中文测试用户名密码邮箱日本語テスト한국어éàüñçß")
                        .ofMinLength(1).ofMaxLength(20));
    }

    /**
     * createTime 生成器：13 位正毫秒区间（可无损往返）或 null。
     *
     * <p>区间上界刻意取过去时间（约 2001-09-09 至 2023-11-14），原因是 {@code DateUtil} 反向
     * 解析 13 位时间戳时要求其 {@code <= System.currentTimeMillis() * 2}，否则会落入按长度匹配
     * 的分支并抛异常。取过去时间既保证 13 位、又确保无损往返，同时覆盖有意义的范围。</p>
     */
    private Arbitrary<Date> createTimes() {
        Arbitrary<Date> inRange = Arbitraries
                .longs()
                .between(1_000_000_000_000L, 1_700_000_000_000L)
                .map(Date::new);
        return Arbitraries.oneOf(Arbitraries.just((Date) null), inRange);
    }

    /**
     * 随机 UserInfo 生成器。
     */
    @Provide
    Arbitrary<UserInfo> userInfos() {
        return Combinators.combine(
                        nullableTexts(),  // id
                        nullableTexts(),  // userName
                        nullableTexts(),  // realName
                        nullableTexts(),  // phone
                        nullableTexts(),  // password
                        nullableTexts(),  // email
                        createTimes())    // createTime
                .as((id, userName, realName, phone, password, email, createTime) -> {
                    UserInfo info = new UserInfo();
                    info.setId(id);
                    info.setUserName(userName);
                    info.setRealName(realName);
                    info.setPhone(phone);
                    info.setPassword(password);
                    info.setEmail(email);
                    info.setCreateTime(createTime);
                    return info;
                });
    }

    /**
     * Property 4：对任意 UserEvent，序列化再反序列化后 businessType、operationType、
     * operationStatus 与 newData 四项均与原始事件相等。
     *
     * <p><b>Validates: Requirements 9.1, 9.2, 9.5</b></p>
     */
    @Property(tries = 100)
    void userEventSerializationRoundTrip(
            @ForAll("eventTexts") String businessType,
            @ForAll("eventTexts") String operationType,
            @ForAll("eventTexts") String operationStatus,
            @ForAll("userInfos") UserInfo newData) {

        AppEvent<UserInfo> original = new AppEvent<>(businessType, operationType, operationStatus);
        original.setNewData(newData);

        // 需求 9.1：序列化为 String
        String json = JsonUtil.serialize(original);
        // 需求 9.2：反序列化回 UserEvent
        AppEvent<UserInfo> restored = JsonUtil.deserialize(json, AppEvent.class);

        // 需求 9.5：四项相等
        assertThat(restored).isNotNull();
        assertThat(restored.getBusinessType()).isEqualTo(original.getBusinessType());
        assertThat(restored.getOperationType()).isEqualTo(original.getOperationType());
        assertThat(restored.getOperationStatus()).isEqualTo(original.getOperationStatus());
        assertUserInfoEquals(restored.getNewData(), original.getNewData());
    }

    /**
     * 逐字段比较两个 {@link UserInfo}（UserInfo 未实现 equals）。
     * createTime 以毫秒精度比较。
     */
    private void assertUserInfoEquals(UserInfo actual, UserInfo expected) {
        if (expected == null) {
            assertThat(actual).isNull();
            return;
        }
        assertThat(actual).isNotNull();
        assertThat(actual.getId()).isEqualTo(expected.getId());
        assertThat(actual.getUserName()).isEqualTo(expected.getUserName());
        assertThat(actual.getRealName()).isEqualTo(expected.getRealName());
        assertThat(actual.getPhone()).isEqualTo(expected.getPhone());
        assertThat(actual.getPassword()).isEqualTo(expected.getPassword());
        assertThat(actual.getEmail()).isEqualTo(expected.getEmail());

        Date expectedTime = expected.getCreateTime();
        Date actualTime = actual.getCreateTime();
        if (expectedTime == null) {
            assertThat(actualTime).isNull();
        } else {
            assertThat(actualTime).isNotNull();
            assertThat(actualTime.getTime()).isEqualTo(expectedTime.getTime());
        }
    }
}
