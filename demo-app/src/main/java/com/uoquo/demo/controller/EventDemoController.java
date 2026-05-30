/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.demo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uoquo.annotation.web.IgnoreAuth;
import com.uoquo.demo.model.pojo.AuthInfo;
import com.uoquo.user.model.pojo.UserInfo;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.web.ReturnData;
import com.uoquo.web.events.AppEvent;
import com.uoquo.web.events.UoquoEventPublisher;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;

/**
 * 事件演示控制器：集中处理认证事件发布（登录成功/失败）与事件序列化重发。
 *
 * <p>所有事件均通过框架发布器 {@link UoquoEventPublisher} 统一发布（需求 10.2），
 * 由 {@code com.uoquo.listener.OperationLogListener} 作为唯一处理器消费。</p>
 *
 * <p>全部接口标注 {@code @IgnoreAuth(all = true)}，便于演示时免认证访问（需求 12.2）。</p>
 */
@Tag(name = "event")
@RestController
@RequestMapping("/demo/event")
@IgnoreAuth(all = true)
public class EventDemoController {

    private static final Logger log = LoggerFactory.getLogger(EventDemoController.class);

    /**
     * 框架事件发布器（需求 10.2），以 {@code private UoquoEventPublisher eventPublisher;} 形式注入。
     */
    @Autowired
    private UoquoEventPublisher eventPublisher;

    /**
     * 模拟登录成功事件（需求 7）。
     *
     * <p>发布一个 {@link AuthEvent}（business=AUTH、operation=LOGIN、status=SUCCESS），
     * newData 为携带所模拟账号的 {@link AuthInfo}。发布失败时仍返回确认 String（需求 7.6）。</p>
     *
     * @param account 模拟登录的账号
     * @return 包装确认信息的 {@link ReturnData}
     */
    @RequestMapping("/login-success")
    @Operation(description = "模拟登录成功事件", operationId = "eventLoginSuccess")
    public ReturnData<String> loginSuccess(@RequestParam @NotNull String account) {
        try {
            // 需求 7.3：business=AUTH、operation=LOGIN、status=成功状态
            AppEvent<AuthInfo> event = new AppEvent<>("AUTH", "LOGIN", "SUCCESS");
            AuthInfo info = new AuthInfo();
            info.setAccount(account);
            // 需求 7.4：newData 为携带账号的 AuthInfo
            event.setNewData(info);
            // 需求 7.1：经框架发布器发布
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            // 需求 7.6：发布失败仍返回确认 String
            log.error("发布登录成功事件失败, account={}", account, e);
        }
        // 需求 7.5：返回确认 String
        return new ReturnData<>("login-success event published");
    }

    /**
     * 模拟登录失败事件（需求 8）。
     *
     * <p>发布一个 {@link AuthEvent}（business=AUTH、operation=LOGIN、status=FAIL），
     * newData 为携带所模拟账号的 {@link AuthInfo}。发布失败时仍返回确认 String 且不重试（需求 8.5）。</p>
     *
     * @param account 模拟登录的账号
     * @return 包装确认信息的 {@link ReturnData}
     */
    @RequestMapping("/login-failure")
    @Operation(description = "模拟登录失败事件", operationId = "eventLoginFailure")
    public ReturnData<String> loginFailure(@RequestParam String account) {
        try {
            // 需求 8.2：business=AUTH、operation=LOGIN、status=失败状态
            AppEvent<AuthInfo> event = new AppEvent<>("AUTH", "LOGIN", "FAIL");
            AuthInfo info = new AuthInfo();
            info.setAccount(account);
            // 需求 8.3：newData 为携带账号的 AuthInfo
            event.setNewData(info);
            // 需求 8.1：经框架发布器发布
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            // 需求 8.5：发布失败仍返回确认 String，且不重试该发布
            log.error("发布登录失败事件失败, account={}", account, e);
        }
        // 需求 8.4：返回确认 String
        return new ReturnData<>("login-failure event published");
    }

    /**
     * 事件序列化重发（需求 9）。
     *
     * <p>构造一个样例 {@link UserEvent}，经 {@link JsonUtil} 序列化为 String，再反序列化回
     * {@link UserEvent}，置 {@code retry=true} 后经框架发布器重新发布——以单体等价方式
     * 演示运维式手动重投递。</p>
     *
     * @return 包装确认信息的 {@link ReturnData}
     */
    @RequestMapping("/resend")
    @Operation(description = "事件序列化重发", operationId = "eventResend")
    public ReturnData<String> resend() {
        // 构造原始样例 UserEvent
        AppEvent<UserInfo> original = buildSampleUserEvent();
        // 需求 9.1：序列化为 String
        String json = JsonUtil.serialize(original);
        // 需求 9.2：反序列化回 UserEvent
        AppEvent<UserInfo> restored = JsonUtil.deserialize(json, AppEvent.class, UserInfo.class);
        // 需求 9.3：发布前置 retry=true
        restored.setRetry(true);
        // 需求 9.4：经框架发布器重新发布
        eventPublisher.publishEvent(restored);
        // 需求 9.6：返回确认 String
        return new ReturnData<>("resend event published");
    }

    /**
     * 构造一个样例 {@link UserEvent}，newData 为一个样例 {@link UserInfo}，用于重发演示。
     *
     * @return 样例 UserEvent
     */
    private AppEvent<UserInfo> buildSampleUserEvent() {
        AppEvent<UserInfo> event = new AppEvent<>("USER", "UPDATE", "SUCCESS");
        UserInfo info = new UserInfo();
        info.setId("sample-user-id");
        info.setUserName("sampleUser");
        info.setRealName("样例用户");
        info.setPhone("13800138000");
        info.setEmail("sample@uoquo.com");
        event.setNewData(info);
        return event;
    }
}
