// Feature: app-core-demo-controllers, Property 3: 认证模拟事件携带所模拟账号
/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.demo.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEvent;
import org.springframework.test.util.ReflectionTestUtils;

import com.uoquo.demo.event.AuthEvent;
import com.uoquo.demo.model.pojo.AuthInfo;
import com.uoquo.web.events.UoquoEventPublisher;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property 3：认证模拟事件携带所模拟账号。
 *
 * <p>For any account 字符串，登录成功模拟与登录失败模拟所发布的 {@link AuthEvent} 的
 * newData 都是一个 {@link AuthInfo}，其 account 字段等于输入的 account。</p>
 *
 * <p>Validates: Requirements 7.4, 8.3</p>
 */
class EventDemoControllerAuthAccountPropertyTest {

    /**
     * account 生成器：覆盖空串、ASCII 与非 ASCII（中文）字符。
     */
    @Provide
    Arbitrary<String> accounts() {
        Arbitrary<String> empty = Arbitraries.just("");
        Arbitrary<String> ascii = Arbitraries.strings()
                .ascii()
                .ofMinLength(1)
                .ofMaxLength(20);
        Arbitrary<String> nonAscii = Arbitraries.strings()
                .withCharRange('\u4e00', '\u9fa5')
                .ofMinLength(1)
                .ofMaxLength(10);
        return Arbitraries.oneOf(empty, ascii, nonAscii);
    }

    /**
     * Property 3：对任意 account，loginSuccess 与 loginFailure 发布的 AuthEvent
     * 其 newData(AuthInfo) 的 account 等于输入账号。
     *
     * <p>Validates: Requirements 7.4, 8.3</p>
     */
    @Property(tries = 100)
    void authSimulationEventCarriesSimulatedAccount(@ForAll("accounts") String account) {
        UoquoEventPublisher publisher = Mockito.mock(UoquoEventPublisher.class);
        EventDemoController controller = new EventDemoController();
        // 注入 mock publisher（私有 @Autowired 字段 eventPublisher）
        ReflectionTestUtils.setField(controller, "eventPublisher", publisher);

        // 需求 7.4：登录成功模拟
        controller.loginSuccess(account);
        // 需求 8.3：登录失败模拟
        controller.loginFailure(account);

        // 捕获两次发布的事件。
        // 注意：UoquoEventPublisher 重载了 publishEvent(ApplicationEvent) 与 publishEvent(Object)；
        // AuthEvent 继承自 ApplicationEvent，控制器调用静态解析到 publishEvent(ApplicationEvent)，
        // 因此须针对该重载捕获参数。
        ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        Mockito.verify(publisher, Mockito.times(2)).publishEvent(captor.capture());

        List<ApplicationEvent> published = captor.getAllValues();
        assertThat(published).hasSize(2);
        for (ApplicationEvent event : published) {
            assertThat(event).isInstanceOf(AuthEvent.class);
            AuthEvent authEvent = (AuthEvent) event;
            AuthInfo newData = authEvent.getNewData();
            assertThat(newData)
                    .as("AuthEvent.newData 应为携带账号的 AuthInfo")
                    .isInstanceOf(AuthInfo.class);
            assertThat(newData.getAccount())
                    .as("AuthInfo.account 应等于输入账号")
                    .isEqualTo(account);
        }
    }
}
