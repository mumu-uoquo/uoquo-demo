# Design Document

## Feature: service-refactor-remote-events

---

## Overview

本功能对 `demo-service-user` 与 `demo-service-book` 两个 Spring Cloud 微服务模块进行结构性重构，使其在包结构、控制器规范、模型分层、监听器模式上对齐参考模块 `demo-app`，同时修复 `demo-service-book` 当前无法编译的问题，并新增远程调用示例控制器与 RemoteEvent 监听/发布的对照示例。

### 核心目标

1. **统一 cloud-core 版本**：将两个服务统一到 `cloud-core:1.1.0`，消除因版本差异导致的包名分裂与编译失败。
2. **包结构对齐**：按 `demo-app` 基线重组两个服务的包结构，迁移散落的模型类，移除两个服务的 filter/interceptor 包。
3. **控制器规范化**：拆分混合职责的控制器，按 Demo_Controller_Convention 规范命名与注解。
4. **监听器统一**：重构两个服务的 `OperationLogListener`，对齐 `demo-app` 的统一监听模式，新增 `RemoteEvent<?>` 兜底监听方法。
5. **事件发布示例**：在 User_Service 中提供定向发布（`destinationService="demo-cloud-book"`）与广播发布（`destinationService="**"`）的对照接口。

### 关键约束

- 两个服务均基于 `cloud-core`（非 `app-core`），框架类型包名与 `demo-app` 不同，需使用 `cloud-core:1.1.0` 中可解析的类型。
- Book_Service 的 `/store/book/name` 接口路径不变，保证 `BookRemoteService` 的 Feign 调用可命中。
- 两个服务均移除 `com.uoquo.filter` 与 `com.uoquo.interceptor` 包，与 Reference_App 基线保持一致。

---

## Architecture

### 服务间关系

```mermaid
graph LR
    subgraph demo-service-user ["demo-service-user (demo-cloud-user:7005)"]
        RC[RemoteCallDemoController]
        EC[EventDemoController]
        BRS[BookRemoteService<br/>@FeignClient]
        UL[OperationLogListener]
    end

    subgraph demo-service-book ["demo-service-book (demo-cloud-book:7006)"]
        BD[BookDemoController<br/>/store/book/name]
        BL[OperationLogListener]
    end

    subgraph infra ["基础设施"]
        Kafka[Kafka<br/>spring-cloud-bus]
        Nacos[Nacos<br/>服务发现/配置]
    end

    RC -->|Feign HTTP| BD
    EC -->|publishEvent| Kafka
    Kafka -->|Directed/Broadcast| BL
    Kafka -->|Broadcast| UL
    BRS -.->|LoadBalancer| Nacos
```

### cloud-core 版本决策

**统一到 `cloud-core:1.1.0`**，理由如下：

- User_Service 当前使用 1.0.6 且可编译，重构后升级至 1.1.0，并确保所有框架类型引用在 1.1.0 类路径上可解析。
- Book_Service 使用 1.1.0 导致 `com.uoquo.web.common.ReturnData`、`com.uoquo.web.common.annotation.IgnoreAuth` 等类型无法解析，是当前编译失败的根本原因；升级到 1.1.0 后需确认 1.1.0 下可解析的正确类型路径。
- 两个服务统一到 1.1.0 后，ReturnData 与 IgnoreAuth 的正确包路径需在 1.1.0 类路径上确认可解析的类型（如与 1.0.6 路径不同，需以 1.1.0 中实际存在的类型替换；若不确定，需在 1.1.0 类路径上确认可解析的 ReturnData 与 IgnoreAuth 类型）。

**1.1.0 下的框架类型映射**（两个服务统一使用）：

| 用途 | 类型全限定名 |
|------|-------------|
| 响应包装 | `com.uoquo.web.common.ReturnData`（需在 1.1.0 类路径上确认可解析） |
| 免认证注解 | `com.uoquo.web.common.annotation.IgnoreAuth`（需在 1.1.0 类路径上确认可解析） |
| Redis 工具 | `com.uoquo.web.handler.RedisUtil` |
| 缓存 Key 前缀 | `com.uoquo.web.common.BaseCacheKey` |
| 应用事件基类 | `com.uoquo.web.events.AppEvent<T>` |
| 远程事件类型 | `com.uoquo.cloud.events.RemoteEvent<T>` |
| 事件发布器 | `com.uoquo.web.events.UoquoEventPublisher` |
| JSON 工具 | `com.uoquo.utils.json.JsonUtil` |

