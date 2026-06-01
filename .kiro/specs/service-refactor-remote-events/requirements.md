# Requirements Document

## Introduction

`demo-service-user` 与 `demo-service-book` 是基于 `com.uoquo:cloud-core` 的两个 Spring Cloud 微服务模块（使用 bootstrap.yml、Nacos 服务发现/配置中心、spring-cloud-bus-kafka 消息总线、Sentinel）。它们的代码组织、控制器写法、模型分层、监听器模式相对于参考模块 `demo-app`（基于 `com.uoquo:app-core` 的单体示例）已发生偏移：

- 模型类散落在 `user/bean`、`book/param` 等非约定包中，而 `demo-app` 统一使用 `<feature>/model/(dto|param|pojo)` 分层。
- 控制器使用泛化的 `@RequestMapping`，缺少 `@Tag`/`@Operation`、精确的 `@GetMapping`/`@PostMapping`，命名为 `TestController`/`TestStoreController` 等测试性名称，而 `demo-app` 使用职责清晰的演示控制器（`ResponseDemoController`、`RedisDemoController`、`EventDemoController`）。
- 两个服务都存在 `OperationLogListener`，但监听器方法零散、与 `demo-app` 的统一事件监听模式不一致。

本功能将参照 `demo-app` 重构这两个微服务，使其在包结构、控制器规范、模型分层、配置组织、监听器模式上对齐参考模块，同时保留并强化微服务特有的两项能力：

1. 保留 `demo-service-user` 远程调用 `demo-service-book` 的能力，并将其从混合用途的控制器中提取到一个**专用的远程调用示例控制器**中。
2. 在两个服务中对齐监听器模式，**新增 `RemoteEvent` 监听器**，并提供一个**定向（指向特定服务）发布事件**的可运行示例，与广播发布形成对照。

### 已核实的技术上下文

以下事实已从源码树、模块 POM 以及框架类型的引用方式中确认，并约束着后续设计：

- `demo-service-user` 的 `pom.xml` 依赖 `com.uoquo:cloud-core:1.0.6`；`demo-service-book` 的 `pom.xml` 依赖 `com.uoquo:cloud-core:1.1.0`。两个模块当前使用了不一致的 `cloud-core` 版本，目标统一版本为 `cloud-core:1.1.0`。
- `demo-service-book` 当前**无法编译**：其控制器引用了 `com.uoquo.web.common.ReturnData` 与 `com.uoquo.web.common.annotation.IgnoreAuth`，这些类型在 `cloud-core:1.1.0` 的类路径上无法解析（出现 “The import com.uoquo.web.common cannot be resolved”、“ReturnData cannot be resolved to a type”、“IgnoreAuth cannot be resolved to a type” 等错误）。`book/param/UserInfo` 还引用了无法解析的 `com.uoquo.web.mybatis.sensitive.SensitiveData`/`SensitiveField`。
- `demo-service-user` 当前可解析 `com.uoquo.web.common.ReturnData`、`com.uoquo.web.common.annotation.IgnoreAuth`、`com.uoquo.web.handler.RedisUtil`、`com.uoquo.web.exception.SystemErrorException`，对应 `cloud-core:1.0.6`；重构后将升级至 `cloud-core:1.1.0`，并确保所有框架类型引用在 1.1.0 类路径上可解析。
- 参考模块 `demo-app`（`app-core`）使用的是 `com.uoquo.web.ReturnData` 与 `com.uoquo.annotation.web.IgnoreAuth`（不含 `.common` 段）。`app-core` 与 `cloud-core` 的包命名不同，因此两个微服务在“写法/结构”上对齐 `demo-app`，但在“具体框架类型/包名”上必须使用各自 `cloud-core` 版本中真实存在、可解析的类型。
- 框架事件类型：`com.uoquo.web.events.AppEvent<T>` 为应用事件基类；`com.uoquo.cloud.events.RemoteEvent<T>` 为可跨服务（经消息总线）传播的远程事件类型，继承自应用事件体系（对 `AppEvent<?>` 的 `@EventListener` 可同时接收其 `RemoteEvent` 子类）。`RemoteEvent` 提供 `(businessType, operationType, operationStatus)` 与 `(businessType, operationType, operationStatus, destinationService)` 两种构造，以及 `getContent()`、`setNewData()`、`setOldData()`、`setDataType()`、`destinationService` 等成员。
- 框架事件发布器为 `com.uoquo.web.events.UoquoEventPublisher`，暴露 `publishEvent(Object)`。
- 定向发布机制依赖 `spring.cloud.bus.id`：两个服务的 `application.yml` 中 `spring.cloud.bus.id` 形如 `${spring.application.name}:${app.code}${app.node}`，配置注释明确 “必须以‘服务名’或‘服务名冒号’开头，否则定向消息将无法接收”。将 `RemoteEvent.destinationService` 置为某个服务名（如 `demo-cloud-book`）即可仅向该服务定向投递；置为 `**` 或不设置则广播给所有服务。
- 两个服务的应用名（`spring.application.name`）分别为 `demo-cloud-user`（端口 7005、上下文 `/api/user`）与 `demo-cloud-book`（端口 7006、上下文 `/api/book`）。
- `demo-service-user` 现有 `BookRemoteService` 为 Feign 客户端：`@FeignClient(name = "demo-cloud-book", contextId = "BookRemoteService", path = "/api/book")` 且 `@LoadBalancerClient(... configuration = IphashLoadBalancerConfiguration.class)`，调用 `demo-service-book` 的 `/store/book/name`。
- `demo-service-user` 现有 `TestStoreController` 同时承担 Redis 演示与远程调用（`/store/remote/book`）两种职责；`TestEventController` 同时承担多种事件发布演示。
- `demo-app` 的包结构基线为：`main/`（启动类）+ `main/config/`（应用/异步/数据源/OpenAPI 等配置）、`<feature>/controller`、`<feature>/service(+impl)`、`<feature>/mapper`、`<feature>/model/(dto|param|pojo)`、`demo/controller`（演示控制器）、`listener/OperationLogListener`（唯一统一监听器）。`demo-app` 不包含 `filter/`、`interceptor/` 包。

