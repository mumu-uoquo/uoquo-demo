package com.uoquo.demo.listener;

import com.uoquo.cloud.events.RemoteEvent;
import com.uoquo.demo.model.pojo.ScnOrder;
import com.uoquo.user.model.pojo.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微服务事件场景测试用的监听器（demo-service-user 一侧）。
 *
 * <p>只处理业务类型为 {@code SCN} 的场景事件。无论事件是本服务发布后本地分发的，还是从总线收到的，都会记录
 * “监听器当时看到的上下文”，用来验证：自己发的事件在本服务只处理一次（不回环）、定向事件只给目标服务、
 * 监听器里能取到发起人信息等。</p>
 */
@Component
public class ScenarioListeners {
    private static final Logger log = LoggerFactory.getLogger(ScenarioListeners.class);

    private static final String SCN = "'SCN'.equals(#event.businessType)";

    @Autowired
    private ScenarioRecorder recorder;

    /** 通配符：接收所有场景事件 */
    @EventListener(condition = SCN)
    public void onAny(RemoteEvent<?> event) {
        record("remote-any", event);
    }

    /** 声明了本服务的 ScnOrder */
    @EventListener(condition = SCN)
    public void onOrder(RemoteEvent<ScnOrder> event) {
        record("order", event);
    }

    /** 声明了本服务的 UserInfo（book 服务有一个同名不同包的 UserInfo） */
    @EventListener(condition = SCN)
    public void onUserInfo(RemoteEvent<UserInfo> event) {
        record("userInfo", event);
    }

    private void record(String listener, RemoteEvent<?> event) {
        Object runId = event.getExtension().get("runId");
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("eventId", event.getEventId());
        info.put("origin", event.getOriginService());
        info.put("destination", event.getDestinationService());
        info.put("event.operatorId", event.getOperatorId());
        info.put("event.traceId", event.getTraceId());
        info.put("dataType", event.getDataType());
        Object data = event.getNewData() != null ? event.getNewData() : event.getOldData();
        info.put("dataClass", data == null ? null : data.getClass().getName());
        info.put("idx", event.getExtension().get("idx"));
        log.info("场景监听器[{}]收到远程事件: runId={}, eventId={}, from={}, to={}, thread={}", listener, runId, event.getEventId(),
                event.getOriginService(), event.getDestinationService(), Thread.currentThread().getName());
        recorder.record(runId == null ? null : String.valueOf(runId), listener, info);
    }
}