---

## Components and Interfaces

### demo-service-user 重构后包结构

```
com.uoquo.main
  └── DemoCloudUserApplication

com.uoquo.main.config
  ├── ApplicationConfig          # 移除 filter/interceptor 注册代码
  ├── ApplicationDeveloperConfig # 升级至 1.1.0 后确认可编译
  ├── MybatisDataSourceOneConfig
  ├── MybatisDataSourceTwoConfig
  └── OpenApiConfig

com.uoquo.listener
  └── OperationLogListener       # 重构为统一模式

com.uoquo.user.controller
  ├── ResponseDemoController     # 原 TestController 重命名规范化
  ├── RedisDemoController        # 从 TestStoreController 拆出 Redis 部分
  ├── EventDemoController        # 从 TestEventController 重构，含定向/广播发布
  └── RemoteCallDemoController   # 新增，专用远程调用演示

com.uoquo.user.events
  ├── CustomEvent                # 泛型改为 user.model.pojo.UserInfo
  └── InnerEvent                 # 保留

com.uoquo.user.model.pojo
  └── UserInfo                   # 从 user.bean 迁移，移除 SensitiveData/SensitiveField

com.uoquo.user.model.dto
  └── UserInfoDto                # DTO 类，控制器返回值使用

com.uoquo.user.remote
  └── BookRemoteService          # 保留，Feign 配置不变

com.uoquo.user.service
  └── TestErrorService           # 保留
```

### demo-service-book 重构后包结构

```
com.uoquo.main
  └── DemoCloudBookApplication

com.uoquo.main.config
  ├── ApplicationConfig          # 移除 filter/interceptor 注册代码
  ├── ApplicationDeveloperConfig # 修复 package 声明 + 替换无法解析类型
  ├── MybatisDataSourcePrimaryConfig
  └── OpenApiConfig

com.uoquo.listener
  └── OperationLogListener       # 重构为统一模式，新增 RemoteEvent 监听

com.uoquo.book.controller
  ├── ResponseDemoController     # 原 TestController 重命名规范化
  └── BookDemoController         # 原 TestStoreController，保留 /store/book/name

com.uoquo.book.events
  └── CustomEvent                # 泛型改为 book.model.pojo.UserInfo

com.uoquo.book.model.pojo
  └── UserInfo                   # 从 book.param 迁移，移除 SensitiveData/SensitiveField

com.uoquo.book.model.param
  └── LoginParam                 # 从 book.param 迁移

com.uoquo.book.service
  └── TestErrorService           # 保留
```

### 控制器接口规范

#### User_Service 控制器

**ResponseDemoController**（原 TestController）
- `@Tag(name = "response-demo")`
- `@RequestMapping("/demo/response")`
- `GET /demo/response/string` → `ReturnData<String>`
- `GET /demo/response/error1` → `ReturnData<Long>`（演示异常处理）
- `GET /demo/response/error2` → `ReturnData<Long>`（演示 SystemErrorException）

**RedisDemoController**（从 TestStoreController 拆出）
- `@Tag(name = "redis-demo")`
- `@RequestMapping("/demo/redis")`
- `@IgnoreAuth(all = true)`
- `GET /demo/redis/set` → `ReturnData<Boolean>`
- `GET /demo/redis/get` → `ReturnData<String>`
- `DELETE /demo/redis/delete` → `ReturnData<Boolean>`
- `GET /demo/redis/exists` → `ReturnData<Boolean>`

**EventDemoController**（从 TestEventController 重构）
- `@Tag(name = "event-demo")`
- `@RequestMapping("/demo/event")`
- `@IgnoreAuth(all = true)`
- `GET /demo/event/directed` → `ReturnData<String>`（定向发布，destinationService="demo-cloud-book"，businessType="USER"，operationType="UPDATE"）
- `GET /demo/event/broadcast` → `ReturnData<String>`（广播发布，destinationService="**"，businessType="AUTH"，operationType="LOGIN"）