## Glossary

- **User_Service**：微服务模块 `demo-service-user`，根包 `com.uoquo`，应用名 `demo-cloud-user`。
- **Book_Service**：微服务模块 `demo-service-book`，根包 `com.uoquo`，应用名 `demo-cloud-book`。
- **Reference_App**：参考模块 `demo-app`，作为重构所遵循的结构与写法基线；本功能不修改 Reference_App。
- **User_Listener**：User_Service 中的监听器 `com.uoquo.listener.OperationLogListener`。
- **Book_Listener**：Book_Service 中的监听器 `com.uoquo.listener.OperationLogListener`。
- **Operation_Log_Listener**：两个服务中位于 `com.uoquo.listener` 包下的统一事件监听器的统称。
- **Remote_Call_Controller**：在 User_Service 中新提取的专用控制器，仅用于演示对 Book_Service 的远程调用。
- **Book_Remote_Service**：User_Service 中的 Feign 客户端 `com.uoquo.user.remote.BookRemoteService`，调用 Book_Service 的接口。
- **Book_Name_Endpoint**：Book_Service 中返回书名字符串的接口（当前为 `/store/book/name`）。
- **Event_Publisher**：框架事件发布器 `com.uoquo.web.events.UoquoEventPublisher`，提供 `publishEvent(Object)`。
- **App_Event**：框架应用事件基类 `com.uoquo.web.events.AppEvent<T>`。
- **Remote_Event**：框架远程事件类型 `com.uoquo.cloud.events.RemoteEvent<T>`，可经消息总线跨服务传播。
- **Custom_Event**：各服务中继承自 Remote_Event 的具体事件子类（User_Service 为 `com.uoquo.user.events.CustomEvent`，Book_Service 为 `com.uoquo.book.events.CustomEvent`）。
- **Destination_Service**：Remote_Event 上的目标服务标识字段；取某个服务名时表示定向投递，取 `**` 或未设置时表示广播。
- **Directed_Event**：Destination_Service 被设置为某个具体服务名（如 `demo-cloud-book`）的 Remote_Event，仅投递给该目标服务。
- **Broadcast_Event**：Destination_Service 为 `**` 或未设置的 Remote_Event，投递给所有订阅服务。
- **Return_Data**：各 `cloud-core` 版本中真实存在的框架响应包装类型（统一版本 `cloud-core:1.1.0` 下两个服务均使用该版本类路径上可解析的对应类型；重构前 User_Service 使用 `com.uoquo.web.common.ReturnData`，重构后须使用 1.1.0 中可解析的类型）。
- **Ignore_Auth**：各 `cloud-core` 版本中真实存在的免认证注解（统一版本 `cloud-core:1.1.0` 下两个服务均使用该版本类路径上可解析的对应类型；重构前 User_Service 使用 `com.uoquo.web.common.annotation.IgnoreAuth`，重构后须使用 1.1.0 中可解析的类型）。
- **Redis_Util**：User_Service 当前使用的框架 Redis 工具 `com.uoquo.web.handler.RedisUtil`。
- **Json_Util**：框架 JSON 工具 `com.uoquo.utils.json.JsonUtil`，提供 `serialize` 与 `deserialize`。
- **Model_Layout**：Reference_App 的模型分层约定，即每个功能包下使用 `model/dto`、`model/param`、`model/pojo` 三个子包。
- **Demo_Controller_Convention**：Reference_App 的控制器写法约定，即类标注 `@RestController` 与 `@Tag`，方法标注 `@Operation` 与精确映射注解（`@GetMapping`/`@PostMapping`），免认证接口标注 Ignore_Auth，返回值统一包装为 Return_Data。

