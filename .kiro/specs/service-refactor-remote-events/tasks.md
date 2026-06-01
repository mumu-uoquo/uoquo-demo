# Implementation Plan: service-refactor-remote-events

## Overview

按照设计文档，分步重构 `demo-service-user` 与 `demo-service-book` 两个 Spring Cloud 微服务：统一 `cloud-core` 版本至 1.1.0、对齐包结构与模型分层（含 user.model.dto 子包与 UserInfoDto）、规范化控制器写法（返回 `ReturnData<XxxDto>`）、重构统一事件监听器并新增 RemoteEvent 监听、提取专用远程调用控制器、新增定向/广播事件发布示例，并为 CustomEvent 序列化往返一致性编写属性测试。

## Tasks

- [x] 1. 统一 cloud-core 版本至 1.1.0 并修复编译错误
  - [x] 1.1 将 `demo-service-user/pom.xml` 中 `cloud-core` 版本从 `1.0.6` 升级为 `1.1.0`
    - 修改 `<version>1.0.6</version>` 为 `<version>1.1.0</version>`，使两个服务版本字符串逐字符相等
    - _Requirements: 4.2, 6.1, 6.2_
  - [x] 1.2 确认并修复两个服务中所有框架类型引用在 `cloud-core:1.1.0` 类路径上可解析
    - 确认 `com.uoquo.web.common.ReturnData` 与 `com.uoquo.web.common.annotation.IgnoreAuth` 在 1.1.0 下可解析；若路径不同则以 1.1.0 中实际存在的类型替换
    - 修复 Book_Service `ApplicationDeveloperConfig` 的 `package` 声明：将 `com.uoquo.platform.main.config` 改为 `com.uoquo.main.config`
    - 确认 `BaseCacheKey`、`RedisUtil` 在 1.1.0 下可解析；若路径不同则以 1.1.0 中实际存在的类型替换
    - _Requirements: 4.2, 5.1, 5.3, 6.2, 6.3_

- [x] 2. 迁移 Book_Service 模型类并移除无法解析注解
  - [x] 2.1 将 `com.uoquo.book.param.UserInfo` 迁移到 `com.uoquo.book.model.pojo.UserInfo`
    - 新建 `com.uoquo.book.model.pojo` 包，创建 `UserInfo.java`
    - 移除 `@SensitiveData` 类注解与 `@SensitiveField` 字段注解（消除无法解析 import）
    - 保留所有字段及 getter/setter
    - _Requirements: 2.3, 5.2_
  - [x] 2.2 将 `com.uoquo.book.param.LoginParam` 迁移到 `com.uoquo.book.model.param.LoginParam`
    - 新建 `com.uoquo.book.model.param` 包，创建 `LoginParam.java`，内容不变
    - _Requirements: 2.4_
  - [x] 2.3 更新 `com.uoquo.book.events.CustomEvent` 的泛型引用
    - 将 import 从 `com.uoquo.book.param.UserInfo` 改为 `com.uoquo.book.model.pojo.UserInfo`
    - 确保存在公有无参构造函数（支持反序列化）
    - _Requirements: 5.3, 14.1_
  - [x] 2.4 删除旧的 `com.uoquo.book.param` 包下已迁移的源文件
    - 删除 `book/param/UserInfo.java` 与 `book/param/LoginParam.java`
    - _Requirements: 2.3, 2.4_

- [x] 3. 迁移 User_Service 模型类并创建 dto 子包
  - [x] 3.1 将 `com.uoquo.user.bean.UserInfo` 迁移到 `com.uoquo.user.model.pojo.UserInfo`
    - 新建 `com.uoquo.user.model.pojo` 包，创建 `UserInfo.java`
    - 移除 `@SensitiveData` 与 `@SensitiveField` 注解（迁移后 pojo 不再需要）
    - 保留所有字段及 getter/setter
    - _Requirements: 1.3_
  - [x] 3.2 创建 `com.uoquo.user.model.dto` 包及 `UserInfoDto` 类
    - 新建 `com.uoquo.user.model.dto` 包（对齐 Model_Layout，`model.dto` 子包必须存在）
    - 创建 `UserInfoDto.java`，包含控制器返回所需的用户信息字段（如 id、userName、phone、email、state 等）
    - 提供标准 getter/setter
    - _Requirements: 1.3, 3.5_
  - [x] 3.3 更新 `com.uoquo.user.events.CustomEvent` 的泛型引用
    - 将 import 从 `com.uoquo.user.bean.UserInfo` 改为 `com.uoquo.user.model.pojo.UserInfo`
    - 确保存在公有无参构造函数（支持反序列化）
    - _Requirements: 1.3, 14.1_
  - [x] 3.4 删除旧的 `com.uoquo.user.bean` 包下已迁移的源文件
    - 删除 `user/bean/UserInfo.java`
    - _Requirements: 1.3_