**RemoteCallDemoController**（新增）
- `@Tag(name = "remote-call-demo")`
- `@RequestMapping("/demo/remote")`
- `@IgnoreAuth(all = true)`
- `GET /demo/remote/book/name` → `ReturnData<String>`（经 BookRemoteService 调用 Book_Service）

#### Book_Service 控制器

**ResponseDemoController**（原 TestController）
- `@Tag(name = "response-demo")`
- `@RequestMapping("/demo/response")`
- `@IgnoreAuth(all = true)`
- `GET /demo/response/string` → `ReturnData<String>`
- `GET /demo/response/error1` → `ReturnData<Long>`
- `GET /demo/response/error2` → `ReturnData<Long>`
- `POST /demo/response/body` → `ReturnData<String>`（接收 LoginParam）

**BookDemoController**（原 TestStoreController）
- `@Tag(name = "book-demo")`
- `@RequestMapping("/store")`
- `@IgnoreAuth(all = true)`
- `GET /store/book/name` → `ReturnData<String>`（Book_Name_Endpoint，路径不变）

### OperationLogListener 重构规范

两个服务的 `OperationLogListener` 统一重构为以下模式（对齐 `demo-app`）：

```java
@Component
public class OperationLogListener {

    // 1. AppEvent<?> 兜底方法（接收所有 AppEvent 及子类，含 RemoteEvent）
    @EventListener
    public void onAppEvent(AppEvent<?> event) {
        log.info("统一事件处理：{}, {}, {}", 
            event.getBusinessType(), event.getOperationType(), JsonUtil.serialize(event));
    }

    // 2. RemoteEvent<?> 专用方法（接收所有 RemoteEvent 及子类，含 CustomEvent）
    @EventListener
    public void onRemoteEvent(RemoteEvent<?> event) {
        String dest = (event.getDestinationService() == null 
            || "**".equals(event.getDestinationService())) 
            ? "[BROADCAST]" : event.getDestinationService();
        log.info("RemoteEvent: from={}, to={}, bizType={}, opType={}",
            event.getOriginService(), dest,
            event.getBusinessType(), event.getOperationType());
    }

    // 3. 带 condition 的过滤方法（按 businessType 过滤）
    @EventListener(condition = "'AUTH'.equals(#event.businessType)")
    public void onAuthEvent(AppEvent<?> event) {
        log.info("处理认证事件【{}】: {}", event.getOperationType(), JsonUtil.serialize(event));
    }
}
```

**移除**：针对 `MessageEvent`、`CustomEvent` 的专用 `@EventListener` 方法。

**异常处理**：`JsonUtil.serialize` 调用包裹在 try-catch 中，异常时记录 ERROR 日志并正常返回，不向事件发布方抛出。

### BookRemoteService（保持不变）

```java
@FeignClient(name = "demo-cloud-book", contextId = "BookRemoteService", path = "/api/book")
@LoadBalancerClient(name = "demo-cloud-book", configuration = IphashLoadBalancerConfiguration.class)
public interface BookRemoteService {
    @GetMapping(value = "/store/book/name")
    String getBookName();
}
```

---

## Data Models

### User_Service 模型迁移

**`com.uoquo.user.model.pojo.UserInfo`**（从 `com.uoquo.user.bean.UserInfo` 迁移）

迁移要点：
- 移除 `@SensitiveData` 类注解（`com.uoquo.web.mybatis.sensitive.SensitiveData` 在 1.0.6 下虽可解析，但迁移后的 pojo 不再需要该注解）
- 移除 `@SensitiveField` 字段注解（同上）
- 保留所有字段（id, instituteId, referralCode, userName, phone, password, email, state, stateTime, stateMemo, pwdExpired, pwdLevel, pwdEditTime, loginErrorCount, lastedLoginIp, lastedLoginTime, createUser, createTime, updateUser, updateTime, deleteState）
- 保留所有 getter/setter

