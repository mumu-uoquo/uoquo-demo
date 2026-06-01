# 简介

`demo-task` 是基于 uoquo 框架的**定时任务调度服务**，属于非 Web 类型 Spring Boot 应用，主要负责周期性的后台任务执行。

---

# 模块定位

| 项目 | 说明 |
|------|------|
| 服务名称 | `scheduler-center` |
| 应用编码 | `01`（十六进制，最大 0xFF） |
| 节点编码 | `1`（十六进制，最大 0xF） |
| Web 类型 | **none**（不启动内嵌 Web 容器） |
| 应用类型 | 纯后台任务服务 |

---

# 技术栈

| 技术 | 用途 |
|------|------|
| Spring Boot | 基础框架 |
| Spring Scheduling (`@Scheduled`) | 定时任务调度 |
| MyBatis + Druid | 数据持久层与连接池 |
| Redis（Lettuce 客户端） | 缓存支持（哨兵 / 集群 / 单点） |
| uoquo app-core | 框架核心（`ServiceApplication`、`@Sensitive`、`JsonUtil` 等） |

---

# 项目结构

```
demo-task/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/uoquo/scheduler/
    │   │   ├── main/
    │   │   │   ├── SchedulerApplication.java          # 启动类
    │   │   │   └── config/
    │   │   │       ├── ApplicationConfig.java         # 应用配置（预留负载均衡）
    │   │   │       └── DataSourcePrimaryConfig.java   # 主数据源配置
    │   │   ├── common/
    │   │   │   ├── BusinessOperationEnum.java         # 业务操作枚举
    │   │   │   └── BusinessTypeEnum.java              # 业务类型枚举
    │   │   └── platform/
    │   │       ├── model/
    │   │       │   ├── dto/UserInfoDto.java           # 用户信息 DTO（含脱敏注解）
    │   │       │   └── pojo/AuthInfo.java             # 认证事件信息 POJO
    │   │       └── task/DemoTask.java                 # 示例定时任务
    │   └── resources/
    │       ├── application.yml                        # 主配置
    │       ├── application-dev.yml                    # 开发环境配置
    │       └── logback-custom.xml                     # 日志配置
    └── test/
        └── java/com/uoquo/test/
            └── JsonTest.java                          # JSON 反序列化测试
```

---

# 核心功能

## 1. 定时任务

模块内建 `DemoTask` 示例任务，通过 `@Scheduled` 注解驱动：

```java
@Component
public class DemoTask {
    @Scheduled(cron = "0/10 * * * * ?")
    public void demoTask() {
        log.info("demoTask");
    }
}
```

- 每 **10 秒** 执行一次
- `@EnableScheduling` 已在父类 `ServiceApplication` 中开启
- 可作为开发模板，按需扩展更多任务

## 2. 启动生命周期

`SchedulerApplication` 注册了 5 个 Spring Boot 应用事件监听器：

| 事件 | 触发时机 |
|------|----------|
| ApplicationStartingEvent | 系统启动时（logback 未初始化） |
| ApplicationEnvironmentPreparedEvent | 加载完 properties 后 |
| ApplicationPreparedEvent | 启动过程中多次执行 |
| ApplicationStartedEvent | main 运行完成后（ApplicationRunner.run 前） |
| ApplicationReadyEvent | 系统启动完毕（ApplicationRunner.run 后） |

> 目前监听器仅作日志输出，预留后续扩展。

## 3. 数据脱敏

`UserInfoDto` 使用 uoquo 框架的 `@Sensitive` 注解进行 JSON 序列化时自动脱敏：

| 字段 | 脱敏类型 | 示例 |
|------|----------|------|
| `realName` | `NAME` | 张三 → 张\* |
| `phone` | `PHONE` | 13812345678 → 138\*\*\*\*5678 |
| `email` | `EMAIL` | test@uoquo.com → t\*\*\*@uoquo.com |

---

# 业务枚举

## BusinessOperationEnum（业务操作）

