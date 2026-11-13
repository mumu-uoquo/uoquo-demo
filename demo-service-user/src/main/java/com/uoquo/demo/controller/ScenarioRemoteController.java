package com.uoquo.demo.controller;

import com.uoquo.annotation.web.IgnoreAuth;
import com.uoquo.cloud.events.RemoteEvent;
import com.uoquo.demo.listener.ScenarioRecorder;
import com.uoquo.demo.model.pojo.ScnOrder;
import com.uoquo.demo.model.pojo.ScnUserOnly;
import com.uoquo.demo.remote.BookRemoteService;
import com.uoquo.user.model.pojo.UserInfo;
import com.uoquo.utils.Config;
import com.uoquo.utils.CurrentUser;
import com.uoquo.web.ReturnData;
import com.uoquo.web.events.UoquoEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.util.stream.Collectors;

/**
 * 事件机制改造的场景测试（微服务：发布方 demo-service-user，接收方 demo-service-book）。
 *
 * <p>每个接口都是一个独立场景：在本服务发布 {@link RemoteEvent} → 等待本服务的监听器 →
 * 通过 Feign 向 demo-service-book 查询它的监听器观察到的现象 → 返回“检查项（checks）+ 双方的观察记录”。
 * 检查项 {@code pass=true} 表示符合预期；{@code allPass} 是整体结论。所有接口都是 GET，浏览器直接访问即可。
 * 从 {@code /demo/scn/remote} 开始，会列出全部场景。</p>
 *
 * <p>依赖：Kafka（Spring Cloud Bus）、Nacos（Feign 通过服务名找到 book）、demo-service-book 已启动。</p>
 */
@Tag(name = "event-scenario-remote")
@RestController
@RequestMapping("/demo/scn/remote")
@IgnoreAuth(all = true)
public class ScenarioRemoteController {

    private static final AtomicLong RUN_SEQ = new AtomicLong();

    private static final String BOOK = "demo-service-book";

    @Autowired
    private UoquoEventPublisher eventPublisher;

    @Autowired
    private ScenarioRecorder recorder;

    @Autowired
    private BookRemoteService bookRemoteService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** 一次场景的两侧观察结果 */
    private record Outcome(List<Map<String, Object>> self, List<Map<String, Object>> book, String bookError) {
    }

    /* ============================ 索引 / 信息 ============================ */