- [x] 4. 检查点 — 确认模型迁移后两个服务可编译
  - 执行 `mvn -pl demo-service-user,demo-service-book compile`，确认零编译错误后继续
  - 如有未解析 import，回到任务 1–3 修复后再继续

- [x] 5. 重构 Book_Service 包结构与配置类
  - [x] 5.1 将 Book_Service 启动类迁移到 `com.uoquo.main` 包（若当前不在该包）
    - 确认 `DemoCloudBookApplication.java` 位于 `com.uoquo.main`，否则迁移并更新 `package` 声明
    - _Requirements: 2.1_
  - [x] 5.2 将 Book_Service 配置类整理到 `com.uoquo.main.config` 包，并移除 filter/interceptor 注册代码
    - 确认 `ApplicationConfig`、`ApplicationDeveloperConfig`、`MybatisDataSourcePrimaryConfig`、`OpenApiConfig` 均位于 `com.uoquo.main.config`
    - 修改 `ApplicationConfig`：移除对 `com.uoquo.filter.*` 与 `com.uoquo.interceptor.*` 的 import 及注册代码，仅保留 `WebMvcConfigurer` 实现
    - _Requirements: 2.1, 2.6_
  - [x] 5.3 删除 Book_Service 的 `com.uoquo.filter` 与 `com.uoquo.interceptor` 包
    - 删除 `filter/` 与 `interceptor/` 目录下的全部源文件
    - _Requirements: 2.6_

- [x] 6. 重构 User_Service 包结构与配置类，移除 filter/interceptor 包
  - [x] 6.1 将 User_Service 启动类迁移到 `com.uoquo.main` 包（若当前不在该包）
    - 确认 `DemoCloudUserApplication.java` 位于 `com.uoquo.main`，否则迁移并更新 `package` 声明
    - _Requirements: 1.1_
  - [x] 6.2 将 User_Service 配置类整理到 `com.uoquo.main.config` 包，并移除 filter/interceptor 注册代码
    - 确认 `ApplicationConfig`、`ApplicationDeveloperConfig`、`MybatisDataSourceOneConfig`、`MybatisDataSourceTwoConfig`、`OpenApiConfig` 均位于 `com.uoquo.main.config`
    - 修改 `ApplicationConfig`：移除对 `com.uoquo.filter.TestErrorFilter1`、`com.uoquo.filter.TestErrorFilter2`、`com.uoquo.interceptor.TestErrorInterceptor` 的 import 及注册代码，仅保留 `WebMvcConfigurer` 实现
    - _Requirements: 1.1, 1.6_
  - [x] 6.3 删除 User_Service 的 `com.uoquo.filter` 与 `com.uoquo.interceptor` 包
    - 删除 `filter/` 与 `interceptor/` 目录下的全部源文件，与 Book_Service 及 Reference_App 基线保持一致
    - _Requirements: 1.6_

