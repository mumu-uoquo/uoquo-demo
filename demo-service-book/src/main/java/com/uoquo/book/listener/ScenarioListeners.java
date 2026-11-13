package com.uoquo.book.listener;

import com.uoquo.book.model.pojo.ScnOrder;
import com.uoquo.book.model.pojo.UserInfo;
import com.uoquo.cloud.events.RemoteEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微服务事件场景测试用的监听器（demo-service-book 一侧）。
 *
 * <p>只处理业务类型为 {@code SCN} 的场景事件，把“监听器当时看到的上下文”记录下来，
 * 由 demo-service-user 的场景接口通过 {@code /demo/scn/result} 取回并校验。</p>
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

    /** 声明了 book 自己的 ScnOrder（与 user 服务的 ScnOrder 同名不同包） */
    @EventListener(condition = SCN)
    public void onOrder(RemoteEvent<ScnOrder> event) {
        record("order", event);
    }

    /** 声明了 book 自己的 UserInfo（与 user 服务的 UserInfo 同名不同包） */
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
