# Implementation Plan: app-core-demo-controllers

## Overview

重写 demo-app 的 controller 层并精简 user 层，使其干净、可编译地演示 `app-core` 框架的四项能力：统一响应类型、Redis 工具、集中事件发布，以及事件序列化重发。实现语言为 **Java**（设计文档已使用真实 Java 包名与 API），属性测试使用 **jqwik**，mock 使用 **Mockito**。

任务按增量顺序组织：先建立数据模型与事件子类、补齐测试依赖、清除无法编译的废弃代码；再自底向上重建持久层、服务层、各 demo controller；最后统一事件发布器接线并验证整模块编译与测试。

## Tasks

- [x] 1. 建立数据模型、事件子类、测试依赖，并移除废弃代码
  - [x] 1.1 创建 AuthInfo POJO
    - 新增 `com.uoquo.demo.model.pojo.AuthInfo`，字段 `account`、`password`、`deviceInfo`、`deviceOs`、`deviceUa`
    - 提供无参构造函数与 getter/setter（供 JSON 反序列化）
    - _Requirements: 12.3_

  - [x] 1.2 创建 UserEvent 具体事件子类
    - 新增 `com.uoquo.user.event.UserEvent extends com.uoquo.web.events.AppEvent<UserInfo>`
    - 提供无参构造函数（支持 JSON 往返）与 `(businessType, operationType, operationStatus)` 构造函数（调用 `super(...)`）
    - _Requirements: 4.2, 9.2_

  - [x] 1.3 创建 AuthEvent 具体事件子类
    - 新增 `com.uoquo.demo.event.AuthEvent extends com.uoquo.web.events.AppEvent<AuthInfo>`
    - 提供无参构造函数与 `(businessType, operationType, operationStatus)` 构造函数
    - _Requirements: 7.2_

  - [x] 1.4 在 demo-app 的 pom.xml 中加入测试框架依赖
    - 以 `test` scope 引入 `org.springframework.boot:spring-boot-starter-test`、`net.jqwik:jqwik`（JUnit 5 平台）、`org.mockito:mockito-core`
    - 确保 JUnit 5 平台可运行 jqwik 属性测试
    - _Requirements: 测试基础设施（支撑 Property 1–5）_

  - [x] 1.5 移除废弃的 controller、listener 包与过期测试
    - 删除 `com.uoquo.demo.controller` 下的 `ExampleController`、`TestController`、`TestEventController`、`TestStoreController`
    - 删除整个 `com.uoquo.demo.listener` 包（含 `ExampleEventListener`）
    - 删除整个 `com.uoquo.user.listener` 包（含 `PasswordChangedEventListener`）
    - 删除/更新引用已移除代码的过期测试类（如 `UserControllerTest`），保证模块仍可编译
    - 保留 `com.uoquo.listener.OperationLogListener`
    - _Requirements: 11.1, 11.2, 11.3_

- [x] 2. 精简持久层（User_Info_Mapper）
  - [x] 2.1 精简 UserInfoMapper 接口
    - 仅声明 `selectBySearch(Map)`、`selectByPrimaryKey(String)`、`updateByPrimaryKey(UserInfo)`
    - 使用真实包 `com.uoquo.user.model.pojo.UserInfo`，不引用 `com.uoquo.user.bean` 或 `com.uoquo.web.mybatis.sensitive.SensitiveField`
    - _Requirements: 3.1, 3.2, 3.4_

  - [x] 2.2 精简 UserInfoMapper.xml
    - `resultMap` 与参数类型改为 `com.uoquo.user.model.pojo.UserInfo`
    - `resultMap` 仅映射 POJO 真实存在的列：`id`、`user_name`、`real_name`、`phone`、`password`、`email`、`create_time`
    - 仅保留 `selectBySearch`、`selectByPrimaryKey`、`updateByPrimaryKey` 三段，删除其余引用不存在列/类型的语句
    - _Requirements: 3.1, 3.3_