**`com.uoquo.user.model.dto.UserInfoDto`**（新建 dto 子包及 DTO 类）

按 Model_Layout 约定，`com.uoquo.user.model.dto` 包必须存在，并提供对应的 DTO 类：
- 创建 `com.uoquo.user.model.dto` 包
- 创建 `UserInfoDto` 类，包含控制器返回所需的用户信息字段
- 控制器方法返回类型使用 `ReturnData<UserInfoDto>` 形式（演示接口可使用 `ReturnData<String>`）

**`com.uoquo.user.events.CustomEvent`**（更新泛型引用）

```java
package com.uoquo.user.events;

import com.uoquo.cloud.events.RemoteEvent;
import com.uoquo.user.model.pojo.UserInfo;  // 从 user.bean 改为 user.model.pojo

public class CustomEvent extends RemoteEvent<UserInfo> {
    private String message;
    public CustomEvent() {}  // 必须有无参构造函数（支持反序列化）
    // ... 构造函数和 getter/setter 保持不变
}
```

### Book_Service 模型迁移

**`com.uoquo.book.model.pojo.UserInfo`**（从 `com.uoquo.book.param.UserInfo` 迁移）

迁移要点：
- 移除 `@SensitiveData` 类注解（`com.uoquo.web.mybatis.sensitive.SensitiveData` 在 1.1.0 下无法解析，是编译错误来源之一）
- 移除 `@SensitiveField` 字段注解（同上）
- 保留所有字段和 getter/setter

**`com.uoquo.book.model.param.LoginParam`**（从 `com.uoquo.book.param.LoginParam` 迁移）

- 仅迁移包路径，内容不变

**`com.uoquo.book.events.CustomEvent`**（更新泛型引用）

```java
package com.uoquo.book.events;

import com.uoquo.cloud.events.RemoteEvent;
import com.uoquo.book.model.pojo.UserInfo;  // 从 book.param 改为 book.model.pojo

public class CustomEvent extends RemoteEvent<UserInfo> {
    private String message;
    public CustomEvent() {}  // 必须有无参构造函数（支持反序列化）
    // ... getter/setter 保持不变
}
```

### Book_Service ApplicationDeveloperConfig 修复

**修复内容**：
1. 将 `package com.uoquo.platform.main.config;` 改为 `package com.uoquo.main.config;`
2. 将 `import com.uoquo.web.common.BaseCacheKey;` 替换为 `cloud-core:1.1.0` 类路径上可解析的对应类型（需在 1.1.0 类路径上确认可解析的 BaseCacheKey 类型）
3. 将 `import com.uoquo.web.handler.RedisUtil;` 替换为 `cloud-core:1.1.0` 类路径上可解析的对应类型（需在 1.1.0 类路径上确认可解析的 RedisUtil 类型）

注：两个服务统一升级到 1.1.0 后，`BaseCacheKey` 和 `RedisUtil` 的包路径需在 1.1.0 类路径上确认可解析的正确类型；若 1.1.0 中路径与 1.0.6 相同则直接保留，若不同则以 1.1.0 中实际存在的类型替换。

### Book_Service ApplicationConfig 修复

移除对 `com.uoquo.filter.TestErrorFilter1`、`com.uoquo.filter.TestErrorFilter2`、`com.uoquo.interceptor.TestErrorInterceptor` 的导入和注册代码（需求 2.6 要求移除 filter/interceptor 包）。

修复后的 `ApplicationConfig` 仅保留 `WebMvcConfigurer` 接口实现，不注册任何 filter 或 interceptor。

### User_Service ApplicationConfig 修复

与 Book_Service 保持一致，移除对 `com.uoquo.filter.TestErrorFilter1`、`com.uoquo.filter.TestErrorFilter2`、`com.uoquo.interceptor.TestErrorInterceptor` 的导入和注册代码（需求 1.6 要求移除 filter/interceptor 包）。

修复后的 `ApplicationConfig` 仅保留 `WebMvcConfigurer` 接口实现，不注册任何 filter 或 interceptor。

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