    @GetMapping({"", "/"})
    @Operation(summary = "场景列表", operationId = "scnRemoteIndex")
    public ReturnData<Map<String, Object>> index() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("说明", "在 demo-service-user 发布事件，demo-service-book 接收。基础路径：/api/user/demo/scn/remote。"
                + "每个场景返回 checks（检查项）与 selfObservations（本服务监听器）、bookObservations（book 服务监听器）");
        List<Map<String, String>> list = new ArrayList<>();
        list.add(item("/info", "本实例信息（多实例验证时用来区分实例）"));
        list.add(item("/broadcast", "广播：本服务处理 1 次（不回环）+ book 处理 1 次"));
        list.add(item("/directed", "定向发给 book：book 处理，本服务的监听器不处理"));
        list.add(item("/directed-self", "定向发给本服务：只在本服务处理，book 不处理"));
        list.add(item("/context", "发起人上下文：book 的监听器中能取到本次请求的操作人、token、请求ID"));
        list.add(item("/generic?case=sameName", "数据类型：book 有同名不同包的 UserInfo，按简单类名还原为 book 自己的类"));
        list.add(item("/generic?case=userOnly", "数据类型：book 没有的类，数据降级为 Map，声明了具体泛型的监听器不被误调用"));
        list.add(item("/burst?count=20", "连发：本服务与 book 各处理恰好 count 次（不丢、不重复、不回环）"));
        list.add(item("/tx?mode=event&outcome=commit", "事务：publishEvent，提交后广播"));
        list.add(item("/tx?mode=event&outcome=rollback", "事务：publishEvent，回滚后仍广播（有 WARN 日志）"));
        list.add(item("/tx?mode=afterCommit&outcome=commit", "事务：publishAfterCommit，提交后广播"));
        list.add(item("/tx?mode=afterCommit&outcome=rollback", "事务：publishAfterCommit，回滚则不广播（本服务与 book 都收不到）"));
        list.add(item("/result?run={runId}", "按运行编号查看本服务监听器的记录"));
        result.put("scenarios", list);
        return new ReturnData<>(result);
    }

    @GetMapping("/info")
    @Operation(summary = "本实例信息", operationId = "scnRemoteInfo")
    public ReturnData<Map<String, Object>> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("service", selfName());
        info.put("port", Config.getString("app.port"));
        info.put("node", Config.getString("app.node"));
        info.put("busId", selfName() + ":" + Config.getString("app.code") + Config.getString("app.node"));
        info.put("说明", "多实例验证：用不同的 SERVICE_PORT / SERVICE_NODE 再启动一个实例，在一个实例上发布广播事件后，"
                + "分别用两个实例的 /result?run=... 查看——发布方实例本地处理 1 次，同名的另一个实例不处理；book 只处理 1 次");
        return new ReturnData<>(info);
    }

    /* ============================ 广播 / 定向 ============================ */

    @GetMapping("/broadcast")
    @Operation(summary = "广播", operationId = "scnRemoteBroadcast")
    public ReturnData<Map<String, Object>> broadcast(@RequestParam(defaultValue = "demo-user") String user) {
        String runId = newRunId();
        login(user, runId);
        try {
            RemoteEvent<ScnOrder> event = newEvent("BROADCAST", runId, null);
            event.setNewData(new ScnOrder("o-1", "广播订单", new BigDecimal("12.10")));
            eventPublisher.publishEvent(event);

            Outcome outcome = collect(runId, 2, 2);
            List<Map<String, Object>> checks = new ArrayList<>();
            checks.add(check("本服务的监听器各处理 1 次（自己发的事件没有经 Kafka 回环后再处理一次）",
                    ScenarioRecorder.count(outcome.self(), "remote-any") == 1 && ScenarioRecorder.count(outcome.self(), "order") == 1,
                    "self=" + outcome.self().size()));
            checks.add(check("book 的监听器各处理 1 次", ScenarioRecorder.count(outcome.book(), "remote-any") == 1
                    && ScenarioRecorder.count(outcome.book(), "order") == 1, bookDetail(outcome)));
            checks.add(check("book 收到的事件来源是 " + selfName(), outcome.book().stream().allMatch(o -> selfName().equals(o.get("origin"))),
                    "origins=" + values(outcome.book(), "origin")));
            checks.add(check("book 把数据还原成了 book 自己的 ScnOrder（发送方与接收方包名不同）",
                    outcome.book().stream().filter(o -> "order".equals(o.get("listener")))
                            .allMatch(o -> "com.uoquo.book.model.pojo.ScnOrder".equals(o.get("dataClass"))),
                    "dataClass=" + values(outcome.book(), "dataClass")));
            return new ReturnData<>(report("broadcast", runId, checks, outcome, new LinkedHashMap<>()));
        } finally {
            CurrentUser.clear();
        }
    }

    @GetMapping("/directed")
    @Operation(summary = "定向发给其他服务", operationId = "scnRemoteDirected")
    public ReturnData<Map<String, Object>> directed(@RequestParam(defaultValue = BOOK) String target) {
        String runId = newRunId();
        login("demo-user", runId);
        try {
            RemoteEvent<ScnOrder> event = newEvent("DIRECTED", runId, target);
            event.setNewData(new ScnOrder("o-2", "定向订单", new BigDecimal("1")));
            eventPublisher.publishEvent(event);

            boolean toBook = BOOK.equals(target);
            Outcome outcome = collect(runId, 0, toBook ? 2 : 0);
            List<Map<String, Object>> checks = new ArrayList<>();
            checks.add(check("发给其他服务的事件，本服务的监听器不处理", outcome.self().isEmpty(),
                    "self=" + outcome.self().size() + "（若不为 0，说明发给别人的事件也在本地被处理了）"));
            if (toBook) {
                checks.add(check("book 的监听器各处理 1 次", ScenarioRecorder.count(outcome.book(), "remote-any") == 1
                        && ScenarioRecorder.count(outcome.book(), "order") == 1, bookDetail(outcome)));
                checks.add(check("book 收到的目标服务是 " + target, outcome.book().stream()
                                .allMatch(o -> String.valueOf(o.get("destination")).startsWith(target)),
                        "destinations=" + values(outcome.book(), "destination")));
            }
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("target", target);
            return new ReturnData<>(report("directed", runId, checks, outcome, info));
        } finally {
            CurrentUser.clear();
        }
    }

    @GetMapping("/directed-self")
    @Operation(summary = "定向发给本服务", operationId = "scnRemoteDirectedSelf")
    public ReturnData<Map<String, Object>> directedSelf() {
        String runId = newRunId();
        login("demo-user", runId);
        try {
            RemoteEvent<ScnOrder> event = newEvent("DIRECTED_SELF", runId, selfName());
            event.setNewData(new ScnOrder("o-3", "定向给自己", new BigDecimal("1")));
            eventPublisher.publishEvent(event);

            Outcome outcome = collect(runId, 2, 0);
            List<Map<String, Object>> checks = new ArrayList<>();
            checks.add(check("定向给本服务的事件，本服务的监听器各处理 1 次",
                    ScenarioRecorder.count(outcome.self(), "remote-any") == 1 && ScenarioRecorder.count(outcome.self(), "order") == 1,
                    "self=" + outcome.self().size()));
            checks.add(check("book 不处理", outcome.book().isEmpty(), "book=" + outcome.book().size()));
            return new ReturnData<>(report("directed-self", runId, checks, outcome, new LinkedHashMap<>()));
        } finally {
            CurrentUser.clear();
        }
    }

    /* ============================ 上下文 / 数据类型 ============================ */

    @GetMapping("/context")
    @Operation(summary = "发起人上下文", operationId = "scnRemoteContext")
    public ReturnData<Map<String, Object>> context(@RequestParam(defaultValue = "demo-user") String user) {
        String runId = newRunId();
        login(user, runId);
        try {
            RemoteEvent<ScnOrder> event = newEvent("CONTEXT", runId, null);
            event.setNewData(new ScnOrder("o-4", "上下文订单", new BigDecimal("1")));
            eventPublisher.publishEvent(event);

            Outcome outcome = collect(runId, 2, 2);
            Map<String, Object> bookOrder = first(outcome.book(), "order");
            String maskedToken = ScenarioRecorder.maskToken(event.getToken());
            List<Map<String, Object>> checks = new ArrayList<>();
            checks.add(check("book 的监听器中 CurrentUser 的操作人 = 发布方的当前用户",
                    bookOrder != null && user.equals(bookOrder.get("currentUser.operatorId")),
                    "operatorId=" + (bookOrder == null ? null : bookOrder.get("currentUser.operatorId"))));
            checks.add(check("book 的监听器中 CurrentUser 的请求ID = 发布方请求的请求ID",
                    bookOrder != null && event.getTraceId().equals(bookOrder.get("currentUser.traceId")),
                    "traceId=" + (bookOrder == null ? null : bookOrder.get("currentUser.traceId")) + ", expect=" + event.getTraceId()));
            checks.add(check("book 的监听器中 CurrentUser 的 token = 发布方的 token（这里只比较脱敏后的值）",
                    bookOrder != null && maskedToken.equals(bookOrder.get("currentUser.token")),
                    "token=" + (bookOrder == null ? null : bookOrder.get("currentUser.token"))));
            checks.add(check("book 的 MDC requestId = 请求ID-业务ID",
                    bookOrder != null && (event.getTraceId() + "-" + event.getBusinessId()).equals(bookOrder.get("mdc.requestId")),
                    "mdc.requestId=" + (bookOrder == null ? null : bookOrder.get("mdc.requestId"))));
            return new ReturnData<>(report("context", runId, checks, outcome, new LinkedHashMap<>()));
        } finally {
            CurrentUser.clear();
        }
    }

    /**
     * @param caseName sameName（book 有同名不同包的 UserInfo）/ userOnly（只有本服务有的类 ScnUserOnly）
     */
    @GetMapping("/generic")
    @Operation(summary = "数据类型还原", operationId = "scnRemoteGeneric")
    public ReturnData<Map<String, Object>> generic(@RequestParam(name = "case", defaultValue = "sameName") String caseName) {
        String runId = newRunId();
        login("demo-user", runId);
        try {
            boolean userOnly = "userOnly".equalsIgnoreCase(caseName);
            RemoteEvent<Object> event = newEvent("GENERIC", runId, null);
            if (userOnly) {
                ScnUserOnly data = new ScnUserOnly();
                data.setNote("only in user");
                event.setNewData(data);
            } else {
                UserInfo data = new UserInfo();
                data.setId("u-1");
                data.setUserName("同名不同包");
                event.setNewData(data);
            }
            eventPublisher.publishEvent(event);

            // 本服务：remote-any（+ userInfo）；book：userOnly 时只有 remote-any
            Outcome outcome = collect(runId, userOnly ? 1 : 2, userOnly ? 1 : 2);
            List<Map<String, Object>> checks = new ArrayList<>();
            if (userOnly) {
                checks.add(check("本服务：remote-any 处理 1 次，声明了具体泛型的监听器不被调用",
                        ScenarioRecorder.count(outcome.self(), "remote-any") == 1 && outcome.self().size() == 1, "self=" + outcome.self().size()));
                checks.add(check("book：无法还原 ScnUserOnly，数据降级为 Map，remote-any 仍收到",
                        ScenarioRecorder.count(outcome.book(), "remote-any") == 1
                                && outcome.book().stream().allMatch(o -> String.valueOf(o.get("dataClass")).contains("Map")),
                        "book dataClass=" + values(outcome.book(), "dataClass") + "；book 控制台应有 WARN：dataType unresolvable"));
                checks.add(check("book：声明了 RemoteEvent<UserInfo> 的监听器没有被误调用（避免 ClassCastException）",
                        ScenarioRecorder.count(outcome.book(), "userInfo") == 0, bookDetail(outcome)));
            } else {
                checks.add(check("本服务：remote-any 与 userInfo 各处理 1 次",
                        ScenarioRecorder.count(outcome.self(), "remote-any") == 1 && ScenarioRecorder.count(outcome.self(), "userInfo") == 1,
                        "self=" + outcome.self().size()));
                checks.add(check("book：remote-any 与 userInfo 各处理 1 次，数据是 book 自己的 UserInfo（简单类名还原）",
                        ScenarioRecorder.count(outcome.book(), "remote-any") == 1 && ScenarioRecorder.count(outcome.book(), "userInfo") == 1
                                && outcome.book().stream().allMatch(o -> "com.uoquo.book.model.pojo.UserInfo".equals(o.get("dataClass"))),
                        "book dataClass=" + values(outcome.book(), "dataClass")));
            }
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("case", userOnly ? "userOnly" : "sameName");
            info.put("publishedDataType", event.getDataType());
            return new ReturnData<>(report("generic", runId, checks, outcome, info));
        } finally {
            CurrentUser.clear();
        }
    }

    /* ============================ 连发 ============================ */

    @GetMapping("/burst")
    @Operation(summary = "连发", operationId = "scnRemoteBurst")
    public ReturnData<Map<String, Object>> burst(@RequestParam(defaultValue = "20") int count) {
        int total = Math.min(Math.max(count, 1), 200);
        String runId = newRunId();
        login("demo-user", runId);
        try {
            for (int i = 0; i < total; i++) {
                RemoteEvent<ScnOrder> event = newEvent("BURST", runId, null);
                event.setNewData(new ScnOrder("o-" + i, "连发-" + i, new BigDecimal(i)));
                event.addExtension("idx", i);
                eventPublisher.publishEvent(event);
            }
            Outcome outcome = collect(runId, total * 2, total * 2);
            List<Map<String, Object>> checks = new ArrayList<>();
            checks.add(check("本服务 order 监听器恰好处理 " + total + " 次（不丢、不重复、不回环）",
                    ScenarioRecorder.count(outcome.self(), "order") == total && distinct(outcome.self(), "order") == total,
                    "count=" + ScenarioRecorder.count(outcome.self(), "order") + ", distinct=" + distinct(outcome.self(), "order")));
            checks.add(check("book order 监听器恰好处理 " + total + " 次（不丢、不重复）",
                    ScenarioRecorder.count(outcome.book(), "order") == total && distinct(outcome.book(), "order") == total,
                    "count=" + ScenarioRecorder.count(outcome.book(), "order") + ", distinct=" + distinct(outcome.book(), "order")));
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("count", total);
            Map<String, Object> report = report("burst", runId, checks, new Outcome(new ArrayList<>(), new ArrayList<>(), outcome.bookError()), info);
            report.put("selfObservationCount", outcome.self().size());
            report.put("bookObservationCount", outcome.book().size());
            return new ReturnData<>(report);
        } finally {
            CurrentUser.clear();
        }
    }

    /* ============================ 事务 ============================ */

    @GetMapping("/tx")
    @Operation(summary = "事务场景", operationId = "scnRemoteTx")
    public ReturnData<Map<String, Object>> tx(@RequestParam(defaultValue = "event") String mode,
                                              @RequestParam(defaultValue = "commit") String outcome,
                                              @RequestParam(defaultValue = "800") long holdMs) {
        String runId = newRunId();
        login("demo-user", runId);
        try {
            boolean afterCommit = "afterCommit".equalsIgnoreCase(mode);
            boolean rollback = "rollback".equalsIgnoreCase(outcome);
            RemoteEvent<ScnOrder> event = newEvent("TX", runId, null);
            event.setNewData(new ScnOrder("o-tx", "事务订单", new BigDecimal("1")));

            int[] deliveredDuringTx = new int[1];
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                if (afterCommit) {
                    eventPublisher.publishAfterCommit(event);
                } else {
                    eventPublisher.publishEvent(event);
                }
                sleepQuietly(holdMs);
                deliveredDuringTx[0] = recorder.get(runId).size();
                if (rollback) {
                    status.setRollbackOnly();
                }
            });

            boolean expectDelivered = !(afterCommit && rollback);
            Outcome result = collect(runId, expectDelivered ? 2 : 0, expectDelivered ? 2 : 0);
            List<Map<String, Object>> checks = new ArrayList<>();
            checks.add(check("事务期间（停留 " + holdMs + "ms）本服务没有收到事件", deliveredDuringTx[0] == 0, "during tx=" + deliveredDuringTx[0]));
            if (expectDelivered) {
                checks.add(check("本服务的监听器各处理 1 次", ScenarioRecorder.count(result.self(), "order") == 1
                        && ScenarioRecorder.count(result.self(), "remote-any") == 1, "self=" + result.self().size()));
                checks.add(check("book 的监听器各处理 1 次", ScenarioRecorder.count(result.book(), "order") == 1
                        && ScenarioRecorder.count(result.book(), "remote-any") == 1, bookDetail(result)));
            } else {
                checks.add(check("回滚后 publishAfterCommit 被丢弃：本服务与 book 都没有收到",
                        result.self().isEmpty() && result.book().isEmpty(),
                        "self=" + result.self().size() + ", book=" + result.book().size()));
            }
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("mode", afterCommit ? "publishAfterCommit" : "publishEvent");
            info.put("outcome", rollback ? "rollback" : "commit");
            return new ReturnData<>(report("tx", runId, checks, result, info));
        } finally {
            CurrentUser.clear();
        }
    }

    @RequestMapping("/result")
    @Operation(summary = "按运行编号查看本服务监听器记录", operationId = "scnRemoteResult")
    public ReturnData<Map<String, Object>> result(@RequestParam("run") String run) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", run);
        result.put("observations", recorder.get(run));
        return new ReturnData<>(result);
    }

    /* ============================ 工具方法 ============================ */

    private static String selfName() {
        return Config.getString("spring.application.name");
    }

    private static String newRunId() {
        return "U" + (System.currentTimeMillis() % 100_000_000L) + "-" + RUN_SEQ.incrementAndGet();
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

    /**
     * @param destination 目标服务名；为空表示广播
     */
    private <T> RemoteEvent<T> newEvent(String subType, String runId, String destination) {
        RemoteEvent<T> event = (destination == null || destination.isBlank())
                ? new RemoteEvent<>("SCN", "CREATE", "SUCCESS")
                : new RemoteEvent<>("SCN", "CREATE", "SUCCESS", null, destination);
        event.setBusinessSubType(subType);
        event.setBusinessId("biz-" + runId);
        event.addExtension("runId", runId);
        return event;
    }

    /** 等待本服务与 book 的监听器记录（期望数量为 0 表示期望“没有记录”，会等待一段时间确认） */
    private Outcome collect(String runId, int expectSelf, int expectBook) {
        // 事件已经发布（发起人信息已放入事件），清掉模拟的登录用户，避免假的 token 通过 Feign 请求头传给 book
        CurrentUser.clear();
        List<Map<String, Object>> self = recorder.await(runId, expectSelf, expectSelf > 0 ? 10_000 : 2000);
        return fetchBook(runId, expectBook, self);
    }

    @SuppressWarnings("unchecked")
    private Outcome fetchBook(String runId, int expected, List<Map<String, Object>> self) {
        long deadline = System.currentTimeMillis() + (expected > 0 ? 12_000 : 3500);
        List<Map<String, Object>> last = new ArrayList<>();
        String error = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                Map<String, Object> data = bookRemoteService.scnResult(runId);
                Object list = data == null ? null : data.get("observations");
                last = list instanceof List ? (List<Map<String, Object>>) list : new ArrayList<>();
                error = null;
            } catch (Exception e) {
                error = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            if (expected > 0 && last.size() >= expected) {
                // 再等一会儿，确认没有多余的（重复）记录
                sleepQuietly(500);
                try {
                    Map<String, Object> data = bookRemoteService.scnResult(runId);
                    Object list = data == null ? null : data.get("observations");
                    last = list instanceof List ? (List<Map<String, Object>>) list : last;
                } catch (Exception ignore) {
                    // 保留上一次的结果
                }
                break;
            }
            sleepQuietly(300);
        }
        // 本服务的记录也再取一次，保证与 book 的时间点接近
        return new Outcome(recorder.get(runId).isEmpty() ? self : recorder.get(runId), last, error);
    }

    private static Map<String, Object> first(List<Map<String, Object>> observations, String listener) {
        return observations.stream().filter(o -> listener.equals(o.get("listener"))).findFirst().orElse(null);
    }

    private static List<Object> values(List<Map<String, Object>> observations, String key) {
        return observations.stream().map(o -> o.get(key)).collect(Collectors.toList());
    }

    private static long distinct(List<Map<String, Object>> observations, String listener) {
        return observations.stream().filter(o -> listener.equals(o.get("listener"))).map(o -> o.get("eventId")).distinct().count();
    }

    private static String bookDetail(Outcome outcome) {
        return "book=" + outcome.book().size() + (outcome.bookError() == null ? "" : "，查询 book 出错：" + outcome.bookError());
    }

    private static Map<String, Object> check(String name, boolean pass, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("pass", pass);
        item.put("check", name);
        item.put("detail", detail);
        return item;
    }

    private static Map<String, String> item(String url, String desc) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("url", "/demo/scn/remote" + url);
        item.put("desc", desc);
        return item;
    }

    private static Map<String, Object> report(String scenario, String runId, List<Map<String, Object>> checks, Outcome outcome,
                                              Map<String, Object> info) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("scenario", scenario);
        report.put("runId", runId);
        report.put("service", selfName());
        report.put("allPass", checks.stream().allMatch(c -> Boolean.TRUE.equals(c.get("pass"))));
        report.put("checks", checks);
        report.put("info", info);
        report.put("selfObservations", outcome.self());
        report.put("bookObservations", outcome.book());
        if (outcome.bookError() != null) {
            report.put("bookQueryError", outcome.bookError());
        }
        return report;
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
