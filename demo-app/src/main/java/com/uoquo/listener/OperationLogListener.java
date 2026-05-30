/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.listener;

import com.uoquo.utils.json.JsonUtil;
import com.uoquo.web.events.AppEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.uoquo.user.model.pojo.UserInfo;

import jakarta.annotation.PostConstruct;

@Component
public class OperationLogListener {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @PostConstruct
    public void setProperties(){
        log.debug("OperationLogListener init ...");
    }

    // EventListener 默认为同步，由于发布任务时已经启动了线程池，此处不需要再加Async标识
//    @Async
    @EventListener
    public void onMessageEvent2(AppEvent<UserInfo> event) {
        UserInfo info = event.getNewData() != null ? event.getNewData() : event.getOldData();
        log.info("处理用户【{}】事件: {}", event.getOperationType(), JsonUtil.serialize(info));
    }

    @EventListener(condition = "'AUTH'.equals(#event.businessType)")
    public void onAUTHEvent(AppEvent<?> event) {
        log.info("处理认证事件【{}】: {}", event.getOperationType(), JsonUtil.serialize(event));
    }

    @EventListener
    public void onAppEvent(AppEvent<?> event) {
        log.info("统一事件处理：{}, {}, {}", event.getBusinessType(), event.getOperationType(), JsonUtil.serialize(event));
    }
}
