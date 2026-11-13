# 简介
应用示例

| 模块 | 说明 | 端口 / 上下文路径 |
|---|---|---|
| `demo-app` | 单体应用（依赖 `app-core`） | `7001` `/api/app` |
| `demo-service-user` | 微服务：事件发布方（依赖 `cloud-core`） | `7005` `/api/user` |
| `demo-service-book` | 微服务：事件接收方（依赖 `cloud-core`） | `7006` `/api/book` |

# 事件机制场景测试

用于在真实环境里验证事件机制改造（见 `uoquo-core/docs/event-refactor-plan.md`）。每个场景是一个 **GET 链接**，浏览器直接打开即可。
返回的 JSON 里：

- `checks`：检查项，`pass=true` 表示符合预期，`detail` 给出实际值；
- `allPass`：整体结论（所有检查项都通过才为 `true`）；
- `observations`（微服务为 `selfObservations` / `bookObservations`）：监听器被调用时“看到的现象”——所在线程、`CurrentUser`、MDC、事件ID等，出现问题时用来排查。

## 0. 运行前准备

1. **先安装新版核心包**：demo 依赖本地 Maven 仓库里的 `app-core` / `cloud-core`（1.1.0），需要先把改造后的核心代码装进去。
   ```
   cd uoquo-core
   mvn install -DskipTests -pl app-core,cloud-core -am
   ```
   > 注意：这会覆盖本地仓库中已有的 1.1.0 版本。
2. **依赖的外部环境**（连接信息见各模块的 `application.yml` / `bootstrap.yml`，可用环境变量覆盖）：

   | 模块 | 需要 |
   |---|---|
   | `demo-app` | MySQL、Redis（事务场景需要真实的数据库连接） |
   | `demo-service-user` / `demo-service-book` | MySQL、Redis、**Nacos**（Feign 通过服务名找到 book）、**Kafka**（Spring Cloud Bus） |
3. **启动**：在 IDE 中运行各模块的 `*Application` 主类，或 `mvn spring-boot:run -f <模块>/pom.xml`。
   微服务场景需要 `demo-service-user` 与 `demo-service-book` **同时启动**。
4. 建议的日志观察：把 `com.uoquo.web.events`、`com.uoquo.cloud.events` 的日志级别调到 `DEBUG`（`logback-custom.xml`），可看到事件的发布、投递、过滤过程（`publish event` 输出事件完整内容，每个事件一次；`skip event`、`process event begin` 只输出一行摘要；要看每个监听器的 `received event` 等更细的日志请调到 `TRACE`）；事件中的 token 在日志里都是脱敏后的值。

## 1. 单体应用 demo-app

入口：`http://localhost:7001/api/app/demo/scn`（列出全部场景）