- [x] 7. 规范化 Book_Service 控制器
  - [x] 7.1 将 `TestController` 重命名/重构为 `ResponseDemoController`，迁移到 `com.uoquo.book.controller`
    - 类标注 `@RestController` 与 `@Tag(name = "response-demo")`
    - 类级别标注 `@IgnoreAuth(all = true)`
    - 方法 `getString`：`@GetMapping("/demo/response/string")`，`@Operation(summary = "...", operationId = "bookResponseString")`，返回 `ReturnData<String>`
    - 方法 `getError1`：`@GetMapping("/demo/response/error1")`，`@Operation(summary = "...", operationId = "bookResponseError1")`，返回 `ReturnData<Long>`
    - 方法 `getError2`：`@GetMapping("/demo/response/error2")`，`@Operation(summary = "...", operationId = "bookResponseError2")`，返回 `ReturnData<Long>`
    - 方法 `postBody`：`@PostMapping("/demo/response/body")`，`@Operation(summary = "...", operationId = "bookResponseBody")`，接收 `LoginParam`，返回 `ReturnData<String>`
    - 移除泛化 `@RequestMapping` 方法映射
    - _Requirements: 2.5, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8_
  - [x] 7.2 将 `TestStoreController` 重命名/重构为 `BookDemoController`，迁移到 `com.uoquo.book.controller`
    - 类标注 `@RestController`、`@Tag(name = "book-demo")`、`@RequestMapping("/store")`、`@IgnoreAuth(all = true)`
    - 保留 `GET /store/book/name` 接口，标注 `@GetMapping("/book/name")`，`@Operation(summary = "获取书名", operationId = "bookGetName")`，返回 `ReturnData<String>`（非空书名字符串）
    - 移除泛化 `@RequestMapping` 方法映射
    - _Requirements: 2.5, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 8.1, 8.2, 8.3, 8.4_

- [x] 8. 规范化 User_Service 控制器（拆分混合职责，返回 ReturnData<XxxDto>）
  - [x] 8.1 将 `TestController` 重命名/重构为 `ResponseDemoController`，迁移到 `com.uoquo.user.controller`
    - 类标注 `@RestController` 与 `@Tag(name = "response-demo")`
    - 方法 `getString`：`@GetMapping("/demo/response/string")`，`@Operation(summary = "...", operationId = "userResponseString")`，返回 `ReturnData<String>`
    - 方法 `getError1`：`@GetMapping("/demo/response/error1")`，`@Operation(summary = "...", operationId = "userResponseError1")`，返回 `ReturnData<Long>`
    - 方法 `getError2`：`@GetMapping("/demo/response/error2")`，`@Operation(summary = "...", operationId = "userResponseError2")`，返回 `ReturnData<Long>`
    - 移除泛化 `@RequestMapping` 方法映射
    - _Requirements: 1.4, 3.1, 3.3, 3.4, 3.5, 3.7, 3.8_
  - [x] 8.2 从 `TestStoreController` 拆出 Redis 演示部分，创建 `RedisDemoController`（`com.uoquo.user.controller`）
    - 类标注 `@RestController`、`@Tag(name = "redis-demo")`、`@RequestMapping("/demo/redis")`、`@IgnoreAuth(all = true)`
    - 注入 `RedisUtil`
    - `GET /demo/redis/set`：`@Operation(operationId = "userRedisSet")`，返回 `ReturnData<Boolean>`
    - `GET /demo/redis/get`：`@Operation(operationId = "userRedisGet")`，返回 `ReturnData<String>`
    - `DELETE /demo/redis/delete`：`@Operation(operationId = "userRedisDelete")`，返回 `ReturnData<Boolean>`
    - `GET /demo/redis/exists`：`@Operation(operationId = "userRedisExists")`，返回 `ReturnData<Boolean>`
    - 移除泛化 `@RequestMapping` 方法映射
    - _Requirements: 1.4, 3.1, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8_
  - [x] 8.3 创建 `RemoteCallDemoController`（`com.uoquo.user.controller`），专用于远程调用演示
    - 类标注 `@RestController`、`@Tag(name = "remote-call-demo")`、`@RequestMapping("/demo/remote")`、`@IgnoreAuth(all = true)`
    - 注入 `BookRemoteService`
    - `GET /demo/remote/book/name`：`@Operation(summary = "远程调用 Book_Service 获取书名", operationId = "remoteGetBookName")`，返回 `ReturnData<String>`
    - 调用 `bookRemoteService.getBookName()`，成功时返回包含书名的 `ReturnData`；异常时捕获并返回包含错误说明的非成功 `ReturnData`，不向调用方抛出未处理异常
    - 该控制器不包含任何 Redis 演示或事件发布接口
    - _Requirements: 1.4, 3.1, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 7.1, 7.2, 7.3, 7.4, 7.5_
  - [x] 8.4 从 `TestEventController` 重构为 `EventDemoController`（`com.uoquo.user.controller`），含定向/广播发布
    - 类标注 `@RestController`、`@Tag(name = "event-demo")`、`@RequestMapping("/demo/event")`、`@IgnoreAuth(all = true)`
    - 注入 `UoquoEventPublisher`
    - `GET /demo/event/directed`：`@Operation(summary = "定向发布事件到 demo-cloud-book", operationId = "eventDirected")`，返回 `ReturnData<String>`；构造 `CustomEvent(businessType="USER", operationType="UPDATE", operationStatus="SUCCESS", destinationService="demo-cloud-book")`，经 `publisher.publishEvent` 发布；成功时返回含目标服务、businessType、operationType 的确认信息；异常时返回错误 `ReturnData`
    - `GET /demo/event/broadcast`：`@Operation(summary = "广播发布事件", operationId = "eventBroadcast")`，返回 `ReturnData<String>`；构造 `CustomEvent(businessType="AUTH", operationType="LOGIN", operationStatus="SUCCESS", destinationService="**")`，经 `publisher.publishEvent` 发布；成功时返回确认信息；异常时返回错误 `ReturnData`
    - 移除泛化 `@RequestMapping` 方法映射；移除旧的混合职责接口
    - _Requirements: 1.4, 3.1, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 11.1, 11.2, 11.5, 11.6, 11.7, 12.1, 12.2, 12.5, 12.6, 12.7, 13.1, 13.3, 13.4_
  - [x] 8.5 删除已被拆分的旧控制器 `TestStoreController` 与 `TestEventController`
    - 确认 Redis 演示、远程调用演示、事件发布演示已分别迁移到对应新控制器后，删除旧文件
    - _Requirements: 7.3_

