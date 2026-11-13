package com.uoquo.demo.listener;

import com.uoquo.demo.controller.ScenarioController;
import com.uoquo.demo.model.pojo.ScnOrder;
import com.uoquo.demo.model.pojo.ScnOuter;
import com.uoquo.demo.model.pojo.ScnProduct;
import com.uoquo.web.events.AppEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 事件场景测试用的监听器。
 *
 * <p>只处理业务类型为 {@code SCN}、{@code SCN_ISO}、{@code SCN_POOL} 的场景事件（见 {@link ScenarioController}），
 * 每次被调用都会向 {@link ScenarioRecorder} 记录“监听器当时看到的上下文”，用来验证：</p>
 * <ul>
 *   <li>监听器在哪个线程执行（事件线程池 / 调用方线程）；</li>
 *   <li>监听器里的 {@code CurrentUser}、MDC 是否来自事件携带的发起人信息；</li>
 *   <li>声明了具体泛型的监听方法，是否只收到数据类型匹配的事件。</li>
 * </ul>
 */
@Component
public class ScenarioListeners {
    private static final Logger log = LoggerFactory.getLogger(ScenarioListeners.class);

    private static final String SCN = "'SCN'.equals(#event.businessType)";
    private static final String ISO = "'SCN_ISO'.equals(#event.businessType)";
    private static final String POOL = "'SCN_POOL'.equals(#event.businessType)";

    @Autowired
    private ScenarioRecorder recorder;

    /* ======================== 泛型过滤 ======================== */

    /** 通配符：接收所有场景事件 */
    @EventListener(condition = SCN)
    public void onAny(AppEvent<?> event) {
        record("any", event, null);
    }

    /** 声明了 {@code AppEvent<ScnOrder>}：只应收到数据是 ScnOrder 的事件 */
    @EventListener(condition = SCN)
    public void onOrder(AppEvent<ScnOrder> event) {
        record("order", event, null);
    }

    /** 声明了 {@code AppEvent<ScnProduct>}：本场景中不应被调用 */
    @EventListener(condition = SCN)
    public void onProduct(AppEvent<ScnProduct> event) {
        record("product", event, null);
    }

    /** 声明了嵌套类 {@code AppEvent<ScnOuter.Inner>} */
    @EventListener(condition = SCN)
    public void onInner(AppEvent<ScnOuter.Inner> event) {
        record("inner", event, null);
    }

    /* ======================== 多监听器互不影响 ======================== */

    @EventListener(condition = ISO)
    public void isolationFast(AppEvent<?> event) {
        record("iso-fast", event, null);
    }

    @EventListener(condition = ISO)
    public void isolationSlow(AppEvent<?> event) throws InterruptedException {
        Thread.sleep(2000);
        record("iso-slow", event, null);
    }

    @EventListener(condition = ISO)
    public void isolationFail(AppEvent<?> event) {
        record("iso-fail", event, null);
        throw new IllegalStateException("场景测试：监听器故意抛出的异常（预期：只出现一条 ERROR 日志，不影响其他监听器）");
    }

    /* ======================== 线程池饱和 ======================== */

    @EventListener(condition = POOL)
    public void pool(AppEvent<?> event) throws InterruptedException {
        record("pool", event, null);
        Object sleepMs = event.getExtension().get("sleepMs");
        Thread.sleep(sleepMs == null ? 0 : Long.parseLong(String.valueOf(sleepMs)));
    }

    private void record(String listener, AppEvent<?> event, Map<String, Object> extra) {
        Object runId = event.getExtension().get("runId");
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("eventId", event.getEventId());
        info.put("retry", event.isRetry());
        info.put("event.operatorId", event.getOperatorId());
        info.put("event.traceId", event.getTraceId());
        info.put("dataType", event.getDataType());
        Object data = event.getNewData() != null ? event.getNewData() : event.getOldData();
        info.put("dataClass", data == null ? null : data.getClass().getName());
        info.put("idx", event.getExtension().get("idx"));
        if (extra != null) {
            info.putAll(extra);
        }
        log.info("场景监听器[{}]收到事件: runId={}, eventId={}, thread={}", listener, runId, event.getEventId(), Thread.currentThread().getName());
        recorder.record(runId == null ? null : String.valueOf(runId), listener, info);
    }
}