## Requirements

### 需求 1：User_Service 包结构对齐 Reference_App

**用户故事：** 作为微服务示例的维护者，我希望 User_Service 的包结构与 Reference_App 一致，从而使两套示例在组织方式上保持统一、便于对照学习。

#### 验收标准

1. THE User_Service SHALL 将其唯一的应用启动类置于 `com.uoquo.main` 包，并将所有应用配置类置于 `com.uoquo.main.config` 包。
2. THE User_Service SHALL 将统一事件监听器置于 `com.uoquo.listener` 包，类名为 `OperationLogListener`。
3. THE User_Service SHALL 按 Model_Layout 组织用户模型类型：将表实体 `UserInfo` 置于 `com.uoquo.user.model.pojo` 包、将数据传输对象（DTO）置于 `com.uoquo.user.model.dto` 包（对齐 Reference_App 的 Model_Layout，`model.dto` 子包必须存在）、将请求参数类型置于 `com.uoquo.user.model.param` 包。
4. THE User_Service SHALL 将所有处理用户业务的控制器类置于 `com.uoquo.user.controller` 包。
5. THE User_Service SHALL 将 Book_Remote_Service 保留在 `com.uoquo.user.remote` 包。
6. THE User_Service SHALL 在重构完成后使其源码树不再包含 `com.uoquo.filter` 与 `com.uoquo.interceptor` 包，与 Reference_App 基线及 Book_Service 保持一致，其中需保留的类迁移至 Reference_App 基线中已存在的对应包、其余类移除。

### 需求 2：Book_Service 包结构对齐 Reference_App

**用户故事：** 作为微服务示例的维护者，我希望 Book_Service 的包结构与 Reference_App 一致，从而使其与 User_Service 及 Reference_App 保持统一。

#### 验收标准

1. THE Book_Service SHALL 将应用启动类置于 `com.uoquo.main` 包、将所有配置类置于 `com.uoquo.main.config` 包。
2. THE Book_Service SHALL 将统一事件监听器置于 `com.uoquo.listener` 包，且该监听器类名为 `OperationLogListener`。
3. THE Book_Service SHALL 按 Model_Layout 组织图书模型类型，将表实体 `UserInfo` 从 `com.uoquo.book.param` 迁移到 `com.uoquo.book.model.pojo` 包。
4. THE Book_Service SHALL 将请求参数类型 `LoginParam` 置于 `com.uoquo.book.model.param` 包。
5. THE Book_Service SHALL 将所有图书相关控制器置于 `com.uoquo.book.controller` 包。
6. WHERE Book_Service 当前存在 Reference_App 基线未包含的 `com.uoquo.filter` 与 `com.uoquo.interceptor` 包，THE Book_Service SHALL 在重构完成后使其源码树不再包含 `com.uoquo.filter` 与 `com.uoquo.interceptor` 包，其中需保留的类迁移至 Reference_App 基线中已存在的对应包、其余类移除。