本功能的大多数需求属于代码结构规范（包路径、注解、编译约束），不适合属性测试。唯一适合 PBT 的需求是需求 14.2：`CustomEvent` 的序列化往返一致性。

该属性满足 PBT 适用条件：
- 被测对象是纯函数（`JsonUtil.serialize` + `JsonUtil.deserialize`）
- 输入空间大（字符串字段的任意组合）
- 存在明确的"for all"量化语句
- 100 次迭代能覆盖空串、null、特殊字符等边界情况

**Property Reflection**：需求 14.2 描述了一个完整的往返属性，覆盖了 businessType、operationType、operationStatus、destinationService 四个关键字段。需求 14.1（无参构造函数）是 14.2 的前提条件，不需要单独的属性测试。需求 14.3（非法 JSON 抛出异常）是边界条件，由属性测试的生成器覆盖范围之外，作为独立的 edge case 测试处理。因此只需一个属性。

### Property 1: CustomEvent 序列化往返一致

*For any* `CustomEvent`（其 `businessType`、`operationType`、`operationStatus` 为长度 0–255 的任意字符串（含空串），`destinationService` 为某服务名、`"**"` 或 `null`），经 `JsonUtil.serialize` 序列化为 String 再经 `JsonUtil.deserialize` 反序列化为 `CustomEvent` 后，所得对象非 null，且其 `businessType`、`operationType`、`operationStatus`、`destinationService` 均与原始事件逐字段相等。

**Validates: Requirements 14.2**

---

## Error Handling

### RemoteCallDemoController 异常处理

```java
@GetMapping("/book/name")
@Operation(description = "远程调用 Book_Service 获取书名", operationId = "remoteGetBookName")
public ReturnData<String> getBookName() {
    try {
        String name = bookRemoteService.getBookName();
        return new ReturnData<>(name);
    } catch (Exception e) {
        try {
            log.error("远程调用 Book_Service 失败", e);
        } catch (Exception ignore) {}
        return new ReturnData<>(false, "远程调用失败: " + e.getMessage());
    }
}
```

### EventDemoController 异常处理

定向发布和广播发布接口均采用相同模式：

```java
try {
    CustomEvent event = new CustomEvent("USER", "UPDATE", "SUCCESS", "demo-cloud-book");
    publisher.publishEvent(event);
    return new ReturnData<>("定向事件已发布，目标服务: demo-cloud-book, bizType: USER, opType: UPDATE");
} catch (Exception e) {
    try {
        log.error("定向事件发布失败", e);
    } catch (Exception ignore) {}
    return new ReturnData<>(false, "事件发布失败: " + e.getMessage());
}
```

### OperationLogListener 异常处理

```java
@EventListener
public void onAppEvent(AppEvent<?> event) {
    try {
        String content = JsonUtil.serialize(event);
        log.info("统一事件处理：{}, {}, {}", 
            event.getBusinessType(), event.getOperationType(), content);
    } catch (Exception e) {
        try {
            log.error("事件序列化失败，bizType={}, opType={}", 
                event.getBusinessType(), event.getOperationType(), e);
        } catch (Exception ignore) {}
    }
}
```

### Book_Service 编译错误修复策略

| 错误类型 | 修复方案 |
|---------|---------|
| `com.uoquo.web.common.ReturnData` 无法解析 | 升级到 1.1.0 并确认 1.1.0 下可解析的正确 ReturnData 类型路径（需在 1.1.0 类路径上确认） |
| `com.uoquo.web.common.annotation.IgnoreAuth` 无法解析 | 升级到 1.1.0 并确认 1.1.0 下可解析的正确 IgnoreAuth 类型路径（需在 1.1.0 类路径上确认） |
| `com.uoquo.web.mybatis.sensitive.SensitiveData/SensitiveField` 无法解析 | 从 `book.param.UserInfo` 中移除这两个注解 |
| `com.uoquo.web.common.BaseCacheKey` 无法解析 | 确认 1.1.0 类路径上可解析的对应类型 |
| `com.uoquo.web.handler.RedisUtil` 无法解析 | 确认 1.1.0 类路径上可解析的对应类型 |
| `ApplicationDeveloperConfig` package 声明错误 | 修正为 `com.uoquo.main.config` |