- [x] 9. 重构 User_Service 的 OperationLogListener
  - [x] 9.1 重构 `com.uoquo.listener.OperationLogListener`（User_Service），对齐统一监听模式
    - 保留 `@Component` 注解，类位于 `com.uoquo.listener` 包
    - 实现 `onAppEvent(AppEvent<?> event)` 兜底方法（`@EventListener`）：try-catch 包裹 `JsonUtil.serialize(event)`，成功时 INFO 日志记录 businessType、operationType、序列化内容；异常时 ERROR 日志记录，正常返回不抛出
    - 实现 `onRemoteEvent(RemoteEvent<?> event)` 方法（`@EventListener`）：记录 originService、destinationService（null 或 `**` 时以 `[BROADCAST]` 记录）、businessType、operationType；异常时 ERROR 日志，不抛出
    - 实现 `onAuthEvent(AppEvent<?> event)` 带 condition 过滤方法（`@EventListener(condition = "'AUTH'.equals(#event.businessType)")`）：仅当 businessType 为 `AUTH` 时被调用，记录 INFO 日志
    - 移除针对 `MessageEvent`、`CustomEvent` 的专用 `@EventListener` 方法
    - 确认 User_Service 中不存在其他声明 `@EventListener` 方法的组件
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 10.1, 10.3, 10.4, 10.5_

- [x] 10. 重构 Book_Service 的 OperationLogListener
  - [x] 10.1 重构 `com.uoquo.listener.OperationLogListener`（Book_Service），对齐统一监听模式
    - 保留 `@Component` 注解，类位于 `com.uoquo.listener` 包
    - 实现 `onAppEvent(AppEvent<?> event)` 兜底方法（`@EventListener`）：try-catch 包裹 `JsonUtil.serialize(event)`，成功时 INFO 日志；异常时 ERROR 日志，正常返回不抛出
    - 实现 `onRemoteEvent(RemoteEvent<?> event)` 方法（`@EventListener`）：记录 originService、destinationService（null 或 `**` 时以 `[BROADCAST]` 记录）、businessType、operationType；异常时 ERROR 日志，不抛出
    - 实现 `onAuthEvent(AppEvent<?> event)` 带 condition 过滤方法（`@EventListener(condition = "'AUTH'.equals(#event.businessType)")`）
    - 移除针对 `MessageEvent`、`CustomEvent` 的专用 `@EventListener` 方法
    - 确认 Book_Service 中不存在其他声明 `@EventListener` 方法的组件
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 10.2, 10.3, 10.4, 10.5_

