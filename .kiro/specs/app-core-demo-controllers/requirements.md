# Requirements Document

## Introduction

demo-app 模块是一个 Spring Boot（Jakarta EE）单体应用，其唯一目的是演示 `app-core` 框架的能力。现有的 controller、listener 以及 user 的 service/mapper 层已经发生了偏移：多个文件引用了在 demo-app 类路径上并不存在的包（例如 `com.uoquo.user.bean`、`com.uoquo.web.common`、`com.uoquo.cloud.events`），用户列表接口引用了未定义的变量，而事件发布使用的是 Spring 的 `ApplicationEventPublisher` 和零散的 listener，而不是框架发布器。

本功能将重写 demo 的 controller 并精简 user 层，使其干净地展示 `app-core` 的四项能力：

1. 返回包装在框架响应对象中的各种常见数据类型。
2. 通过框架 Redis 工具进行基础 Redis 操作。
3. 通过框架发布器进行统一、集中的事件发布。
4. 对一个先前已序列化的事件进行事件重发（resend）。

所有事件发布都必须经过 `UoquoEventPublisher`，并且所有事件都必须由 `com.uoquo.listener` 包中唯一保留的 listener `OperationLogListener` 处理。废弃的 listener 包（`com.uoquo.demo.listener` 和 `com.uoquo.user.listener`）将被移除。

### 已核实的技术上下文

以下事实已从源码树以及 `app-core` jar 包中确认，并约束着设计：

- `com.uoquo.web.events.AppEvent<T>` 是一个**抽象**类，继承自 `ApplicationEvent`。它不能被直接实例化，必须使用具体的子类。它暴露了 `businessType`、`operationType`、`operationStatus`、`oldData`、`newData`、`retry`、`id` 以及相关的 getter/setter。
- `com.uoquo.web.events.UoquoEventPublisher` 暴露了 `publishEvent(Object)`。
- `OperationLogListener.onMessageEvent2(AppEvent<UserInfo> event)` 已经存在，并读取 `getNewData()`/`getOldData()`/`getOperationType()`。它可匹配 `AppEvent<UserInfo>` 的任意具体子类型。
- demo-app 仅依赖 `app-core` 以及 web/redis/mybatis 这些 starter。在所引用的 resend 代码片段中使用的 `com.uoquo.cloud.events.RemoteEvent`、`org.springframework.cloud.stream.function.StreamBridge` 和 `org.springframework.cloud.bus.BusConstants` 属于 spring-cloud-bus 技术栈，**不在** demo-app 的类路径上。因此 resend 需求将以适合单体应用的等价方式来满足（在本地通过框架发布器重新发布反序列化后的事件）。
- `AuthInfo` 仅存在于 demo-task 模块（`com.uoquo.scheduler.platform.model.pojo.AuthInfo`）中，而 demo-app 并不依赖该模块。demo-app 需要自己的 `AuthInfo` 类型。
- demo-app 可用的框架类：`com.uoquo.web.ReturnData`、`com.uoquo.annotation.web.IgnoreAuth`、`com.uoquo.utils.spring.RedisUtil`、`com.uoquo.mybatis.page.PageList` / `PageResult`、`com.uoquo.mybatis.page.PageHelper`、`com.uoquo.utils.json.JsonUtil`、`com.uoquo.utils.IDGenerator`。（注意：原始请求引用了 `com.uoquo.web.common.*`、`com.uoquo.web.handler.RedisUtil` 和 `com.uoquo.web.mybatis.page.*`；设计必须使用上面已核实的真实包名。）

## Glossary

