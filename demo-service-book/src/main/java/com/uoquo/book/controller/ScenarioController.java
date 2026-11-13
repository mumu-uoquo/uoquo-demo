package com.uoquo.book.controller;

import com.uoquo.annotation.web.IgnoreAuth;
import com.uoquo.book.listener.ScenarioRecorder;
import com.uoquo.book.model.pojo.ScnOrder;
import com.uoquo.cloud.events.RemoteEvent;
import com.uoquo.utils.Config;
import com.uoquo.utils.CurrentUser;
import com.uoquo.web.ReturnData;
import com.uoquo.web.events.UoquoEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 事件机制改造的场景测试（微服务：book 一侧）。
 *
 * <ul>
 *   <li>{@code /demo/scn/result?run=}：返回 book 的监听器在某次运行中观察到的记录，由 demo-service-user 的场景接口通过 Feign 调用；</li>
 *   <li>{@code /demo/scn/broadcast}：反方向的场景——由 book 发布广播事件，book 自己处理 1 次，
 *       demo-service-user 通过它的 {@code /demo/scn/remote/result?run=} 查看接收情况。</li>
 * </ul>
 */
@Tag(name = "event-scenario-book")
@RestController
@RequestMapping("/demo/scn")
@IgnoreAuth(all = true)
public class ScenarioController {

    private static final AtomicLong RUN_SEQ = new AtomicLong();

    @Autowired
    private UoquoEventPublisher eventPublisher;

    @Autowired
    private ScenarioRecorder recorder;

    /** 供 demo-service-user 查询（Feign）：book 的监听器在某次运行中观察到的记录 */
    @RequestMapping("/result")
    @Operation(summary = "按运行编号查看监听器记录", operationId = "bookScnResult")
    public ReturnData<Map<String, Object>> result(@RequestParam("run") String run) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", run);
        result.put("observations", recorder.get(run));
        return new ReturnData<>(result);
    }

    @GetMapping("/info")
    @Operation(summary = "本实例信息", operationId = "bookScnInfo")
    public ReturnData<Map<String, Object>> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("service", Config.getString("spring.application.name"));
        info.put("port", Config.getString("app.port"));
        info.put("node", Config.getString("app.node"));
        return new ReturnData<>(info);
    }

    /**
     * 反方向：由 book 发布广播事件。
     * 预期：book 的监听器各处理 1 次（不回环）；demo-service-user 也各处理 1 次（用返回的 userResultUrl 查看）。
     */
    @GetMapping("/broadcast")
    @Operation(summary = "由 book 发布广播事件", operationId = "bookScnBroadcast")
    public ReturnData<Map<String, Object>> broadcast(@RequestParam(defaultValue = "book-user") String user) {
        String runId = "B" + (System.currentTimeMillis() % 100_000_000L) + "-" + RUN_SEQ.incrementAndGet();
        login(user, runId);
        try {
            RemoteEvent<ScnOrder> event = new RemoteEvent<>("SCN", "CREATE", "SUCCESS");
            event.setBusinessSubType("FROM_BOOK");
            event.setBusinessId("biz-" + runId);
            event.addExtension("runId", runId);
            event.setNewData(new ScnOrder("o-book", "book 发布的订单", new BigDecimal("5")));
            eventPublisher.publishEvent(event);

            // 等待本服务的监听器
            List<Map<String, Object>> self = awaitSelf(runId, 2, 10_000);
            List<Map<String, Object>> checks = new ArrayList<>();
            checks.add(check("book 的监听器各处理 1 次（自己发的事件没有经 Kafka 回环后再处理一次）",
                    ScenarioRecorder.count(self, "remote-any") == 1 && ScenarioRecorder.count(self, "order") == 1,
                    "self=" + self.size()));

            Map<String, Object> report = new LinkedHashMap<>();
            report.put("scenario", "book-broadcast");
            report.put("runId", runId);
            report.put("allPass", checks.stream().allMatch(c -> Boolean.TRUE.equals(c.get("pass"))));
            report.put("checks", checks);
            report.put("selfObservations", self);
            report.put("userResultUrl", "/api/user/demo/scn/remote/result?run=" + runId
                    + "  （预期 demo-service-user 的 remote-any、order 各 1 条，origin 为 demo-service-book）");
            return new ReturnData<>(report);
        } finally {
            CurrentUser.clear();
        }
    }

    private List<Map<String, Object>> awaitSelf(String runId, int expected, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (recorder.get(runId).size() >= expected) {
                sleep(500);
                break;
            }
            sleep(20);
        }
        return recorder.get(runId);
    }

    /** 用 CurrentUser 模拟一个已登录的用户 */
    private void login(String user, String runId) {
        CurrentUser.clear();
        CurrentUser.setToken("DEMO-TOKEN-" + user + "-0123456789");
        CurrentUser.setTraceId("trace-" + runId);
        CurrentUser.setAppkey("demo-app-key");
        CurrentUser.setDeviceId("demo-device");
        CurrentUser.setAppVersion("1.0.0");
        CurrentUser.setClientIp("127.0.0.1");
        CurrentUser.UserInfo info = new CurrentUser.UserInfo();
        info.setUserId(user);
        info.setUserName("name-of-" + user);
        info.setInstituteId("demo-institute");
        CurrentUser.setInfo(info);
    }

    private static Map<String, Object> check(String name, boolean pass, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("pass", pass);
        item.put("check", name);
        item.put("detail", detail);
        return item;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