- [x] 3. 实现用户服务层
  - [x] 3.1 定义精简后的 UserInfoService 接口
    - 恰好声明两个操作：`PageList<UserInfo> getListByPage(Map, int page, int pageSize)` 与 `boolean changePassword(String id, String oldPassword, String newPassword)`
    - _Requirements: 2.1_

  - [x] 3.2 实现 UserInfoServiceImpl（使用框架发布器）
    - 注入 `private UoquoEventPublisher eventPublisher;`，移除 Spring `ApplicationEventPublisher` 与内部 `PasswordChangedEvent` 类
    - `getListByPage`：`PageHelper.startPage(page, pageSize)` 后调用 `selectBySearch` 返回 `PageList<UserInfo>`
    - `changePassword`：用户不存在或旧密码不匹配返回 false 且不更新；匹配则更新密码，更新成功后构造 `UserEvent`（newData=更新后的 UserInfo）经 `eventPublisher.publishEvent` 发布并返回 true；更新未成功不发布事件
    - _Requirements: 2.2, 2.3, 2.4, 2.5, 4.1, 4.3, 4.5, 10.1, 11.4_

  - [x]* 3.3 编写属性测试：修改密码校验逻辑
    - **Property 1: 修改密码校验逻辑**
    - **Validates: Requirements 2.3, 2.4, 2.5**
    - 使用 Mockito mock mapper；oldPassword 生成器以一定概率等于存储密码以覆盖 true/false 两条路径；至少 100 次迭代
    - 测试类注释标注：`// Feature: app-core-demo-controllers, Property 1: ...`

  - [x]* 3.4 编写属性测试：成功时发布携带更新数据的 UserEvent
    - **Property 2: 修改密码成功才发布携带更新数据的 User_Event**
    - **Validates: Requirements 4.1, 4.3, 4.5**
    - mock publisher，断言成功时恰好发布一个 newData 等于更新后 UserInfo 的 UserEvent，失败时零发布

  - [ ]* 3.5 编写集成测试：分页查询委托
    - 验证 `getListByPage` 经 `PageHelper.startPage` 启动分页并返回 `PageList<UserInfo>`
    - _Requirements: 2.2_

- [x] 4. 重写 UserController
  - [x] 4.1 实现 UserController（分页查询 + 修改密码）
    - 恰好两个带请求映射的接口：`GET /user/list` 返回 `ReturnData<PageList<UserInfo>>`，`POST /user/changePassword` 接收 `@Valid ChangePasswordParam` 返回 `ReturnData<Boolean>`
    - 类级 `@IgnoreAuth(all = true)`；从 `com.uoquo.web` 导入 `ReturnData`，从 `com.uoquo.mybatis.page` 导入 `PageList`；不引用任何未定义变量
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 12.2_

  - [ ]* 4.2 编写集成测试：UserController 接口
    - 通过 MockMvc 验证分页查询与修改密码端到端返回 ReturnData 包装结果
    - _Requirements: 1.2, 1.3_

- [~] 5. 检查点 - 确保测试通过
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. 实现 ResponseDemoController
  - [x] 6.1 实现 7 个返回类型演示接口
    - `GET /demo/response/{string|int|long|double|boolean|map|user}`，分别返回 `ReturnData<String|Integer|Long|Double|Boolean|Map<String,Object>|UserInfo>`
    - 全部 `@IgnoreAuth(all = true)`，每个方法包装相应类型的示例值
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 12.2_

  - [ ]* 6.2 编写单元测试：返回类型接口
    - 对每种返回类型以少量示例断言类型与示例值（节制，避免冗余）
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_

- [x] 7. 实现 RedisDemoController
  - [x] 7.1 实现 set/get/delete/exists 接口（含嵌套异常处理）
    - `POST /demo/redis/set`、`GET /demo/redis/get`、`DELETE /demo/redis/delete`、`GET /demo/redis/exists`，经 `RedisUtil` 操作并返回 `ReturnData`
    - 异常时记录日志并返回 `ReturnData<>(false)`；内层 catch 吞掉日志记录自身异常仍返回 false
    - 全部 `@IgnoreAuth(all = true)`
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 12.2_

  - [x]* 7.2 编写属性测试：Redis 存取往返
    - **Property 5: Redis 存取往返**
    - **Validates: Requirements 6.7**
    - 非空 key（加随机前缀避免冲突）+ 任意 value；存入后过期前读取应返回所存 value；使用内嵌/测试 Redis 或内存级 fake

  - [ ]* 7.3 编写单元测试：Redis 异常处理
    - mock `RedisUtil` 抛异常，断言返回 `ReturnData<>(false)`；覆盖日志记录本身失败的内层 catch
    - _Requirements: 6.5, 6.6_

