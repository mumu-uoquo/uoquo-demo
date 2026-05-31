/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.listener;

import com.uoquo.cloud.events.RemoteEvent;
import com.uoquo.user.model.pojo.UserInfo;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.web.events.AppEvent;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OperationLogListener {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @PostConstruct
    public void setProperties() {
        log.debug("OperationLogListener init ...");
    }

    /**
     * 兜底方法：接收所有 AppEvent 及其子类。
     * JsonUtil.serialize 调用包裹在 try-catch 中，异常时记录 ERROR 日志并正常返回，不向事件发布方抛出。
     */
    @EventListener
    public void onAppEvent(AppEvent<?> event) {
        try {
            String content = JsonUtil.serialize(event);
            log.info("统一【内部】事件处理：{}, {}, {}", event.getBusinessType(), event.getOperationType(), content);
        } catch (Exception e) {
            log.error("【内部】事件处理失败，bizType={}, opType={}", event.getBusinessType(), event.getOperationType(), e);
        }
    }

    /**
     * RemoteEvent 专用方法：接收所有 RemoteEvent 及其子类。
     * destinationService 为 null 或 "**" 时以 [BROADCAST] 记录。
     * 异常时记录 ERROR 日志，不向消息总线抛出。
     */
    @EventListener
    public void onRemoteEvent(RemoteEvent<?> event) {
        try {
            String dest = (event.getDestinationService() == null || "**".equals(event.getDestinationService()))
                    ? "[BROADCAST]" : event.getDestinationService();
            log.info("统一【远程】事件处理：from={}, to={}, bizType={}, opType={}",
                    event.getOriginService(), dest,
                    event.getBusinessType(), event.getOperationType());
        } catch (Exception e) {
            log.error("RemoteEvent 处理失败，bizType={}, opType={}", event.getBusinessType(), event.getOperationType(), e);
        }
    }

    @EventListener
    public void onRemoteUserInfoEvent(RemoteEvent<UserInfo> event) {
        try {
            String dest = (event.getDestinationService() == null || "**".equals(event.getDestinationService()))
                    ? "[BROADCAST]" : event.getDestinationService();
            log.info("【远程用户】事件处理：from={}, to={}, bizType={}, opType={}",
                    event.getOriginService(), dest,
                    event.getBusinessType(), event.getOperationType());
        } catch (Exception e) {
            log.error("【远程用户】事件处理失败，bizType={}, opType={}", event.getBusinessType(), event.getOperationType(), e);
        }
    }

    /**
     * 带 condition 的过滤方法：仅当 businessType 为 "AUTH" 时被调用。
     */
    @EventListener(condition = "'AUTH'.equals(#event.businessType)")
    public void onAuthEvent(AppEvent<?> event) {
        try {
            log.info("处理认证事件【{}】: {}", event.getOperationType(), JsonUtil.serialize(event));
        } catch (Exception e) {
            log.error("认证事件序列化失败，opType={}", event.getOperationType(), e);
        }
    }
}