### 需求 3：控制器写法对齐 Demo_Controller_Convention

**用户故事：** 作为微服务示例的使用者，我希望两个服务的控制器写法与 Reference_App 一致，从而获得清晰的接口文档与一致的响应包装。

#### 验收标准

1. THE User_Service SHALL 使每个被保留的控制器类标注 `@RestController`，并标注带有非空 `name` 属性的 `@Tag`。
2. THE Book_Service SHALL 使每个被保留的控制器类标注 `@RestController`，并标注带有非空 `name` 属性的 `@Tag`。
3. IF 一个被保留的控制器方法仅检索数据且不改变服务端持久化状态（读取操作），THEN THE User_Service 与 THE Book_Service SHALL 为该方法标注 `@GetMapping`。
4. THE User_Service 与 THE Book_Service SHALL 为每个被保留的演示控制器方法标注带有非空 `summary` 的 `@Operation`，并赋予一个在所在服务生成的 OpenAPI 文档内唯一且非空的 `operationId`。
5. THE User_Service 与 THE Book_Service SHALL 将每个被保留的控制器方法的返回值类型声明为 `ReturnData<XxxDto>` 形式（即以 Return_Data 包装 DTO 类型），控制器方法不直接返回 pojo 类型。
6. WHERE 某个控制器接口被设计为免认证访问（即无需携带认证凭据即可访问），THE User_Service 与 THE Book_Service SHALL 使用 Ignore_Auth 注解该接口或该控制器。
7. IF 一个被保留的控制器方法创建、更新或删除服务端持久化状态（写入操作），THEN THE User_Service 与 THE Book_Service SHALL 为该方法标注 `@PostMapping`。
8. THE User_Service 与 THE Book_Service SHALL 不在被保留的控制器方法上使用未指定 HTTP 方法的泛化 `@RequestMapping`。

### 需求 4：User_Service 可编译并可构建

**用户故事：** 作为微服务示例的维护者，我希望 User_Service 在重构后能够编译并打包，从而使该服务可运行。

#### 验收标准

1. WHEN 对 User_Service 执行编译构建 时，THE User_Service SHALL 在控制器、远程客户端、监听器、模型与配置类的全部源码范围内产生零个未解析的 import 且零个编译错误。
2. THE User_Service SHALL 对 Return_Data 与 Ignore_Auth 仅引用其 `cloud-core:1.1.0` 类路径上可解析的框架类型，不引用任何在该类路径上无法解析的类型。
3. WHEN Maven 构建在零编译错误的前提下完成 时，THE User_Service SHALL 使该 Maven 构建以成功状态结束并产出一个可部署的 JAR 制品。
4. IF 存在任一未解析的 import 或任一编译错误，THEN THE User_Service SHALL 使该构建以失败状态终止、报告对应的错误，并且不产出任何制品。

### 需求 5：Book_Service 可编译并可构建

**用户故事：** 作为微服务示例的维护者，我希望当前无法编译的 Book_Service 在重构后能够编译并打包，从而修复其损坏状态。

#### 验收标准

1. THE Book_Service SHALL 以其 `cloud-core:1.1.0` 版本中真实存在、可解析的框架类型表示 Return_Data 与 Ignore_Auth，替换当前无法解析的 `com.uoquo.web.common.ReturnData` 与 `com.uoquo.web.common.annotation.IgnoreAuth` 引用，且替换后这两处引用均不存在未解析 import。
2. THE Book_Service SHALL 移除 `book/param/UserInfo` 中无法解析的 `com.uoquo.web.mybatis.sensitive.SensitiveData` 与 `SensitiveField` 引用，或以其 `cloud-core` 版本中可解析的对应类型替换之，使该类型在编译时不再存在对上述两个类型的未解析 import。
3. THE Book_Service SHALL 在所有控制器、监听器、模型与配置类中均无未解析 import、且编译过程产生零编译错误的情况下完成编译。
4. WHEN 对 Book_Service 执行 Maven 打包构建 时，THE Book_Service SHALL 以构建成功状态（无编译错误、无构建失败）结束，并产出可部署的 JAR 打包制品。

