package com.uoquo.demo.controller;

import com.uoquo.annotation.web.IgnoreAuth;
import com.uoquo.demo.model.pojo.ScnOrder;
import com.uoquo.demo.model.pojo.ScnOuter;
import com.uoquo.demo.listener.ScenarioRecorder;
import com.uoquo.utils.CurrentUser;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.web.ReturnData;
import com.uoquo.web.events.AppEvent;
import com.uoquo.web.events.EventEnricher;
import com.uoquo.web.events.EventExecutor;
import com.uoquo.web.events.EventSupport;
import com.uoquo.web.events.UoquoEventPublisher;
import com.uoquo.web.events.deserializer.DataTypeResolver;
import com.uoquo.web.events.deserializer.EventPackageScanner;
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
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 事件机制改造的场景测试（单体应用）。
 *
 * <p>每个接口都是一个独立场景：发布事件 → 等待监听器 → 返回“检查项（checks）+ 监听器观察到的现象（observations）”。
 * 检查项 {@code pass=true} 表示符合预期；{@code allPass} 是整体结论。所有接口都是 GET，浏览器直接访问即可。
 * 从 {@code /demo/scn} 开始，会列出全部场景。</p>
 *
 * <p>说明：场景通过 {@code CurrentUser} 模拟了一个登录用户（参数 user，默认 demo-user），
 * 用来验证“发布时自动放入发起人信息”以及“监听器中能取到发起人信息”。</p>
 */
@Tag(name = "event-scenario")
@RestController
@RequestMapping("/demo/scn")
@IgnoreAuth(all = true)
public class ScenarioController {

    private static final AtomicLong RUN_SEQ = new AtomicLong();

    @Autowired
    private UoquoEventPublisher eventPublisher;

    @Autowired
    private ScenarioRecorder recorder;

    @Autowired
    private DataTypeResolver dataTypeResolver;

    @Autowired
    private EventPackageScanner eventPackageScanner;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /* ============================ 索引 ============================ */

