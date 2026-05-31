/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.demo.controller;

import com.uoquo.annotation.web.IgnoreAuth;
import com.uoquo.cloud.events.RemoteEvent;
import com.uoquo.demo.model.pojo.AuthInfo;
import com.uoquo.user.model.pojo.UserInfo;
import com.uoquo.web.ReturnData;
import com.uoquo.web.events.AppEvent;
import com.uoquo.web.events.UoquoEventPublisher;
import com.uoquo.web.exception.SystemErrorException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
     * 定向发布事件到 demo-cloud-book。
     *
     * <p>构造 {@link RemoteEvent}（businessType="USER", operationType="UPDATE",
     * operationStatus="SUCCESS", destinationService="demo-cloud-book"），
     * 经 {@link UoquoEventPublisher#publishEvent} 发布。
     * 成功时返回含目标服务、businessType、operationType 的确认信息；
     * 异常时返回包装了错误说明的非成功 {@link ReturnData}，不向调用方抛出未处理异常。</p>
     *
     * @return 包装确认信息的 ReturnData，或包装错误说明的非成功 ReturnData
     */
    @GetMapping("/remote/directed")
    @Operation(summary = "定向发布事件到 demo-cloud-book", operationId = "eventDirected")
    public ReturnData<String> directed() {
        try {
            RemoteEvent<UserInfo> event = new RemoteEvent<>("USER", "UPDATE", "SUCCESS", "demo-cloud-book");
            UserInfo userInfo = new UserInfo();
            userInfo.setId("userId");
            userInfo.setUserName("定向信息");
            event.setNewData(userInfo);
            eventPublisher.publishEvent(event);
            return new ReturnData<>("定向事件已发布，目标服务: demo-cloud-book, bizType: USER, opType: UPDATE");
        } catch (Exception e) {
            log.error("定向事件发布失败", e);
            return new ReturnData<>(new SystemErrorException("事件发布失败: " + e.getMessage()));
        }
    }

    /**
     * 广播发布事件（投递给所有订阅服务）。
     *
     * <p>构造 {@link RemoteEvent}（businessType="AUTH", operationType="LOGIN",
     * operationStatus="SUCCESS", destinationService="**"），
     * 经 {@link UoquoEventPublisher#publishEvent} 发布。
     * 成功时返回确认信息；
     * 异常时返回包装了错误说明的非成功 {@link ReturnData}，不向调用方抛出未处理异常。</p>
     *
     * @return 包装确认信息的 ReturnData，或包装错误说明的非成功 ReturnData
     */
    @GetMapping("/remote/broadcast")
    @Operation(summary = "广播发布事件", operationId = "eventBroadcast")
    public ReturnData<String> broadcast() {
        try {
            RemoteEvent<UserInfo> event = new RemoteEvent<>("AUTH", "LOGIN", "SUCCESS");
            UserInfo userInfo = new UserInfo();
            userInfo.setId("userId");
            userInfo.setUserName("广播信息");
            event.setNewData(userInfo);
            eventPublisher.publishEvent(event);
            return new ReturnData<>("广播事件已发布，bizType: AUTH, opType: LOGIN，所有订阅服务均可接收");
        } catch (Exception e) {
            log.error("广播事件发布失败", e);
            return new ReturnData<>(new SystemErrorException("事件发布失败: " + e.getMessage()));
        }
    }

    /**
     * 模拟登录成功事件（需求 7）。
     *
     * <p>发布一个 {@link AppEvent}（business=AUTH、operation=LOGIN、status=SUCCESS），
     * newData 为携带所模拟账号的 {@link AuthInfo}。发布失败时仍返回确认 String（需求 7.6）。</p>
     *
     * @param account 模拟登录的账号
     * @return 包装确认信息的 {@link ReturnData}
     */
    @RequestMapping("/inner/login")
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
     * <p>发布一个 {@link AppEvent}（business=AUTH、operation=LOGIN、status=FAIL），
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
}