### 需求 6：两个服务使用一致的 cloud-core 版本

**用户故事：** 作为微服务示例的维护者，我希望两个服务依赖一致的 `cloud-core` 版本，从而消除因版本差异导致的包名分裂与编译不一致。

#### 验收标准

1. THE User_Service 与 THE Book_Service SHALL 在各自 `pom.xml` 中声明完全相同的 `com.uoquo:cloud-core` 版本字符串 `1.1.0`（即两处 `cloud-core` 依赖的版本取值均为 `1.1.0`，逐字符相等）。
2. THE User_Service 与 THE Book_Service SHALL 选定 `com.uoquo:cloud-core:1.1.0` 作为统一版本，使两个服务所引用的全部框架类型（包括 Return_Data 与 Ignore_Auth）均在 `cloud-core:1.1.0` 的类路径上可解析。
3. WHILE User_Service 与 Book_Service 声明同一个 `com.uoquo:cloud-core:1.1.0` 版本，THE User_Service 与 THE Book_Service SHALL 仅引用该版本类路径上可解析的框架类型，并在无任何未解析 import 的情况下完成编译。

### 需求 7：保留并提取专用的远程调用示例控制器

**用户故事：** 作为微服务示例的使用者，我希望 User_Service 调用 Book_Service 的示例集中在一个专用控制器中，从而清晰地演示服务间的远程调用。

#### 验收标准

1. THE User_Service SHALL 提供一个专用的 Remote_Call_Controller，该控制器暴露的全部接口均为对 Book_Service 的远程调用演示，且不包含任何 Redis 演示接口或事件发布演示接口。
2. WHEN Remote_Call_Controller 收到远程调用书名的请求 时，THE Remote_Call_Controller SHALL 经由 Book_Remote_Service 调用 Book_Name_Endpoint，并在远程调用成功返回时返回一个携带成功状态标识、且内容为 Book_Name_Endpoint 所返回书名字符串的 Return_Data。
3. THE User_Service SHALL 将远程调用接口从混合用途的 `TestStoreController` 中移出，使 Redis 演示与远程调用演示分属不同控制器。
4. THE Book_Remote_Service SHALL 保持为以 `name = "demo-cloud-book"`、`path = "/api/book"` 配置的 Feign 客户端。
5. IF 经由 Book_Remote_Service 远程调用 Book_Service 的过程中抛出异常（包括但不限于连接失败、调用超时或 Book_Service 返回非成功结果），THEN THE Remote_Call_Controller SHALL 捕获该异常并向调用方返回一个携带非成功状态标识与说明远程调用失败原因的错误信息的 Return_Data，且不向调用方抛出未处理异常、不中断 User_Service 的正常运行。

### 需求 8：Book_Service 提供被远程调用的书名接口

**用户故事：** 作为微服务示例的使用者，我希望 Book_Service 持续提供被 User_Service 调用的书名接口，从而保证远程调用示例端到端可用。

#### 验收标准

1. THE Book_Service SHALL 暴露 Book_Name_Endpoint，并将其设计为读取操作、使用与 Book_Remote_Service 所声明调用方法一致的 `@GetMapping`。
2. WHEN Book_Name_Endpoint 收到调用书名的请求 时，THE Book_Service SHALL 返回一个包装了非空书名字符串的 Return_Data。
3. THE Book_Name_Endpoint SHALL 位于与 Book_Remote_Service 所声明路径（上下文 `/api/book` 下的 `/store/book/name`）一致的请求映射下，使经由 Book_Remote_Service 发起的远程调用能够命中该接口。
4. WHERE Book_Name_Endpoint 被设计为供 User_Service 经 Book_Remote_Service 进行免认证远程调用，THE Book_Service SHALL 使用 Ignore_Auth 注解该接口，使该远程调用不因认证校验而被拒绝。