    @GetMapping({"", "/"})
    @Operation(summary = "场景列表", operationId = "scnIndex")
    public ReturnData<Map<String, Object>> index() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("说明", "每个场景返回 checks（检查项，pass=true 表示符合预期）与 observations（监听器观察到的现象）。基础路径：/api/app/demo/scn");
        List<Map<String, String>> list = new ArrayList<>();
        list.add(item("/basic", "基础：发布后自动补充发起人信息；监听器异步执行且取得发起人上下文"));
        list.add(item("/tx?mode=event&outcome=commit", "事务：publishEvent，提交后才投递（事务期间不投递）"));
        list.add(item("/tx?mode=event&outcome=rollback", "事务：publishEvent，回滚后仍投递（并输出 WARN 日志）"));
        list.add(item("/tx?mode=afterCommit&outcome=commit", "事务：publishAfterCommit，提交后投递"));
        list.add(item("/tx?mode=afterCommit&outcome=rollback", "事务：publishAfterCommit，回滚则丢弃"));
        list.add(item("/tx/none", "没有事务：立即投递（不再有固定 200ms 延迟）"));
        list.add(item("/generic?case=normal", "泛型过滤：只有声明匹配数据类型的监听器被调用"));
        list.add(item("/generic?case=degrade", "泛型过滤：数据降级为 Map 时，声明了具体泛型的监听器被跳过"));
        list.add(item("/generic?case=nested", "泛型过滤：嵌套类（Outer$Inner）数据能匹配到监听器"));
        list.add(item("/isolation", "多监听器：一个很慢、一个抛异常，不影响其他监听器"));
        list.add(item("/mask", "日志脱敏：事件里的 token 在日志文本中被脱敏"));
        list.add(item("/pool/saturate?count=30&sleepMs=400", "线程池饱和：不丢事件，由调用方线程兜底执行"));
        list.add(item("/pool/stats", "事件线程池当前状态"));
        list.add(item("/replay", "回放：跨包名还原数据类型 + retry=true 重发，事件ID重新生成"));
        list.add(item("/resolve", "数据类型解析：各种 dataType 写法（嵌套类、泛型、未知类型）的解析结果"));
        list.add(item("/resolve?dataType=other.pkg.ScnOrder", "数据类型解析：解析指定的 dataType"));
        list.add(item("/scanner", "事件类扫描器：扫描包范围与启动预热状态"));
        list.add(item("/validate", "参数校验：业务类型/操作类型为空时的行为"));
        list.add(item("/result?run={runId}", "按运行编号查看监听器记录（用于查看慢监听器的最终结果）"));
        result.put("scenarios", list);
        return new ReturnData<>(result);
    }

    /* ============================ 基础 ============================ */

    @GetMapping("/basic")
    @Operation(summary = "基础场景", operationId = "scnBasic")
    public ReturnData<Map<String, Object>> basic(@RequestParam(defaultValue = "demo-user") String user) {
        String runId = newRunId();
        login(user, runId);
        try {
            AppEvent<ScnOrder> event = newEvent("SCN", "BASIC", "CREATE", runId);
            event.setNewData(new ScnOrder("o-1", "订单-1", new BigDecimal("12.10")));
            String requestThread = Thread.currentThread().getName();

            long begin = System.currentTimeMillis();
            eventPublisher.publishEvent(event);
            long publishCost = System.currentTimeMillis() - begin;

            List<Map<String, Object>> obs = recorder.await(runId, 2, 5000);
            Map<String, Object> order = first(obs, "order");

            List<Map<String, Object>> checks = new ArrayList<>();
            checks.add(check("any、order 两个监听器各被调用 1 次",
                    ScenarioRecorder.count(obs, "any") == 1 && ScenarioRecorder.count(obs, "order") == 1, "observations=" + obs.size()));
            checks.add(check("发布时自动补充了发起人信息（事件ID、操作人、操作时间、请求ID、token）",
                    event.getEventId() != null && user.equals(event.getOperatorId()) && event.getOperationTime() != null
                            && event.getTraceId() != null && event.getToken() != null,
                    "eventId=" + event.getEventId() + ", operatorId=" + event.getOperatorId() + ", traceId=" + event.getTraceId()));
            checks.add(check("监听器不在请求线程执行（异步）",
                    order != null && !requestThread.equals(order.get("thread")),
                    "requestThread=" + requestThread + ", listenerThread=" + (order == null ? null : order.get("thread"))));
            checks.add(check("监听器中的 CurrentUser 来自事件（操作人、token、请求ID）",
                    order != null && user.equals(order.get("currentUser.operatorId"))
                            && ScenarioRecorder.maskToken(event.getToken()).equals(order.get("currentUser.token"))
                            && event.getTraceId().equals(order.get("currentUser.traceId")),
                    "listener: operatorId=" + (order == null ? null : order.get("currentUser.operatorId"))
                            + ", traceId=" + (order == null ? null : order.get("currentUser.traceId"))));
            checks.add(check("监听器的 MDC requestId = 请求ID-业务ID",
                    order != null && (event.getTraceId() + "-" + event.getBusinessId()).equals(order.get("mdc.requestId")),
                    "mdc.requestId=" + (order == null ? null : order.get("mdc.requestId"))));
            checks.add(check("发布线程自己的 CurrentUser 未被监听器破坏",
                    ("DEMO-TOKEN-" + user + "-0123456789").equals(CurrentUser.getToken()), "token still set in request thread"));

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("requestThread", requestThread);
            info.put("publishCostMs", publishCost);
            info.put("eventAfterPublish", describe(event));
            return new ReturnData<>(report("basic", runId, checks, obs, info));
        } finally {
            CurrentUser.clear();
        }
    }

    /* ============================ 事务 ============================ */

    /**
     * 在事务中发布事件。
     * @param mode    event（publishEvent：事务结束后投递，提交或回滚都投递）/ afterCommit（publishAfterCommit：仅提交成功后投递）
     * @param outcome commit / rollback
     * @param holdMs  事务内停留的时间（毫秒），用来观察“事务期间不投递”
     */
    @GetMapping("/tx")
    @Operation(summary = "事务场景", operationId = "scnTx")
    public ReturnData<Map<String, Object>> tx(@RequestParam(defaultValue = "event") String mode,
                                              @RequestParam(defaultValue = "commit") String outcome,
                                              @RequestParam(defaultValue = "1000") long holdMs,
                                              @RequestParam(defaultValue = "demo-user") String user) {
        String runId = newRunId();
        login(user, runId);
        try {
            boolean afterCommit = "afterCommit".equalsIgnoreCase(mode);
            boolean rollback = "rollback".equalsIgnoreCase(outcome);
            AppEvent<ScnOrder> event = newEvent("SCN", "TX", "CREATE", runId);
            event.setNewData(new ScnOrder("o-tx", "事务订单", new BigDecimal("1")));

            long[] times = new long[3]; // 0: 发布时刻  1: 事务体结束时刻  2: 事务体结束前已投递的数量
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.executeWithoutResult(status -> {
                times[0] = System.currentTimeMillis();
                if (afterCommit) {
                    eventPublisher.publishAfterCommit(event);
                } else {
                    eventPublisher.publishEvent(event);
                }
                sleep(holdMs);
                times[2] = recorder.get(runId).size();
                times[1] = System.currentTimeMillis();
                if (rollback) {
                    status.setRollbackOnly();
                }
            });

            boolean expectDelivered = !(afterCommit && rollback);
            List<Map<String, Object>> obs = recorder.await(runId, expectDelivered ? 2 : 0, expectDelivered ? 5000 : 1500);

            List<Map<String, Object>> checks = new ArrayList<>();
            checks.add(check("事务期间（停留 " + holdMs + "ms）没有投递", times[2] == 0, "delivered during tx=" + times[2]));
            if (expectDelivered) {
                long firstAt = obs.stream().mapToLong(o -> (Long) o.get("atMs")).min().orElse(0);
                checks.add(check("事务结束后投递（监听器收到事件的时间不早于事务体结束）",
                        !obs.isEmpty() && firstAt >= times[1], "txBodyEndAt=" + times[1] + ", firstDeliveredAt=" + firstAt
                                + ", delay=" + (firstAt - times[1]) + "ms"));
                checks.add(check("any、order 监听器各被调用 1 次",
                        ScenarioRecorder.count(obs, "any") == 1 && ScenarioRecorder.count(obs, "order") == 1, "observations=" + obs.size()));
                if (rollback) {
                    checks.add(check("回滚后 publishEvent 仍然投递（需关注日志中的 WARN：事务未提交时发布）", true,
                            "控制台应有：the event is published in a transaction which is not committed"));
                }
            } else {
                checks.add(check("回滚后 publishAfterCommit 被丢弃（监听器没有收到事件）", obs.isEmpty(), "observations=" + obs.size()
                        + "；控制台应有：the transaction is not committed, event is discarded"));
            }
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("mode", afterCommit ? "publishAfterCommit" : "publishEvent");
            info.put("outcome", rollback ? "rollback" : "commit");
            info.put("publishedAtMs", times[0]);
            info.put("txBodyEndAtMs", times[1]);
            return new ReturnData<>(report("tx", runId, checks, obs, info));
        } finally {
            CurrentUser.clear();
        }
    }

    @GetMapping("/tx/none")
    @Operation(summary = "没有事务", operationId = "scnTxNone")
    public ReturnData<Map<String, Object>> txNone(@RequestParam(defaultValue = "demo-user") String user) {
        String runId = newRunId();
        login(user, runId);
        try {
            AppEvent<ScnOrder> event = newEvent("SCN", "TX_NONE", "CREATE", runId);
            event.setNewData(new ScnOrder("o-none", "无事务订单", new BigDecimal("1")));

            long publishedAt = System.currentTimeMillis();
            eventPublisher.publishEvent(event);
            List<Map<String, Object>> obs = recorder.await(runId, 2, 5000);
            long firstAt = obs.stream().mapToLong(o -> (Long) o.get("atMs")).min().orElse(0);

            List<Map<String, Object>> checks = new ArrayList<>();
            checks.add(check("any、order 监听器各被调用 1 次",
                    ScenarioRecorder.count(obs, "any") == 1 && ScenarioRecorder.count(obs, "order") == 1, "observations=" + obs.size()));
            checks.add(check("没有事务时立即投递（延迟明显小于原来固定的 200ms）", !obs.isEmpty() && firstAt - publishedAt < 150,
                    "latency=" + (firstAt - publishedAt) + "ms（首次调用包含类加载等，可多访问几次观察稳定值）"));
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("latencyMs", firstAt - publishedAt);
            return new ReturnData<>(report("tx-none", runId, checks, obs, info));
        } finally {
            CurrentUser.clear();
        }
    }

    /* ============================ 泛型过滤 ============================ */

    /**
     * @param caseName normal（数据是 ScnOrder）/ degrade（数据降级为 Map）/ nested（数据是嵌套类 ScnOuter.Inner）/ product（数据是 ScnProduct）
     */
    @GetMapping("/generic")
    @Operation(summary = "泛型过滤", operationId = "scnGeneric")
    public ReturnData<Map<String, Object>> generic(@RequestParam(name = "case", defaultValue = "normal") String caseName) {
        String runId = newRunId();
        login("demo-user", runId);
        try {
            AppEvent<Object> event = newEvent("SCN", "GENERIC", "CREATE", runId);
            // 每个 case 对应的预期：哪些监听器应当被调用
            long expectAny = 1, expectOrder = 0, expectProduct = 0, expectInner = 0;
            String desc;
            switch (caseName) {
                case "degrade" -> {
                    // 模拟接收方无法还原数据类型：dataType 写的是 ScnOrder，但数据已降级为 Map
                    event.setDataType(ScnOrder.class.getName());
                    Map<String, Object> degraded = new LinkedHashMap<>();
                    degraded.put("id", "o-degraded");
                    event.setNewData(degraded);
                    desc = "dataType=ScnOrder 但数据是 Map：order 监听器应被跳过（避免 ClassCastException），any 仍然收到";
                }
                case "nested" -> {
                    ScnOuter.Inner inner = new ScnOuter.Inner();
                    inner.setValue("nested");
                    event.setNewData(inner);
                    expectInner = 1;
                    desc = "数据是嵌套类 ScnOuter$Inner：inner 监听器应被调用";
                }
                case "product" -> {
                    com.uoquo.demo.model.pojo.ScnProduct product = new com.uoquo.demo.model.pojo.ScnProduct();
                    product.setId("p-1");
                    event.setNewData(product);
                    expectProduct = 1;
                    desc = "数据是 ScnProduct：product 监听器被调用，order 不被调用";
                }
                default -> {
                    event.setNewData(new ScnOrder("o-1", "订单", new BigDecimal("1")));
                    expectOrder = 1;
                    desc = "数据是 ScnOrder：order 被调用，product、inner 不被调用";
                }
            }
            eventPublisher.publishEvent(event);
            int expectTotal = (int) (expectAny + expectOrder + expectProduct + expectInner);
            List<Map<String, Object>> obs = recorder.await(runId, expectTotal, 5000);
            // 再等一会儿，确认不该被调用的监听器确实没有被调用
            sleep(300);
            obs = recorder.get(runId);

            List<Map<String, Object>> checks = new ArrayList<>();
            checks.add(check("any 被调用 " + expectAny + " 次", ScenarioRecorder.count(obs, "any") == expectAny,
                    "actual=" + ScenarioRecorder.count(obs, "any")));
            checks.add(check("order 被调用 " + expectOrder + " 次", ScenarioRecorder.count(obs, "order") == expectOrder,
                    "actual=" + ScenarioRecorder.count(obs, "order")));
            checks.add(check("product 被调用 " + expectProduct + " 次", ScenarioRecorder.count(obs, "product") == expectProduct,
                    "actual=" + ScenarioRecorder.count(obs, "product")));
            checks.add(check("inner 被调用 " + expectInner + " 次", ScenarioRecorder.count(obs, "inner") == expectInner,
                    "actual=" + ScenarioRecorder.count(obs, "inner")));
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("case", caseName);
            info.put("description", desc);
            info.put("eventDataType", event.getDataType());
            return new ReturnData<>(report("generic", runId, checks, obs, info));
        } finally {
            CurrentUser.clear();
        }
    }

    /* ============================ 多监听器 ============================ */

    @GetMapping("/isolation")
    @Operation(summary = "多监听器互不影响", operationId = "scnIsolation")
    public ReturnData<Map<String, Object>> isolation() {
        String runId = newRunId();
        login("demo-user", runId);
        try {
            AppEvent<Object> event = newEvent("SCN_ISO", "ISOLATION", "CREATE", runId);
            event.setNewData("payload");
            long publishedAt = System.currentTimeMillis();
            eventPublisher.publishEvent(event);

            // 600ms 后：快监听器、会抛异常的监听器都应已执行；慢监听器（休眠 2s）还在执行
            sleep(600);
            List<Map<String, Object>> early = recorder.get(runId);
            // 慢监听器最终也会执行完成
            List<Map<String, Object>> all = recorder.await(runId, 3, 6000);
            long slowAt = all.stream().filter(o -> "iso-slow".equals(o.get("listener"))).mapToLong(o -> (Long) o.get("atMs")).findFirst().orElse(0);

            List<Map<String, Object>> checks = new ArrayList<>();
            checks.add(check("600ms 时，快监听器已执行（没有被慢监听器阻塞）", ScenarioRecorder.count(early, "iso-fast") == 1,
                    "early=" + early.size()));
            checks.add(check("600ms 时，抛异常的监听器已执行，且没有影响其他监听器", ScenarioRecorder.count(early, "iso-fail") == 1,
                    "控制台应有 1 条 ERROR：process event error（来自 iso-fail），仅此一条"));
            checks.add(check("600ms 时，慢监听器尚未完成", ScenarioRecorder.count(early, "iso-slow") == 0, "slow still running"));
            checks.add(check("慢监听器最终执行完成（约 2s 后）", slowAt > 0 && slowAt - publishedAt >= 1900,
                    "slowFinishedAfter=" + (slowAt == 0 ? "n/a" : (slowAt - publishedAt) + "ms")));
            return new ReturnData<>(report("isolation", runId, checks, all, new LinkedHashMap<>()));
        } finally {
            CurrentUser.clear();
        }
    }

    /* ============================ 日志脱敏 ============================ */

    @GetMapping("/mask")
    @Operation(summary = "日志脱敏", operationId = "scnMask")
    public ReturnData<Map<String, Object>> mask() {
        String runId = newRunId();
        login("demo-user", runId);
        try {
            AppEvent<ScnOrder> event = newEvent("SCN", "MASK", "CREATE", runId);
            event.setNewData(new ScnOrder("o-mask", "脱敏", new BigDecimal("1")));
            eventPublisher.publishEvent(event);
            List<Map<String, Object>> obs = recorder.await(runId, 2, 5000);

            String rawToken = event.getToken();
            String logText = EventSupport.toLogString(event);

            List<Map<String, Object>> checks = new ArrayList<>();
            checks.add(check("日志文本里不含完整 token", rawToken != null && !logText.contains(rawToken), "logText 中 token 已脱敏"));
            checks.add(check("日志文本里完整保留 token 的前 20 位，其余隐藏", logText.contains(ScenarioRecorder.maskToken(rawToken)),
                    "masked=" + ScenarioRecorder.maskToken(rawToken)));
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("logText", logText);
            info.put("hint", "把 com.uoquo.web.events 的日志级别调到 DEBUG，可在控制台看到 'publish event:'、'received event:' 等日志，其中 token 均为脱敏后的值");
            return new ReturnData<>(report("mask", runId, checks, obs, info));
        } finally {
            CurrentUser.clear();
        }
    }

    /* ============================ 线程池 ============================ */

    @GetMapping("/pool/stats")
    @Operation(summary = "事件线程池状态", operationId = "scnPoolStats")
    public ReturnData<Map<String, Object>> poolStats() {
        return new ReturnData<>(poolInfo());
    }

    /**
     * 线程池饱和：连续发布 count 个事件，每个事件的监听器休眠 sleepMs。
     * demo-app 把事件线程池配置得很小（见 application.yml 的 app.event.pool），因此很容易打满。
     */
    @GetMapping("/pool/saturate")
    @Operation(summary = "线程池饱和", operationId = "scnPoolSaturate")
    public ReturnData<Map<String, Object>> poolSaturate(@RequestParam(defaultValue = "30") int count,
                                                        @RequestParam(defaultValue = "400") long sleepMs) {
        int total = Math.min(Math.max(count, 1), 500);
        String runId = newRunId();
        login("demo-user", runId);
        try {
            Map<String, Object> before = poolInfo();
            String requestThread = Thread.currentThread().getName();
            long begin = System.currentTimeMillis();
            for (int i = 0; i < total; i++) {
                AppEvent<Object> event = newEvent("SCN_POOL", "POOL", "CREATE", runId);
                event.setNewData("payload-" + i);
                event.addExtension("idx", i);
                event.addExtension("sleepMs", sleepMs);
                eventPublisher.publishEvent(event);
            }
            long publishCost = System.currentTimeMillis() - begin;
            List<Map<String, Object>> obs = recorder.await(runId, total, 60_000);

            long onEventPool = obs.stream().filter(o -> String.valueOf(o.get("thread")).startsWith("event-pool-")).count();
            long onCaller = obs.size() - onEventPool;
            long distinct = obs.stream().map(o -> o.get("idx")).distinct().count();

            List<Map<String, Object>> checks = new ArrayList<>();
            checks.add(check("没有丢事件：监听器收到 " + total + " 个，且各不相同", obs.size() == total && distinct == total,
                    "received=" + obs.size() + ", distinct=" + distinct));
            checks.add(check("线程池饱和时由调用方线程兜底执行（onCallerThread > 0 说明触发了兜底；count 较小时可能为 0）", onCaller > 0,
                    "onEventPool=" + onEventPool + ", onCallerThread=" + onCaller + "；控制台应有 WARN：event executor is saturated"));
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("requestThread", requestThread);
            info.put("publishCostMs", publishCost);
            info.put("publishCostNote", "发布耗时明显大于 0，说明触发了反压（调用方线程在执行任务）");
            info.put("poolBefore", before);
            info.put("poolAfter", poolInfo());
            // 事件较多，不逐条返回
            Map<String, Object> report = report("pool-saturate", runId, checks, new ArrayList<>(), info);
            report.put("observationCount", obs.size());
            return new ReturnData<>(report);
        } finally {
            CurrentUser.clear();
        }
    }

    /* ============================ 回放 / 解析 ============================ */

    /**
     * 回放：模拟从事件记录表取出历史事件的 JSON，数据类型的包名与本应用不同（跨服务），
     * 用 DataTypeResolver 按简单类名还原数据类型，置 retry=true 后重新发布。
     */
    @GetMapping("/replay")
    @Operation(summary = "事件回放", operationId = "scnReplay")
    public ReturnData<Map<String, Object>> replay() {
        String runId = newRunId();
        login("demo-user", runId);
        try {
            // 1. 模拟“当初发布过”的事件：补充发起人信息后序列化保存
            AppEvent<ScnOrder> original = newEvent("SCN", "REPLAY", "CREATE", runId);
            original.setNewData(new ScnOrder("o-replay", "回放订单", new BigDecimal("99.90")));
            EventEnricher.enrich(original);
            String oldId = original.getEventId();
            String stored = JsonUtil.serialize(original);
            // 2. 模拟发送方与接收方的数据类包名不同
            String foreignJson = stored.replace(ScnOrder.class.getName(), "other.service.model.ScnOrder");

            // 3. 回放：解析 dataType → 反序列化 → retry=true → 重新发布
            Map<String, Object> map = JsonUtil.deserialize(foreignJson);
            String dataType = String.valueOf(map.get("dataType"));
            Class<?> resolved = dataTypeResolver.resolve(dataType, "AppEvent");
            AppEvent<?> restored = JsonUtil.deserialize(foreignJson, AppEvent.class, resolved);
            restored.setRetry(true);
            eventPublisher.publishEvent(restored);
            List<Map<String, Object>> obs = recorder.await(runId, 2, 5000);
            Map<String, Object> order = first(obs, "order");

            List<Map<String, Object>> checks = new ArrayList<>();
            checks.add(check("跨包名的 dataType 按简单类名还原为本应用的类", ScnOrder.class.equals(resolved), "dataType=" + dataType + " → " + resolved.getName()));
            checks.add(check("还原后数据是 ScnOrder，order 监听器被调用", order != null && ScnOrder.class.getName().equals(order.get("dataClass")),
                    "dataClass=" + (order == null ? null : order.get("dataClass"))));
            checks.add(check("监听器收到 retry=true", order != null && Boolean.TRUE.equals(order.get("retry")), "retry=" + (order == null ? null : order.get("retry"))));
            checks.add(check("回放时事件ID重新生成", order != null && order.get("eventId") != null && !oldId.equals(order.get("eventId")),
                    "oldId=" + oldId + ", newId=" + (order == null ? null : order.get("eventId"))));
            checks.add(check("原发起人信息保留（不会被当前请求覆盖）", order != null && "demo-user".equals(order.get("event.operatorId")),
                    "event.operatorId=" + (order == null ? null : order.get("event.operatorId"))));
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("storedJson", EventSupport.maskToken(stored));
            return new ReturnData<>(report("replay", runId, checks, obs, info));
        } finally {
            CurrentUser.clear();
        }
    }

    @GetMapping("/resolve")
    @Operation(summary = "数据类型解析", operationId = "scnResolve")
    public ReturnData<Map<String, Object>> resolve(@RequestParam(required = false) String dataType) {
        List<String> samples = new ArrayList<>();
        if (dataType != null) {
            samples.add(dataType);
        } else {
            samples.add(ScnOrder.class.getName());
            samples.add("other.service.model.ScnOrder");
            samples.add(ScnOuter.Inner.class.getName());
            samples.add("other.service.model.ScnOuter$Inner");
            samples.add("java.util.List<com.x.Foo>");
            samples.add("String");
            samples.add("unknown.pkg.NoSuchType");
            samples.add("");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (String sample : samples) {
            Class<?> resolved = dataTypeResolver.resolve(sample, "AppEvent");
            result.put(sample.isEmpty() ? "(空)" : sample, resolved.getName());
        }
        result.put("说明", "找不到类型时降级为 java.util.Map（控制台会有 WARN）；嵌套类按内部类简单名回退；带泛型参数的只取原始类型");
        return new ReturnData<>(result);
    }

    @GetMapping("/scanner")
    @Operation(summary = "事件类扫描器", operationId = "scnScanner")
    public ReturnData<Map<String, Object>> scanner() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanner", eventPackageScanner.toString());
        result.put("warmedUp", eventPackageScanner.isWarmedUp());
        result.put("indexedClasses", eventPackageScanner.warmUp());
        result.put("ScnOrder 可被扫描到", eventPackageScanner.findBySimpleName("ScnOrder").size() == 1);
        result.put("说明", "warmedUp=true 说明启动完成后已预热索引（启动日志中有 'EventPackageScanner warm up' 一行）；"
                + "可用环境变量 EVENT_SCAN_WARMUP=false 关闭预热后重启对比");
        return new ReturnData<>(result);
    }

    @GetMapping("/validate")
    @Operation(summary = "参数校验", operationId = "scnValidate")
    public ReturnData<Map<String, Object>> validate() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("业务类型为空时构造事件", attempt(() -> new AppEvent<String>("", "OP", "OK")));
        result.put("操作类型为 null 时构造事件", attempt(() -> new AppEvent<String>("BIZ", null, "OK")));
        result.put("构造后把业务类型改为空再发布", attempt(() -> {
            AppEvent<String> event = new AppEvent<>("BIZ", "OP", "OK");
            event.setBusinessType(null);
            eventPublisher.publishEvent(event);
        }));
        result.put("发布 null", attempt(() -> eventPublisher.publishEvent((Object) null)));
        result.put("说明", "前三项应抛出 IllegalArgumentException（构造时即失败，调用方能直接定位）；发布 null 只会记录 WARN，不抛异常");
        return new ReturnData<>(result);
    }

    @RequestMapping("/result")
    @Operation(summary = "按运行编号查看监听器记录", operationId = "scnResult")
    public ReturnData<Map<String, Object>> result(@RequestParam("run") String run) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", run);
        result.put("observations", recorder.get(run));
        return new ReturnData<>(result);
    }

    /* ============================ 工具方法 ============================ */

    private static String newRunId() {
        return "R" + (System.currentTimeMillis() % 100_000_000L) + "-" + RUN_SEQ.incrementAndGet();
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

    private <T> AppEvent<T> newEvent(String businessType, String subType, String operation, String runId) {
        AppEvent<T> event = new AppEvent<>(businessType, operation, "SUCCESS");
        event.setBusinessSubType(subType);
        event.setBusinessId("biz-" + runId);
        event.addExtension("runId", runId);
        return event;
    }

    private static Map<String, Object> first(List<Map<String, Object>> observations, String listener) {
        return observations.stream().filter(o -> listener.equals(o.get("listener"))).findFirst().orElse(null);
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
        item.put("url", "/demo/scn" + url);
        item.put("desc", desc);
        return item;
    }

    private static Map<String, Object> report(String scenario, String runId, List<Map<String, Object>> checks,
                                              List<Map<String, Object>> observations, Map<String, Object> info) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("scenario", scenario);
        report.put("runId", runId);
        report.put("allPass", checks.stream().allMatch(c -> Boolean.TRUE.equals(c.get("pass"))));
        report.put("checks", checks);
        report.put("info", info);
        report.put("observations", observations);
        return report;
    }

    private static Map<String, Object> describe(AppEvent<?> event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("eventId", event.getEventId());
        map.put("traceId", event.getTraceId());
        map.put("operatorId", event.getOperatorId());
        map.put("operatorName", event.getOperatorName());
        map.put("operationTime", event.getOperationTime());
        map.put("appKey", event.getAppKey());
        map.put("token(masked)", ScenarioRecorder.maskToken(event.getToken()));
        return map;
    }

    private static Map<String, Object> poolInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        if (EventExecutor.executor() instanceof ThreadPoolExecutor pool) {
            info.put("corePoolSize", pool.getCorePoolSize());
            info.put("maximumPoolSize", pool.getMaximumPoolSize());
            info.put("poolSize", pool.getPoolSize());
            info.put("active", pool.getActiveCount());
            info.put("queued", pool.getQueue().size());
            info.put("queueRemaining", pool.getQueue().remainingCapacity());
            info.put("completed", pool.getCompletedTaskCount());
            info.put("largestPoolSize", pool.getLargestPoolSize());
        }
        return info;
    }

    private static String attempt(Runnable runnable) {
        try {
            runnable.run();
            return "未抛异常";
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