- [x] 8. 实现 EventDemoController
  - [x] 8.1 实现 login-success、login-failure、resend 接口
    - 注入 `private UoquoEventPublisher eventPublisher;`，全部 `@IgnoreAuth(all = true)`
    - `POST /demo/event/login-success`：发布 `AuthEvent(AUTH, LOGIN, SUCCESS)`，newData=AuthInfo(account)，返回确认 String；发布失败仍返回确认
    - `POST /demo/event/login-failure`：发布 `AuthEvent(AUTH, LOGIN, FAIL)`，newData=AuthInfo(account)，返回确认 String；发布失败仍返回确认且不重试
    - `POST /demo/event/resend`：`JsonUtil.serialize` → `JsonUtil.deserialize(json, UserEvent.class)` → `setRetry(true)` → `eventPublisher.publishEvent`，返回确认 String
    - _Requirements: 7.1, 7.3, 7.5, 8.1, 8.2, 8.4, 9.3, 9.4, 9.6, 10.2, 12.2_

  - [x]* 8.2 编写属性测试：认证模拟事件携带账号
    - **Property 3: 认证模拟事件携带所模拟账号**
    - **Validates: Requirements 7.4, 8.3**
    - account 生成器覆盖空串、ASCII、非 ASCII；mock publisher 捕获事件断言 newData.account 等于输入

  - [x]* 8.3 编写属性测试：UserEvent 序列化往返
    - **Property 4: User_Event 序列化往返**
    - **Validates: Requirements 9.1, 9.2, 9.5**
    - 随机 businessType/operationType/operationStatus + 随机 UserInfo newData；序列化再反序列化后四项相等

  - [ ]* 8.4 编写单元测试：事件发布失败处理
    - mock publisher 抛异常，断言 login-success / login-failure 仍返回确认 String（失败模拟不重试）
    - _Requirements: 7.6, 8.5_

- [x] 9. 集成与接线
  - [x] 9.1 统一事件发布器接线并验证整模块编译
    - 确认全模块仅通过 `UoquoEventPublisher` 发布事件，无 Spring `ApplicationEventPublisher` 残留，`OperationLogListener` 为唯一事件处理器
    - 执行 `mvn -pl demo-app compile` 确认无未解析 import 错误
    - _Requirements: 10.3, 11.3, 12.1_

  - [ ]* 9.2 编写集成测试：事件经发布器到达 OperationLogListener
    - 在 Spring 上下文中发布 `UserEvent`，验证 `OperationLogListener.onMessageEvent2(AppEvent<UserInfo>)` 被分发处理
    - _Requirements: 4.4_

- [~] 10. 最终检查点 - 确保模块编译且所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- 标记 `*` 的子任务为可选（单元/属性/集成测试），可为更快的 MVP 跳过；核心实现任务不标记可选。
- 每个任务都引用了 design.md / requirements.md 中具体的需求子条款以保证可追溯。
- 5 个属性测试逐一对应设计文档的 5 个 Correctness Properties，且每个属性用单个属性测试实现、至少 100 次迭代。
- 实现语言为 Java；属性测试库为 jqwik，mock 为 Mockito。
- 检查点用于增量验证；最终检查点覆盖整模块编译（需求 12.1）与全部测试。

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3", "1.4", "1.5", "2.1", "2.2", "3.1"] },
    { "id": 1, "tasks": ["3.2", "4.1", "6.1", "7.1", "8.1"] },
    { "id": 2, "tasks": ["3.3", "3.4", "3.5", "4.2", "6.2", "7.2", "7.3", "8.2", "8.3", "8.4", "9.1"] },
    { "id": 3, "tasks": ["9.2"] }
  ]
}
```