- [x] 11. 检查点 — 确认两个服务完整编译并通过单元测试
  - 执行 `mvn -pl demo-service-user,demo-service-book compile`，确认零编译错误
  - 确认 `BookRemoteService` 的 Feign 配置（`name="demo-cloud-book"`, `path="/api/book"`）未被修改
  - 确认 Book_Service 的 `/store/book/name` 路径未变更
  - 如有问题，回到对应任务修复后再继续

- [x] 12. 编写 CustomEvent 序列化往返一致性属性测试
  - [x]* 12.1 在 `demo-service-user` 中创建属性测试 `CustomEventSerializationPropertyTest`
    - 文件路径：`demo-service-user/src/test/java/com/uoquo/user/event/CustomEventSerializationPropertyTest.java`
    - 使用 jqwik（`@Property(tries = 100)`）
    - 注释标注：`// Feature: service-refactor-remote-events, Property 1: CustomEvent 序列化往返一致`
    - 生成器 `eventTexts()`：覆盖空串、ASCII 字符串（1–50 字符）、中文字符串（1–20 字符）
    - 生成器 `destinationServices()`：覆盖 `null`、`"**"`、`"demo-cloud-book"`、`"demo-cloud-user"`、任意 ASCII 字符串
    - 属性断言：`restored` 非 null，且 businessType、operationType、operationStatus、destinationService 均与原始事件逐字段相等
    - **Property 1: CustomEvent 序列化往返一致**
    - **Validates: Requirements 14.2**
    - _Requirements: 14.1, 14.2_
  - [x]* 12.2 在同一测试类中添加非法 JSON 边界测试（edge case）
    - 方法 `deserializeInvalidJsonThrowsException()`：调用 `JsonUtil.deserialize("not-valid-json", CustomEvent.class)`，断言抛出 `Exception`
    - _Requirements: 14.3_

- [x] 13. 最终检查点 — 确认所有测试通过
  - 执行 `mvn -pl demo-service-user test`，确认属性测试与单元测试全部通过
  - 执行 `mvn -pl demo-service-book compile`，确认零编译错误
  - 如有失败，回到对应任务修复后再继续

## Notes

- 标注 `*` 的子任务为可选项，可在 MVP 阶段跳过
- 每个任务均引用了具体的需求条款，便于追溯
- 检查点任务（4、11、13）用于增量验证，确保每个阶段的改动不引入新的编译错误
- 两个服务统一使用 `cloud-core:1.1.0`；User_Service 从 1.0.6 升级，所有框架类型引用须在 1.1.0 类路径上确认可解析
- 两个服务均移除 `com.uoquo.filter` 与 `com.uoquo.interceptor` 包，与 Reference_App 基线保持一致
- `com.uoquo.user.model.dto` 子包必须存在，并提供 `UserInfoDto` 类；控制器方法返回类型使用 `ReturnData<XxxDto>` 形式
- Book_Service 的 `/store/book/name` 接口路径在整个重构过程中保持不变，保证 `BookRemoteService` Feign 调用可命中
- 属性测试使用 jqwik，与 `demo-app` 保持一致（项目已有 `.jqwik-database` 文件）

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "3.1", "3.4"] },
    { "id": 1, "tasks": ["1.2", "2.1", "2.2", "3.2", "3.3"] },
    { "id": 2, "tasks": ["2.3", "2.4", "5.1", "6.1"] },
    { "id": 3, "tasks": ["5.2", "5.3", "6.2", "6.3"] },
    { "id": 4, "tasks": ["7.1", "7.2", "8.1", "8.2", "8.3", "9.1", "10.1"] },
    { "id": 5, "tasks": ["8.4", "8.5"] },
    { "id": 6, "tasks": ["12.1", "12.2"] }
  ]
}
```