### 需求 9：两个服务的 Operation_Log_Listener 对齐 Reference_App 的监听器模式

**用户故事：** 作为微服务示例的维护者，我希望两个服务的事件监听器与 Reference_App 的统一监听模式一致，从而以一致的方式集中处理事件。

#### 验收标准

1. THE User_Listener 与 THE Book_Listener SHALL 各自在 `com.uoquo.listener` 包中有且仅有一个标注 `@Component` 的事件监听类 `OperationLogListener`，且所在服务中不存在其他声明 `@EventListener` 方法的组件。
2. THE User_Listener 与 THE Book_Listener SHALL 各自提供恰好一个以无参数化 App_Event 为参数的 `@EventListener` 兜底方法，该方法接收 App_Event 及其全部子类（含 Remote_Event）的事件实例。
3. WHERE 需要按业务类型过滤事件，THE User_Listener 与 THE Book_Listener SHALL 至少提供一个使用 `@EventListener` `condition` 表达式（引用事件 businessType）的处理方法，且当且仅当事件 businessType 等于该表达式指定值时该方法被调用一次、businessType 为其他值或为空时该方法不被调用。
4. WHEN 一个事件被某个监听方法接收 时，THE Operation_Log_Listener SHALL 在单条 INFO 级别日志中记录该事件的业务类型、操作类型，以及经 Json_Util 序列化得到的事件内容字符串。
5. IF 在记录事件内容时 Json_Util 序列化抛出异常，THEN THE Operation_Log_Listener SHALL 记录一条 ERROR 级别日志并正常返回，不向事件发布方抛出未处理异常，且不影响该事件其他监听方法的执行。

### 需求 10：在两个服务中新增 Remote_Event 监听器

**用户故事：** 作为微服务示例的使用者，我希望两个服务都能监听 Remote_Event，从而演示跨服务事件的接收处理。

#### 验收标准

1. THE User_Listener SHALL 提供单个 Remote_Event 监听方法，该方法接收 Remote_Event 及其全部子类（含 Custom_Event）的事件实例，且无需为任何子类单独定义监听方法。
2. THE Book_Listener SHALL 提供单个 Remote_Event 监听方法，该方法接收 Remote_Event 及其全部子类（含 Custom_Event）的事件实例，且无需为任何子类单独定义监听方法。
3. WHEN 一个 Remote_Event 被本服务的 Remote_Event 监听方法接收 时，THE Operation_Log_Listener SHALL 通过单条日志记录该事件的来源服务、目标服务、业务类型与操作类型；其中当目标服务为 `**` 或未设置时，SHALL 以可识别的广播标识记录目标服务。
4. WHEN 一个 Custom_Event（Remote_Event 的子类）被本服务接收 时，THE Operation_Log_Listener SHALL 由其 Remote_Event 监听方法以 Custom_Event 实例被调用并完成接收，而不依赖任何针对 Custom_Event 的专用监听方法。
5. IF 本服务的 Remote_Event 监听方法在接收或记录某个 Remote_Event 的过程中发生异常，THEN THE Operation_Log_Listener SHALL 记录一条指示该事件处理失败的错误日志，并且不向消息总线的事件消费流程抛出未处理异常，从而使后续 Remote_Event 仍可被接收。

### 需求 11：提供定向事件发布示例

**用户故事：** 作为微服务示例的使用者，我希望有一个接口能够发布指向特定服务的 Directed_Event，从而演示定向（而非广播）的事件投递。

#### 验收标准