| 链接 | 验证什么 | 预期 |
|---|---|---|
| `/basic?user=alice` | 发布时自动放入发起人信息；监听器异步执行；监听器里能取到发起人上下文 | `allPass=true`。事件被补充了事件ID、操作人、操作时间、请求ID、token；监听器线程是 `event-pool-*`，其中 `CurrentUser`、MDC `requestId`(=请求ID-业务ID) 来自事件；请求线程自己的 `CurrentUser` 不受影响 |
| `/tx?mode=event&outcome=commit` | `publishEvent`：事务提交后才投递 | 事务停留期间（默认 1000ms）监听器没有收到事件；事务体结束后才收到 |
| `/tx?mode=event&outcome=rollback` | `publishEvent`：回滚后仍然投递（如“登录失败”这类先发布再抛异常的事件） | 事务结束后监听器仍收到；控制台有 WARN：`the event is published in a transaction which is not committed` |
| `/tx?mode=afterCommit&outcome=commit` | `publishAfterCommit`：提交成功后投递 | 与第一行一致 |
| `/tx?mode=afterCommit&outcome=rollback` | `publishAfterCommit`：回滚则丢弃 | 监听器没有收到事件；控制台有 INFO：`the transaction is not committed, event is discarded` |
| `/tx/none` | 没有事务时立即投递（不再有固定 200ms 延迟） | `latencyMs` 明显小于 200（首次访问含类加载，多访问几次看稳定值） |
| `/generic?case=normal` | 声明了具体泛型的监听器只收到匹配的数据 | `order`、`any` 被调用；`product`、`inner` 不被调用 |
| `/generic?case=degrade` | 数据降级为 Map 时，声明了具体泛型的监听器被跳过 | 只有 `any` 被调用；控制台有 WARN：`event data type mismatch, skip listener`（原来这里会在监听方法内抛 `ClassCastException`） |
| `/generic?case=nested` | 嵌套类（`ScnOuter$Inner`）的数据能匹配到监听器 | `inner`、`any` 被调用 |
| `/generic?case=product` | 另一种类型的数据 | `product`、`any` 被调用；`order` 不被调用 |
| `/isolation` | 多个监听器互不影响 | 快监听器 600ms 内已执行；抛异常的监听器只产生 1 条 ERROR 日志（`process event error`）；慢监听器（休眠 2s）约 2s 后才完成，没有阻塞其他监听器 |
| `/mask` | 日志中 token 脱敏 | `logText` 中 token 完整显示前 20 位，其余隐藏（如 `DEMO-TOKEN-demo-user***`） |
| `/pool/saturate?count=30&sleepMs=400` | 事件线程池饱和：不丢事件，由调用方线程兜底 | 监听器恰好收到 30 个且各不相同；`onCallerThread>0`；`publishCostMs` 明显偏大（反压）；控制台有 WARN：`event executor is saturated` |
| `/pool/stats` | 事件线程池当前状态 | 核心/最大线程数、活跃数、队列长度（demo 里配置得很小：2/4/10） |
| `/replay` | 回放：发送方与接收方数据类包名不同时的还原 + 重发 | `other.service.model.ScnOrder` 被还原成本应用的 `ScnOrder`；监听器收到 `retry=true`；事件ID重新生成；原操作人保留 |
| `/resolve` | 各种 `dataType` 写法的解析结果 | 嵌套类按内部类简单名回退；`java.util.List<com.x.Foo>` 取原始类型 `List`；未知类型降级为 `Map`（有 WARN） |
| `/resolve?dataType=xxx` | 解析你指定的 `dataType` | — |
| `/scanner` | 事件类扫描器与启动预热 | `warmedUp=true`；启动日志有 `EventPackageScanner warm up: N classes indexed in M ms`。设置环境变量 `EVENT_SCAN_WARMUP=false` 重启后，`/scanner` 之前 `warmedUp=false` |
| `/validate` | 必填项校验 | 业务类型/操作类型为空时，**构造事件**即抛 `IllegalArgumentException`；发布 `null` 只记 WARN |
| `/result?run={runId}` | 按运行编号查看监听器记录 | 用于查看慢监听器的最终结果 |

可调整的配置（`demo-app/src/main/resources/application.yml` 的 `app.event`，也可用环境变量）：

| 配置 | 环境变量 | demo 取值 | 框架默认值 |
|---|---|---|---|
| `app.event.pool.core-size` | `EVENT_POOL_CORE` | 2 | 4 |
| `app.event.pool.max-size` | `EVENT_POOL_MAX` | 4 | 16 |
| `app.event.pool.queue-capacity` | `EVENT_POOL_QUEUE` | 10 | 1000 |
| `app.event.scanner.warm-up` | `EVENT_SCAN_WARMUP` | true | true |

> 调小线程池只是为了容易复现“饱和”。想看默认值下的表现，把三个 `EVENT_POOL_*` 改回框架默认值再访问 `/pool/saturate`。

## 2. 微服务 demo-service-user → demo-service-book

入口：`http://localhost:7005/api/user/demo/scn/remote`（列出全部场景）。
场景在 user 发布事件，user 自己等待本服务的监听器，再通过 Feign 向 book 查询它的监听器观察到的现象，汇总到一个返回里（`selfObservations` 是 user 的，`bookObservations` 是 book 的）。