- **Demo_App**：demo-app Spring Boot 单体模块，承载所有 controller、user 层以及保留的 listener。
- **User_Controller**：REST 控制器 `com.uoquo.user.controller.UserController`，精简之后的版本。
- **User_Info_Service**：接口 `com.uoquo.user.service.UserInfoService` 及其实现 `UserInfoServiceImpl`，精简之后的版本。
- **User_Info_Mapper**：MyBatis mapper `com.uoquo.user.mapper.UserInfoMapper` 及其 XML，精简之后的版本。
- **Response_Demo_Controller**：位于 `com.uoquo.demo.controller` 下的控制器，为每种被演示的返回类型暴露一个接口。
- **Redis_Demo_Controller**：位于 `com.uoquo.demo.controller` 下的控制器，暴露基础 Redis 操作。
- **Event_Demo_Controller**：位于 `com.uoquo.demo.controller` 下的控制器，集中处理事件发布与事件重发。
- **Event_Publisher**：框架发布器 `com.uoquo.web.events.UoquoEventPublisher`，以 `private UoquoEventPublisher eventPublisher;` 形式注入。
- **App_Event**：框架抽象事件基类 `com.uoquo.web.events.AppEvent<T>`。
- **User_Event**：`App_Event` 的具体子类，参数化为 `AppEvent<UserInfo>`，用于用户相关事件。
- **Auth_Event**：`App_Event` 的具体子类，参数化为 `AppEvent<AuthInfo>`，用于认证事件。
- **Auth_Info**：一个 demo-app 的 POJO，表示认证数据，包含字段 account、password、deviceInfo、deviceOs 和 deviceUa。
- **User_Info**：POJO `com.uoquo.user.model.pojo.UserInfo`，包含字段 id、userName、realName、phone、password、email 和 createTime。
- **Operation_Log_Listener**：保留的 listener `com.uoquo.listener.OperationLogListener`，包括其方法 `onMessageEvent2(AppEvent<UserInfo>)`。
- **Return_Data**：框架响应包装类 `com.uoquo.web.ReturnData<T>`。
- **Redis_Util**：框架 Redis 工具 `com.uoquo.utils.spring.RedisUtil`。
- **Page_List**：框架分页结果类型 `com.uoquo.mybatis.page.PageList<T>`。
- **Page_Helper**：框架分页助手 `com.uoquo.mybatis.page.PageHelper`。
- **Json_Util**：框架 JSON 工具 `com.uoquo.utils.json.JsonUtil`，提供 `serialize` 和 `deserialize`。
- **Change_Password_Param**：请求体 `com.uoquo.user.model.param.ChangePasswordParam`，包含字段 id、oldPassword 和 newPassword。

## Requirements

### 需求 1：将 User_Controller 精简为两个接口

**用户故事：** 作为框架 demo 的维护者，我希望 User_Controller 只暴露一个分页查询和一个修改密码操作，从而使用户控制器保持精简，并专注于演示 app-core。

#### 验收标准

1. THE User_Controller SHALL 恰好暴露两个带请求映射的接口：一个分页用户查询和一个修改密码操作。
2. 当 收到带有页码和每页大小的分页用户查询请求 时，THE User_Controller SHALL 返回一个包装了 User_Info 记录 Page_List 的 Return_Data。
3. 当 收到带有合法 Change_Password_Param 请求体的修改密码请求 时，THE User_Controller SHALL 调用 User_Info_Service 修改密码，并返回一个包装了布尔结果的 Return_Data。
4. THE User_Controller SHALL 在不引用任何未定义变量的情况下完成编译。
5. THE User_Controller SHALL 从包 `com.uoquo.web` 导入 Return_Data，从包 `com.uoquo.mybatis.page` 导入 Page_List。

### 需求 2：将 User_Info_Service 精简为两个操作

**用户故事：** 作为框架 demo 的维护者，我希望 User_Info_Service 只支持分页查询和修改密码，从而使 service 层与精简后的 controller 保持一致。

#### 验收标准

1. THE User_Info_Service SHALL 恰好声明两个操作：一个分页用户查询和一个修改密码。
2. 当 以查询 map、页码和每页大小调用分页查询操作 时，THE User_Info_Service SHALL 通过 Page_Helper 启动分页，并返回一个 User_Info 记录的 Page_List。
3. 当 以用户 id、旧密码和新密码调用修改密码操作，且存储的旧密码与所提供的旧密码匹配 时，THE User_Info_Service SHALL 更新所存储的密码并返回 true。
4. IF 用户 id 无法解析为一条已存储的 User_Info 记录，THEN THE User_Info_Service SHALL 返回 false，且不修改任何 User_Info 记录。
5. IF 所提供的旧密码与存储的密码不匹配，THEN THE User_Info_Service SHALL 返回 false，且不更新任何记录。

### 需求 3：将 User_Info_Mapper 精简为所需查询

**用户故事：** 作为框架 demo 的维护者，我希望 User_Info_Mapper 只声明两个 service 操作所使用的方法，从而使持久层保持精简并能干净地编译。

#### 验收标准

1. THE User_Info_Mapper SHALL 仅声明分页查询和修改密码操作所需要的方法。
2. THE User_Info_Mapper SHALL 使用 User_Info 的真实包 `com.uoquo.user.model.pojo` 来引用它。
3. THE User_Info_Mapper XML SHALL 仅映射 User_Info POJO 上存在的列。
4. THE User_Info_Mapper SHALL 在不引用包 `com.uoquo.user.bean` 或类型 `com.uoquo.web.mybatis.sensitive.SensitiveField` 的情况下完成编译。

### 需求 4：在修改密码成功时发布一个 User_Event

**用户故事：** 作为框架 demo 的维护者，我希望一次成功的修改密码能够发布一个携带受影响 User_Info 的 AppEvent，从而由保留的 Operation_Log_Listener 处理该变更。

#### 验收标准

