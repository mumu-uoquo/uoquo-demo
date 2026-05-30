# demo-app

基于 `com.uoquo:app-core` 的单体应用示例，演示框架的统一响应、Redis 操作、事件发布/监听等能力。

## 技术栈

- JDK 21、Spring Boot 3.5.x（由父 POM `com.uoquo:dependencies` 统一管理版本）
- app-core（uoquo 框架核心）
- MySQL（mysql-connector-j）+ Druid 连接池
- MyBatis（mybatis-spring-boot-starter）
- Redis（spring-boot-starter-data-redis，支持单点/哨兵/集群）
- springdoc-openapi（Swagger 3 UI）

## 环境依赖

- MySQL：数据库 `uoquo_health`
- Redis：单点 / 哨兵 / 集群任选其一（优先级：哨兵 > 集群 > 单点）

连接信息通过环境变量或 `application.yml` 配置，关键默认值：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVICE_PORT` | `7001` | 服务端口 |
| `SERVICE_PATH` | `/api/app` | 全局上下文路径（context-path） |
| `DB_HOST` | `localhost` | MySQL 主机，端口固定 3306 |
| `DB_USER` / `DB_PSWD` | `admin` / `Admin@l23` | MySQL 账号密码 |
| `REDIS_HOST` | `dev.xuziu.com` | Redis 单点主机，端口 6379 |
| `REDIS_PASSWORD` | `xuziu` | Redis 密码 |

> 所有接口都位于上下文路径之下。例如返回类型演示接口的完整路径为 `/api/app/demo/response/string`。

## 运行

```bash
# 开发环境（dev：开启 Swagger）
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 或打包后运行
mvn clean package
java -jar target/demo-app.jar --spring.profiles.active=dev
```

启动类：`com.uoquo.main.DemoAppApplication`。

### API 文档（Swagger）

`dev` 环境默认开启（`prod` 默认关闭）。启动后访问：

```
http://localhost:7001/api/app/swagger-ui.html
```

## 接口一览

所有演示接口均标注 `@IgnoreAuth(all = true)`，免认证即可访问。

### 1. 返回类型演示 — `ResponseDemoController`（`/demo/response`）

演示框架统一响应对象 `ReturnData<T>` 对各类数据的包装与序列化。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/demo/response/string` | 返回 String |
| GET | `/demo/response/int` | 返回 int |
| GET | `/demo/response/long` | 返回 long |
| GET | `/demo/response/double` | 返回 double |
| GET | `/demo/response/boolean` | 返回 boolean |
| GET | `/demo/response/map` | 返回 `Map<String, Object>` |
| GET | `/demo/response/user` | 返回 `UserInfoDto` 对象 |

### 2. Redis 操作演示 — `RedisDemoController`（`/demo/redis`）

通过框架工具 `RedisUtil` 演示基础读写。采用嵌套 try-catch，保证演示接口不会因 Redis 异常逃逸为 5xx。

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| POST | `/demo/redis/set` | `key`, `value` | 写入（默认 TTL 300 秒） |
| GET | `/demo/redis/get` | `key` | 读取 |
| DELETE | `/demo/redis/delete` | `key` | 删除 |
| GET | `/demo/redis/exists` | `key` | 是否存在 |

### 3. 事件演示 — `EventDemoController`（`/demo/event`）

通过框架发布器 `UoquoEventPublisher` 发布事件，由 `OperationLogListener` 统一消费。

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| POST/GET | `/demo/event/login-success` | `account` | 发布登录成功事件（AUTH/LOGIN/SUCCESS） |
| POST/GET | `/demo/event/login-failure` | `account` | 发布登录失败事件（AUTH/LOGIN/FAIL） |
| POST/GET | `/demo/event/resend` | — | 事件序列化后反序列化重发（`retry=true`） |

### 4. 用户管理 — `UserController`（`/user`）

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| GET | `/user/list` | `userName`(可选), `page`(默认1), `pageSize`(默认10) | 分页查询，返回 `PageResult<UserInfoDto>` |
| POST | `/user/changePassword` | JSON: `ChangePasswordParam` | 修改密码 |

修改密码请求体示例：

```json
{
  "id": "user123",
  "oldPassword": "old123",
  "newPassword": "new456"
}
```

## 事件机制说明

- **发布**：使用框架的 `UoquoEventPublisher.publishEvent(AppEvent)` 统一发布，事件携带 `businessType` / `operationType` / `status` 等元信息。
- **监听**：`com.uoquo.listener.OperationLogListener` 作为统一处理器，按事件类型分别记录日志：
  - `onAUTHEvent`：仅处理 `businessType=AUTH` 的认证事件（SpEL 条件过滤）。
  - `onMessageEvent2`：处理 `AppEvent<UserInfo>` 用户事件。
  - `onAppEvent`：兜底处理所有 `AppEvent`。
- 监听器默认同步执行；事件发布时线程池已介入，无需额外 `@Async`。

## 项目结构

```
demo-app/
├── pom.xml
└── src/main/java/com/uoquo/
    ├── main/
    │   ├── DemoAppApplication.java          # 启动类
    │   └── config/                          # 应用 / 异步 / 数据源 / OpenAPI 等配置
    ├── demo/
    │   ├── controller/
    │   │   ├── ResponseDemoController.java   # 返回类型演示
    │   │   ├── RedisDemoController.java      # Redis 操作演示
    │   │   └── EventDemoController.java      # 事件发布 / 重发演示
    │   └── model/pojo/AuthInfo.java
    ├── user/
    │   ├── controller/UserController.java    # 用户分页查询 / 修改密码
    │   ├── service/                          # UserInfoService 及实现
    │   ├── mapper/                           # MyBatis Mapper + XML
    │   └── model/                            # dto / param / pojo
    └── listener/
        └── OperationLogListener.java         # 统一事件监听器
```

## 测试

项目使用 JUnit 5 + jqwik（属性测试）+ Mockito。

```bash
mvn test
```