1. THE User_Service SHALL 暴露一个接口，该接口发布一个 Destination_Service 设置为 `demo-cloud-book` 的 Directed_Event。
2. WHEN 收到定向发布请求 时，THE User_Service SHALL 经由 Event_Publisher 发布该 Directed_Event。
3. WHEN 一个 Directed_Event 以 `demo-cloud-book` 为 Destination_Service 被发布 时，THE Book_Service SHALL 在该事件经消息总线传播后 30 秒内通过其 Remote_Event 监听方法接收该事件。
4. WHEN 一个 Directed_Event 以 `demo-cloud-book` 为 Destination_Service 被发布 时，THE User_Service SHALL 不将该 Directed_Event 投递给应用名不为 `demo-cloud-book` 的任何服务实例。
5. WHEN 定向发布请求完成 时，THE User_Service SHALL 返回一个 Return_Data，其确认信息至少包含目标服务标识 `demo-cloud-book` 以及所发布 Directed_Event 的业务类型与操作类型。
6. THE User_Service SHALL 为 Directed_Event 设置非 null 且非空字符串的业务类型与操作类型，使 Book_Service 的 Remote_Event 监听方法能够记录这两个值。
7. IF 定向发布请求在经由 Event_Publisher 发布 Directed_Event 时失败，THEN THE User_Service SHALL 返回一个包装了错误说明的 Return_Data，而不向调用方抛出未处理异常。

### 需求 12：提供广播事件发布示例以作对照

**用户故事：** 作为微服务示例的使用者，我希望有一个接口能够发布 Broadcast_Event，从而与定向发布形成对照。

#### 验收标准

1. THE User_Service SHALL 暴露一个接口，发布一个 Destination_Service 为 `**` 或未设置的 Broadcast_Event。
2. WHEN 收到广播发布请求 时，THE User_Service SHALL 经由 Event_Publisher 发布该 Broadcast_Event。
3. WHEN 一个 Broadcast_Event 被发布 时，THE Book_Service SHALL 通过其 Remote_Event 监听方法接收该事件。
4. WHEN 一个 Broadcast_Event 被发布 时，THE User_Service SHALL 将该事件投递给所有订阅消息总线的服务实例，而不限定单一 Destination_Service，以与 Directed_Event 仅投递给单一目标服务的定向投递形成对照。
5. THE User_Service SHALL 为 Broadcast_Event 设置可被接收服务识别的业务类型与操作类型。
6. WHEN 广播发布请求处理成功完成 时，THE User_Service SHALL 返回一个表示发布成功、包装了确认信息的 Return_Data。
7. IF 经由 Event_Publisher 发布 Broadcast_Event 失败，THEN THE User_Service SHALL 返回一个包装了错误说明的 Return_Data，而不向调用方抛出未处理异常。

### 需求 13：事件发布统一经由 Event_Publisher

**用户故事：** 作为微服务示例的维护者，我希望两个服务的事件发布统一经过 Event_Publisher，从而一致地展示框架的事件发布方式。

#### 验收标准

1. THE User_Service SHALL 通过一个被依赖注入的 Event_Publisher 字段，调用其 `publishEvent` 方法发布 App_Event 与 Remote_Event。
2. THE Book_Service SHALL 通过一个被依赖注入的 Event_Publisher 字段，调用其 `publishEvent` 方法发布 App_Event 与 Remote_Event。
3. THE User_Service 与 THE Book_Service SHALL 仅通过 Event_Publisher 发布 App_Event 与 Remote_Event，不存在任何绕过 Event_Publisher 的事件发布调用。
4. THE User_Service 与 THE Book_Service SHALL 在其控制器、服务、监听器与配置代码中不注入、不持有、不调用 Spring 的 `ApplicationEventPublisher` 来发布事件。

### 需求 14：Remote_Event 序列化往返一致

**用户故事：** 作为微服务示例的维护者，我希望 Remote_Event 经 Json_Util 序列化后再反序列化能够保持关键字段一致，从而保证事件跨消息总线传播时的正确性。

#### 验收标准

1. THE User_Service 与 THE Book_Service SHALL 为 Remote_Event 的具体子类 Custom_Event 提供公有无参构造函数，以支持反序列化。
2. WHEN 一个 Remote_Event 对象（其业务类型、操作类型、操作状态为长度 0–255 的字符串、含空串，目标服务取某服务名、`**` 或未设置）经 Json_Util 序列化为 String 再反序列化为该具体子类型 时，THE Json_Util SHALL 产生一个非 null 的 Remote_Event，其业务类型、操作类型、操作状态与目标服务均与原始事件逐字段相等（往返属性，覆盖空串与未设置目标服务的边界）。
3. IF 反序列化的输入为非合法 JSON，THEN THE Json_Util SHALL 抛出异常以指示失败，而不返回 null 或字段不完整的对象。