1. 当 修改密码操作成功完成 时，THE User_Info_Service SHALL 通过 Event_Publisher 发布一个 User_Event。
2. THE User_Event SHALL 是 App_Event 的一个具体子类，参数化为 `AppEvent<UserInfo>`。
3. 当 修改密码操作发布一个 User_Event 时，THE User_Info_Service SHALL 将事件的 new data 设置为更新后的 User_Info 记录。
4. 当 一个 User_Event 被发布 时，THE Operation_Log_Listener SHALL 通过其方法 `onMessageEvent2(AppEvent<UserInfo>)` 处理该事件。
5. IF 修改密码操作返回 false，THEN THE User_Info_Service SHALL 不发布任何 User_Event。

### 需求 5：为每种被演示的返回类型暴露一个接口

**用户故事：** 作为框架 demo 的使用者，我希望每种常见返回类型都有一个包装在 Return_Data 中的接口，从而能够看到 app-core 如何序列化每种类型。

#### 验收标准

1. THE Response_Demo_Controller SHALL 暴露一个返回包装了 String 值的 Return_Data 的接口。
2. THE Response_Demo_Controller SHALL 暴露一个返回包装了 int 值的 Return_Data 的接口。
3. THE Response_Demo_Controller SHALL 暴露一个返回包装了 long 值的 Return_Data 的接口。
4. THE Response_Demo_Controller SHALL 暴露一个返回包装了 double 值的 Return_Data 的接口。
5. THE Response_Demo_Controller SHALL 暴露一个返回包装了 boolean 值的 Return_Data 的接口。
6. THE Response_Demo_Controller SHALL 暴露一个返回包装了 key 为 String、value 为 Object 的 Map 的 Return_Data 的接口。
7. THE Response_Demo_Controller SHALL 暴露一个返回包装了 User_Info 对象的 Return_Data 的接口。

### 需求 6：提供基础 Redis 操作

**用户故事：** 作为框架 demo 的使用者，我希望通过 Redis_Util 提供基础 Redis 操作的接口，从而能够看到 app-core 如何与 Redis 集成。

#### 验收标准

1. 当 收到带有 key 和 value 的 Redis set 请求 时，THE Redis_Demo_Controller SHALL 通过 Redis_Util 将该 value 存储到该 key 下，并返回一个包装了布尔结果的 Return_Data。
2. 当 收到带有 key 的 Redis get 请求 时，THE Redis_Demo_Controller SHALL 通过 Redis_Util 读取该 key 对应的 value，并返回一个包装了该 value 的 Return_Data。
3. 当 收到带有 key 的 Redis delete 请求 时，THE Redis_Demo_Controller SHALL 通过 Redis_Util 删除该 key，并返回一个包装了布尔结果的 Return_Data。
4. 当 收到带有 key 的 Redis exists 请求 时，THE Redis_Demo_Controller SHALL 通过 Redis_Util 报告该 key 是否存在，并返回一个包装了布尔结果的 Return_Data。
5. IF 某个 Redis 操作抛出异常，THEN THE Redis_Demo_Controller SHALL 记录该异常，并返回一个包装了 false 布尔结果的 Return_Data。
6. IF 某个 Redis 操作抛出异常，且记录该异常本身也失败，THEN THE Redis_Demo_Controller SHALL 返回一个包装了 false 布尔结果的 Return_Data。
7. 当 通过 Redis_Demo_Controller 将一个 value 存储到某个 key 下，并在过期前重新读取 时，THE Redis_Demo_Controller SHALL 返回所存储的 value（往返属性）。

### 需求 7：模拟登录成功事件

**用户故事：** 作为框架 demo 的使用者，我希望有一个接口能够模拟一个携带 Auth_Info 的登录成功事件，从而能够看到一个认证事件通过框架发布器被发布。

#### 验收标准

1. 当 收到登录成功模拟请求 时，THE Event_Demo_Controller SHALL 通过 Event_Publisher 发布一个 Auth_Event。
2. THE Auth_Event SHALL 是 App_Event 的一个具体子类，参数化为 `AppEvent<AuthInfo>`。
3. 当 登录成功模拟发布一个 Auth_Event 时，THE Event_Demo_Controller SHALL 将事件的 business type 设置为 `AUTH`、operation type 设置为 `LOGIN`、operation status 设置为一个成功状态。
4. 当 登录成功模拟发布一个 Auth_Event 时，THE Event_Demo_Controller SHALL 将事件的 new data 设置为一个携带所模拟账号的 Auth_Info 对象。
5. 当 登录成功模拟完成 时，THE Event_Demo_Controller SHALL 返回一个包装了确认 String 的 Return_Data。
6. IF 发布 Auth_Event 失败，THEN THE Event_Demo_Controller SHALL 仍然返回一个包装了确认 String 的 Return_Data。