| 链接 | 验证什么 | 预期 |
|---|---|---|
| `/info` | 本实例信息 | 服务名、端口、节点、busId |
| `/broadcast` | 广播：发送方自处理且不回环 | user 的 `remote-any`、`order` 各 1 次（自己发的事件没有经 Kafka 回来再处理一次）；book 各 1 次；book 的数据类型是 book 自己的 `com.uoquo.book.model.pojo.ScnOrder` |
| `/directed` | 定向发给 book | book 各 1 次；**user 的监听器不处理**（发给别人的事件不在发送方本地处理） |
| `/directed?target=other-service` | 定向发给一个不存在的服务 | user、book 都不处理 |
| `/directed-self` | 定向发给本服务 | 只有 user 各处理 1 次，book 不处理 |
| `/context` | 发起人上下文跨服务传递 | book 监听器里的 `CurrentUser` 操作人、token(脱敏后)、请求ID 与 user 的请求一致；book 的 MDC `requestId` = 请求ID-业务ID |
| `/generic?case=sameName` | book 有同名不同包的 `UserInfo` | book 的 `userInfo` 监听器被调用，数据类是 `com.uoquo.book.model.pojo.UserInfo`（按简单类名还原） |
| `/generic?case=userOnly` | book 没有的数据类 | book 数据降级为 `Map`（book 控制台有 WARN：`dataType unresolvable`），只有 `remote-any` 被调用，`userInfo` 不被误调用 |
| `/burst?count=20` | 连发 | user、book 的 `order` 监听器各恰好 20 次、事件ID各不相同（不丢、不重复、不回环） |
| `/tx?mode=event&outcome=commit` | 事务提交后广播 | user、book 各收到 1 次；事务期间 user 没有收到 |
| `/tx?mode=event&outcome=rollback` | 回滚后 `publishEvent` 仍广播 | user、book 各收到 1 次；有 WARN 日志 |
| `/tx?mode=afterCommit&outcome=commit` | `publishAfterCommit` 提交后广播 | 同上 |
| `/tx?mode=afterCommit&outcome=rollback` | `publishAfterCommit` 回滚则不广播 | user、book 都没有收到 |
| `/result?run={runId}` | 查看 user 监听器的记录 | — |

反方向（book 发布，user 接收）：`http://localhost:7006/api/book/demo/scn/broadcast`
返回里 book 自己的监听器各处理 1 次；再打开返回中的 `userResultUrl`（`/api/user/demo/scn/remote/result?run=...`），应看到 user 的 `remote-any`、`order` 各 1 条，`origin` 为 `demo-service-book`。

### 多实例验证（同一服务启动两个实例）

事件总线的消费组是“每个服务一个组”（`${app.name}-bus`），同一服务的多个实例中只有一个会收到某条消息。

1. 再启动第二个 user 实例：`SERVICE_PORT=7015 SERVICE_NODE=2`（其余配置不变）。
2. 在实例 A（7005）上访问 `/broadcast`，得到 `runId`。
3. 分别访问两个实例的 `/api/user/demo/scn/remote/result?run={runId}`：
   - **发布方实例 A**：`remote-any`、`order` 各 1 条（本地分发）；
   - **同名的另一个实例 B**：没有记录（同名服务发出的事件，从总线收到后一律丢弃，不会重复处理）。
4. book 只处理 1 次。若 book 也起了两个实例，请**分别直接查询**两个 book 实例的 `/api/book/demo/scn/result?run={runId}`，两个实例的记录合计各监听器 1 条。
   （此时 user 的场景接口只会通过负载均衡查询到其中一个 book 实例，`bookObservations` 的检查项可能因此显示不全，属于预期。）

## 3. 排查提示

- `allPass=false` 时先看 `checks` 里 `pass=false` 的那几项的 `detail`，再对照 `observations`。
- 微服务场景的 `bookQueryError` 不为空：说明 user 通过 Feign 访问 book 失败（book 没启动、Nacos 里没有注册等），不是事件的问题。
- 微服务场景里 book 的记录一直是 0：检查两个服务的 `KAFKA_ENABLED`、`KAFKA_HOST`、`KAFKA_BUS_TOPIC` 是否一致，以及 book 是否订阅了同一个 topic。
- 已有的 `OperationLogListener` 会打印所有事件，所以控制台里除了场景监听器的日志，还会看到它们的输出，属于正常现象。
