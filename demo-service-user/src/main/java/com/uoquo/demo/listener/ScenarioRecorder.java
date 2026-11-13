package com.uoquo.demo.listener;

import com.uoquo.utils.Config;
import com.uoquo.utils.CurrentUser;
import com.uoquo.web.events.EventSupport;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 场景测试用的记录器：监听器被调用时，把“当时看到的上下文”按运行编号（runId）记录下来，
 * 供控制器等待、汇总并返回。仅保存在内存中，重启后清空。
 */
@Component
public class ScenarioRecorder {

    /** 最多保留的运行编号数量，超过后清理最早的，避免内存增长 */
    private static final int MAX_RUNS = 200;

    private final Map<String, List<Map<String, Object>>> runs = new ConcurrentHashMap<>();

    private final List<String> order = new CopyOnWriteArrayList<>();

    /**
     * 记录一次监听器调用。必须在监听器所在线程中调用（会读取当前线程的 CurrentUser 与 MDC）。
     */
    public void record(String runId, String listener, Map<String, Object> extra) {
        if (runId == null) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("listener", listener);
        item.put("service", Config.getString("spring.application.name"));
        item.put("instance", Config.getString("app.port") + "/node" + Config.getString("app.node"));
        item.put("thread", Thread.currentThread().getName());
        item.put("atMs", System.currentTimeMillis());
        // 监听器线程里看到的用户上下文（由事件中的发起人信息填充）
        item.put("currentUser.operatorId", CurrentUser.getInfo().getUserId());
        item.put("currentUser.operatorName", CurrentUser.getInfo().getUserName());
        item.put("currentUser.token", maskToken(CurrentUser.getToken()));
        item.put("currentUser.traceId", CurrentUser.getTraceId());
        item.put("mdc.requestId", MDC.get("requestId"));
        if (extra != null) {
            item.putAll(extra);
        }
        if (!runs.containsKey(runId)) {
            order.add(runId);
            while (order.size() > MAX_RUNS) {
                runs.remove(order.remove(0));
            }
        }
        runs.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>()).add(item);
    }

    /** 获取某次运行到目前为止的记录 */
    public List<Map<String, Object>> get(String runId) {
        List<Map<String, Object>> list = runs.get(runId);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    /**
     * 等待某次运行的记录数量达到期望值（或超时），返回当前记录。
     * expected 为 0 时表示“期望没有记录”：等待 timeoutMs 后返回，看看有没有意外的记录。
     */
    public List<Map<String, Object>> await(String runId, int expected, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (expected > 0 && get(runId).size() >= expected) {
                // 再等一小会儿，看看有没有多余的（重复）记录
                sleep(300);
                return get(runId);
            }
            sleep(20);
        }
        return get(runId);
    }

    /** 统计某个监听器的记录数量 */
    public static long count(List<Map<String, Object>> observations, String listener) {
        return observations.stream().filter(item -> listener.equals(item.get("listener"))).count();
    }

    /** token 脱敏（与日志脱敏的规则一致：完整显示前 20 位，其余隐藏） */
    public static String maskToken(String token) {
        if (token == null || token.isEmpty()) {
            return token;
        }
        return EventSupport.maskToken("{\"token\":\"" + token + "\"}").replaceAll("^\\{\"token\":\"|\"}$", "");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