### 需求 8：模拟登录失败事件

**用户故事：** 作为框架 demo 的使用者，我希望有一个接口能够模拟一个登录失败事件，从而能够看到一个失败状态的认证事件通过框架发布器被发布。

#### 验收标准

1. 当 收到登录失败模拟请求 时，THE Event_Demo_Controller SHALL 通过 Event_Publisher 发布一个 Auth_Event。
2. 当 登录失败模拟发布一个 Auth_Event 时，THE Event_Demo_Controller SHALL 将事件的 business type 设置为 `AUTH`、operation type 设置为 `LOGIN`、operation status 设置为一个失败状态。
3. 当 登录失败模拟发布一个 Auth_Event 时，THE Event_Demo_Controller SHALL 将事件的 new data 设置为一个携带所模拟账号的 Auth_Info 对象。
4. 当 登录失败模拟完成 时，THE Event_Demo_Controller SHALL 返回一个包装了确认 String 的 Return_Data。
5. IF 发布 Auth_Event 失败，THEN THE Event_Demo_Controller SHALL 仍然返回一个包装了确认 String 的 Return_Data，且不重试该发布。

### 需求 9：序列化并重发一个 App_Event

**用户故事：** 作为框架 demo 的使用者，我希望有一个接口能够将一个 User_Event 序列化为 String，然后将该 String 作为事件重发，从而能够看到通过框架进行的运维式手动重投递。

#### 验收标准

1. 当 收到重发模拟请求 时，THE Event_Demo_Controller SHALL 通过 Json_Util 将一个 User_Event 序列化为 String。
2. 当 Event_Demo_Controller 已将 User_Event 序列化为 String 时，THE Event_Demo_Controller SHALL 通过 Json_Util 将该 String 反序列化回一个 User_Event。
3. 当 Event_Demo_Controller 重发一个反序列化后的 User_Event 时，THE Event_Demo_Controller SHALL 在发布前将事件的 retry 标志设置为 true。
4. 当 Event_Demo_Controller 重发一个反序列化后的 User_Event 时，THE Event_Demo_Controller SHALL 通过 Event_Publisher 发布该事件。
5. 对于所有 User_Event 对象，将事件序列化为 String 然后再将该 String 反序列化 SHALL 产生一个 User_Event，其 business type、operation type、operation status 和 new data 都与原始事件相等（往返属性）。
6. 当 重发模拟完成 时，THE Event_Demo_Controller SHALL 返回一个包装了确认 String 的 Return_Data。

### 需求 10：专一使用框架发布器

**用户故事：** 作为框架 demo 的维护者，我希望每一个发布事件的组件都使用 Event_Publisher，从而使该 demo 一致地展示统一的 app-core 事件发布。

#### 验收标准

1. THE User_Info_Service SHALL 以 `private UoquoEventPublisher eventPublisher;` 的形式注入 Event_Publisher 用于事件发布。
2. THE Event_Demo_Controller SHALL 以 `private UoquoEventPublisher eventPublisher;` 的形式注入 Event_Publisher 用于事件发布。
3. THE Demo_App SHALL 仅通过 Event_Publisher 发布事件，而不通过 Spring 的 `ApplicationEventPublisher`。

### 需求 11：移除废弃的 listener 包

**用户故事：** 作为框架 demo 的维护者，我希望移除废弃的 listener 包，从而使 Operation_Log_Listener 成为该 demo 中唯一的事件处理器。

#### 验收标准

1. THE Demo_App SHALL 移除包 `com.uoquo.demo.listener` 及其中的 `ExampleEventListener`。
2. THE Demo_App SHALL 移除包 `com.uoquo.user.listener` 及其中的 `PasswordChangedEventListener`。
3. THE Demo_App SHALL 保留包 `com.uoquo.listener` 及其中的 Operation_Log_Listener。
4. THE Demo_App SHALL 移除此前由 User_Info_Service 发布的内部 `PasswordChangedEvent` 类型。

### 需求 12：Demo_App 可编译并可构建

**用户故事：** 作为框架 demo 的维护者，我希望整个 demo-app 模块在重写之后能够编译，从而使该演示能够运行。

#### 验收标准

1. THE Demo_App SHALL 在所有 controller、user 层以及保留的 listener 中均无未解析的 import 错误的情况下完成编译。
2. WHERE 某个 controller 接口被设计为无需认证即可访问，THE Demo_App SHALL 使用 `com.uoquo.annotation.web.IgnoreAuth` 注解该接口或该控制器。
3. THE Demo_App SHALL 在 demo-app 模块内部定义一个 Auth_Info 类型，从而使 Auth_Event 无需依赖 demo-task 模块即可编译。