字典编码范围 `010xxx`，用于标识用户认证相关操作：

| 枚举值 | 编码 | 含义 |
|--------|------|------|
| UNKNOWN | 000000 | 未知 |
| LOGIN | 010001 | 登录 |
| REFRESH_TOKEN | 010003 | 刷新令牌 |
| LOGOUT | 010005 | 登出 |
| REGISTER | 010007 | 注册 |

## BusinessTypeEnum（业务类型）

字典编码范围 `009xxx`：

| 枚举值 | 编码 | 含义 |
|--------|------|------|
| UNKNOWN | 000000 | 未知 |
| AUTH | 009005 | 认证 |
| SYSTEM | 009009 | 系统 |
| ACCOUNT | 009010 | 账户 |
| INSTITUTE | 009020 | 机构 |
| MESSAGE | 009030 | 消息 |
| SSE | 009901 | SSE 信息（内置，不维护字典表） |

两个枚举均支持 `getByCode(String)` 和 `getTextByCode(String)` 方法反向查询。

---

# 配置说明

## 数据源

使用 **Druid** 连接池 + **MyBatis** 框架，配置主数据源（`@Primary`）：

- 配置前缀：`spring.datasource.druid.primary`
- 连接池参数：initial-size=2, max-active=10, min-idle=1
- 慢 SQL 记录：100ms 阈值
- Mapper 扫描包：`com.uoquo.**.mapper`
- 架构上预留了多数据源扩展能力

## Redis

支持三种模式，优先级：**哨兵 > 集群 > 单点**：

| 模式 | 配置方式 |
|------|----------|
| 哨兵 | `spring.redis.sentinel.*` |
| 集群 | `spring.redis.cluster.*` |
| 单点 | `spring.redis.host/port` |

Lettuce 连接池：max-active=16, max-idle=8, min-idle=4

## 线程池

后台任务线程池配置：

| 参数 | 值 |
|------|-----|
| core-pool-size | 8 |
| max-pool-size | 16 |
| keep-alive-seconds | 60 |
| queue-capacity | 1024 |

## 环境变量

所有关键配置均支持环境变量覆盖（如 `${DB_HOST:dev.xuziu.com}`），适配容器化部署场景。

---

# 构建与部署

## 瘦身打包

模块采用**瘦身模式**打包：依赖 jar 外置到 `lib` 目录，应用 jar 不包含依赖：

```shell
# Maven 构建
mvn clean package -DskipTests

# 运行（需确保 lib 目录与 jar 同级）
java -Dloader.path=lib -jar demo-task.jar
```

**构建插件**：
- `maven-dependency-plugin` — 将依赖拷贝至 `lib/`
- `maven-jar-plugin` — 配置 Class-Path manifest
- `spring-boot-maven-plugin` — `layout=ZIP, includes=nothing`

## 环境切换

通过 Spring Profile 切换环境：

```shell
java -Dloader.path=lib -jar demo-task.jar --spring.profiles.active=dev
```

---

# 测试

`JsonTest` 提供两个 JSON 反序列化单元测试：

1. **testJson()** — 验证泛型嵌套反序列化
   - 测试 `ReturnData<PageResult<UserInfoDto>>` 类型解析
   - 使用 `TypeToken.getParameterized()` 构建泛型类型

2. **testJson2()** — 验证 `RemoteEvent` 多态反序列化
   - 测试 `RemoteEvent<AuthInfo>` 类型解析
   - 验证 `@JsonTypeInfo` 多态机制，JSON 中必须包含 `@type` 属性

---

# 依赖

```xml
<dependency>
    <groupId>com.uoquo</groupId>
    <artifactId>app-core</artifactId>
    <version>1.1.0</version>
</dependency>
```

主要传递依赖：`spring-boot-starter`、`spring-boot-starter-validation`、`spring-boot-starter-data-redis`、`mybatis-spring-boot-starter`、`druid-spring-boot-starter`、`mysql-connector-j`。