---

## Testing Strategy

### 测试分层

本功能的测试分为三层：

**1. 编译验证（Smoke）**

大多数需求（包结构、注解规范、编译约束）通过 `mvn compile` 和 `mvn package` 验证：
- User_Service：`mvn -pl demo-service-user compile`
- Book_Service：`mvn -pl demo-service-book compile`

**2. 单元测试（Example-based）**

针对控制器的错误处理行为，使用 Mockito mock 依赖：

- `RemoteCallDemoController`：mock `BookRemoteService` 抛出异常，验证返回非成功 `ReturnData` 且不抛出异常
- `EventDemoController`：mock `UoquoEventPublisher` 抛出异常，验证返回错误 `ReturnData` 且不抛出异常
- `BookDemoController`：验证 `/store/book/name` 返回非空书名的 `ReturnData`

**3. 属性测试（Property-based，使用 jqwik）**

针对需求 14.2 的序列化往返一致性，在 `demo-service-user` 的测试目录中添加：

```
demo-service-user/src/test/java/com/uoquo/user/event/
  └── CustomEventSerializationPropertyTest.java
```

测试配置：
- 框架：`net.jqwik:jqwik`（项目已有 `.jqwik-database` 文件，与 `demo-app` 保持一致）
- 每个属性测试运行 **100 次**（`@Property(tries = 100)`）
- 标签注释：`// Feature: service-refactor-remote-events, Property 1: CustomEvent 序列化往返一致`

**属性测试实现要点**：

```java
// Feature: service-refactor-remote-events, Property 1: CustomEvent 序列化往返一致
@Property(tries = 100)
void customEventSerializationRoundTrip(
        @ForAll("eventTexts") String businessType,
        @ForAll("eventTexts") String operationType,
        @ForAll("eventTexts") String operationStatus,
        @ForAll("destinationServices") @Nullable String destinationService) {

    CustomEvent original = destinationService != null
        ? new CustomEvent(businessType, operationType, operationStatus, destinationService)
        : new CustomEvent(businessType, operationType, operationStatus);

    String json = JsonUtil.serialize(original);
    CustomEvent restored = JsonUtil.deserialize(json, CustomEvent.class);

    assertThat(restored).isNotNull();
    assertThat(restored.getBusinessType()).isEqualTo(original.getBusinessType());
    assertThat(restored.getOperationType()).isEqualTo(original.getOperationType());
    assertThat(restored.getOperationStatus()).isEqualTo(original.getOperationStatus());
    assertThat(restored.getDestinationService()).isEqualTo(original.getDestinationService());
}

@Provide
Arbitrary<String> eventTexts() {
    return Arbitraries.oneOf(
        Arbitraries.just(""),
        Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(50),
        Arbitraries.strings().withCharRange('\u4e00', '\u9fa5').ofMinLength(1).ofMaxLength(20)
    );
}

@Provide
Arbitrary<String> destinationServices() {
    return Arbitraries.oneOf(
        Arbitraries.just(null),
        Arbitraries.just("**"),
        Arbitraries.just("demo-cloud-book"),
        Arbitraries.just("demo-cloud-user"),
        Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(30)
    );
}
```

**边界条件覆盖**（由生成器自动覆盖）：
- `businessType`/`operationType`/`operationStatus` 为空串
- `destinationService` 为 `null`（广播场景）
- `destinationService` 为 `"**"`（广播场景）
- `destinationService` 为具体服务名（定向场景）
- 包含非 ASCII 字符（中文）的字段值

**非法 JSON 边界测试**（独立 edge case，非属性测试）：

```java
@Test
void deserializeInvalidJsonThrowsException() {
    assertThatThrownBy(() -> JsonUtil.deserialize("not-valid-json", CustomEvent.class))
        .isInstanceOf(Exception.class);
}
```

### 测试执行顺序建议

1. 先执行编译验证（`mvn compile`），确认两个服务零编译错误
2. 再执行单元测试（`mvn test -pl demo-service-user`）
3. 最后执行属性测试（包含在 `mvn test` 中，jqwik 自动发现）
