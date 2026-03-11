# FlowTest V2 使用手册

这份手册面向第一次接入 `flowtest-v2` 的用户，目标是让你从 0 到 1 完成集成，并写出第一条可运行的场景测试。

当前 `v2` 的核心设计是 4 个概念：

- `given`：准备前置数据，可选
- `observe`：声明要观测的表或实体，**可选**（框架会从 `then(...)` 自动推导）
- `when`：执行业务动作
- `then`：**唯一的验证入口**，断言结果、数据变化和 fixture 状态

如果你只记一条规则，记这条：**`then(...)` 里引用的资源会被自动观测**，你只需要在需要 route 条件或动态表参数时才显式写 `observe(...)`。

## 1. 适用范围

当前 `v2` 已支持：

- Java 8
- JUnit 5
- TestNG
- Spring Boot 自动装配
- 普通单表场景
- 分库分表场景
- 动态表名
- 多数据源
- MyBatis-Plus 元数据识别
- **自动数据填充**：fixture 实例在 trait 应用前自动填入随机数据（基于 Instancio）
- **Fixture 实体自动注册**：`given(g -> g.fixture(...))` 声明的实体类会自动注册到 JDBC 元数据
- **观测自动推导**：`then(...)` 中引用的 table/entity/fixture 会自动注册为观测资源
- **动态表路由自动推导**：`observe(o -> o.entity(handle))` 可自动从 fixture 实例的动态表属性推导 `TableRouteScope`

当前限制：

- `CleanupPolicy.ROLLBACK` 需要外部事务边界，默认不要使用
- 自动推导 `TableRouteScope` 的前提是 trait 已设置动态表路由属性（如 `bucket`），否则需要手动 `.dynamicTableBy(...)`

推荐默认 cleanup：

- 只有新增数据：`DELETE_INSERTED`
- 会修改/删除存量数据：`RESTORE_BEFORE_IMAGE`

### 1.1 场景与能力对照

| 概念 | 它解决的问题 | 典型特征 | 对应 API | 什么时候用 |
| --- | --- | --- | --- | --- |
| `act-only` | 测试前是否需要准备数据 | 没有 `given(...)`，数据只在 `when(...)` 中产生或变化 | `.when(...).then(...)` | 只关心 act 产生的新数据或存量变化 |
| `混合场景` | 测试前是否需要准备数据 | 有 `given(...)`，同时观察 fixture 和 act 产生的数据 | `given(...).when(...).then(...)` | 先有前置数据，再执行 act 并校验变化 |
| `分库分表` | SQL 如何合法路由 | 快照、diff、cleanup 的 SQL 必须带分片条件 | `.route(...)`、`RouteScope` | 中间件要求 SQL 带 `tenant_id`、`user_id` 等路由字段 |
| `动态表名` | 运行时实际查哪张物理表 | 逻辑表固定，物理表会变成 `table_a`、`table_b` 之类 | `.dynamicTableBy(...)`、`TableRouteScope`、`@JdbcDynamicTable` | 同一个逻辑表会根据 bucket、月份、类型切到不同物理表 |
| `多数据源` | 这张表属于哪个 `DataSource` | 表和数据源的映射是基础设施配置，不应写在 DSL 里 | `flowtest.v2.datasource.*` | 一条场景会同时观察不同库里的表 |

再用一句话记忆：

- `act-only` / `混合场景`：描述测试数据来源
- `RouteScope`：描述 SQL 怎么查
- `TableRouteScope`：描述查哪张物理表
- `多数据源配置`：描述去哪个库查

常见组合写法如下：

| 场景 | 示例写法 |
| --- | --- |
| `act-only + 普通表` | `.when(...).then(t -> t.table("ft_order", o -> o.inserted(1)))` |
| `act-only + 分库分表` | `.observe(o -> o.table("ft_order", r -> r.route("tenant_id", 100L))).when(...).then(...)` |
| `act-only + 动态表` | `.observe(o -> o.table("ft_order", r -> r.dynamicTableBy("bucket", "a"))).when(...).then(...)` |
| `act-only + 动态表 + 分库分表` | `.observe(o -> o.table("ft_order", r -> r.dynamicTableBy("bucket", "a").route("tenant_id", 100L))).when(...).then(...)` |
| `混合场景 + 普通表` | `.given(...).when(...).then(t -> t.fixture(user, ...).table("ft_order", ...))` |
| `混合场景 + 动态表 + 分库分表` | `.given(...).observe(o -> o.table("ft_order", r -> r.dynamicTableBy("bucket", "a").route("tenant_id", 100L))).when(...).then(...)` |

## 2. 模块选择

最常见的接入方式有 3 种：

1. Spring Boot
2. JUnit 5
3. TestNG

推荐优先级：

1. Spring Boot + JUnit 5
2. Spring Boot + TestNG
3. 纯 JUnit 5
4. 纯手动 JDBC 装配

如果你是第一次接入，建议先走 Spring Boot，再决定测试框架。

## 3. 第一步：添加依赖

### 3.1 Spring Boot + TestNG

```xml
<dependency>
    <groupId>com.github.sailfishc</groupId>
    <artifactId>flowtest-v2-testng</artifactId>
    <version>${flowtest.version}</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>com.github.sailfishc</groupId>
    <artifactId>flowtest-v2-spring-boot-starter</artifactId>
    <version>${flowtest.version}</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.testng</groupId>
    <artifactId>testng</artifactId>
    <scope>test</scope>
</dependency>
```

如果工程默认使用 JUnit，还需要让 Surefire 走 TestNG：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <testNGArtifactName>org.testng:testng</testNGArtifactName>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>org.apache.maven.surefire</groupId>
            <artifactId>surefire-testng</artifactId>
            <version>3.1.2</version>
        </dependency>
    </dependencies>
</plugin>
```

### 3.2 Spring Boot + JUnit 5

```xml
<dependency>
    <groupId>com.github.sailfishc</groupId>
    <artifactId>flowtest-v2-junit5</artifactId>
    <version>${flowtest.version}</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>com.github.sailfishc</groupId>
    <artifactId>flowtest-v2-spring-boot-starter</artifactId>
    <version>${flowtest.version}</version>
    <scope>test</scope>
</dependency>
```

### 3.3 纯 JUnit 5

```xml
<dependency>
    <groupId>com.github.sailfishc</groupId>
    <artifactId>flowtest-v2-junit5</artifactId>
    <version>${flowtest.version}</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

## 4. 第二步：注册实体和表

`v2` 不会扫描整个数据库，但对于 **带类型信息的实体观察**，框架会优先按注解和约定自动推导 JDBC 元数据。

对于 **fixture 实体**（在 `given(...)` 中声明的实体），框架也会在运行时自动注册 JDBC 元数据并生成默认的 fixture adapter。

对于 **observe-only 表**（只在 `observe(...)` 中声明但不造数的表），或者需要 route 条件的表，你需要显式注册。

如果你走的是 Spring Boot starter，starter 负责自动装配这些 Bean：

- `FlowTestDataSourceRegistry`
- `FixtureAdapterRegistry`
- `JdbcObservationRegistry`
- `FixtureMaterializer` — 默认使用 Instancio 自动填充
- `DataFiller` — 默认 `InstancioDataFiller`
- `FixtureExecutor`
- `ObservationExecutor`
- `ScenarioExecutor`

### 4.1 普通 JavaBean

```java
@JdbcEntity(table = "ft_user", keyColumns = {"id"})
public class TestUser {
    private Long id;
    private Long tenantId;
    private String name;
    private Long balance;
}
```

```java
@Bean
JdbcObservationRegistry jdbcObservationRegistry() {
    return new JdbcObservationRegistry()
        .registerTable("ft_order", "id");
}
```

### 4.2 MyBatis-Plus 实体

常规场景不再要求额外写 `@JdbcEntity`。只要实体上已经有 `@TableName`、`@TableId`、`@TableField` 就可以直接使用。

### 4.3 字段名和列名不一致

只配置差异项：

```java
@Bean
JdbcObservationRegistry jdbcObservationRegistry() {
    return new JdbcObservationRegistry()
        .entity(TestUser.class, "ft_user", "id")
        .column("displayName", "display_name")
        .ignore("transientFlag")
        .register();
}
```

## 5. 第三步：写第一条场景测试

下面这条测试覆盖了最基本的混合场景：

- `given` 准备一个用户
- `observe` 声明需要 route 条件的表（可选，普通表不需要）
- `when` 执行业务
- `then` 断言结果、fixture 状态、表变化

```java
ScenarioExecutionResult<Long> result = FlowTestV2.scenario("create-order")
    .given(g -> g.fixture("user", TestUser.class,
        FixtureTrait.mutate(v -> v.setId(1L)),
        FixtureTrait.mutate(v -> v.setTenantId(100L)),
        FixtureTrait.mutate(v -> v.setName("Alice")),
        FixtureTrait.mutate(v -> v.setBalance(100L))))
    .observe(o -> o.table("ft_order", r -> r.route("tenant_id", 100L)))
    .when(() -> orderService.createOrder(100L, 1L, 10L))
    .then(t -> t
        .success()
        .returns(10L)
        .fixture("user", TestUser.class, u -> u
            .before(v -> assertThat(v.getBalance()).isEqualTo(100L))
            .after(v -> assertThat(v.getBalance()).isEqualTo(80L)))
        .entity(TestUser.class, e -> e.modified(1))
        .table("ft_order", order -> order
            .inserted(1)
            .inspect(ctx -> {
                assertThat(ctx.insertedOne().getColumn("status")).isEqualTo("CREATED");
            })))
    .run();
```

这里的 `.run()` 依赖测试框架集成在当前线程提前绑定默认 `ScenarioExecutor`。如果你还在手动装配阶段，先使用 `.execute(executor)`。

运行结束后，框架会按 cleanup 策略自动清理。

### 5.1 观测自动推导

`then(...)` 中引用的资源会被自动推导为观测对象。你只在以下情况才需要显式写 `observe(...)`：

- 资源需要 **route 条件**（分库分表）
- 资源需要 **动态表参数**
- 资源只在 `inspect(...)` 里访问，不在声明式断言中出现

```java
// 普通场景：不需要 observe，自动推导
FlowTestV2.scenario("simple")
    .when(() -> service.doSomething())
    .then(t -> t
        .success()
        .table("ft_order", order -> order.inserted(1)))
    .run();

// 分库分表：需要 observe 指定 route
FlowTestV2.scenario("sharded")
    .observe(o -> o.table("ft_order", r -> r.route("tenant_id", 100L)))
    .when(() -> service.doSomething())
    .then(t -> t
        .success()
        .table("ft_order", order -> order.inserted(1)))
    .run();
```

### 5.2 Fixture 引用方式

新 DSL 支持两种 fixture 引用方式：

**Alias 方式（推荐）：**

```java
.given(g -> g.fixture("user", TestUser.class, ...))
.then(t -> t.fixture("user", TestUser.class, u -> u
    .after(v -> assertThat(v.getBalance()).isEqualTo(80L))))
```

**FixtureHandle 方式：**

```java
FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");

.given(g -> g.fixture(user, ...))
.then(t -> t.fixture(user, u -> u
    .after(v -> assertThat(v.getBalance()).isEqualTo(80L))))
```

两种方式等价，alias 方式更简洁。

### 5.3 同一个 entity 的多条前置数据

当一张表需要多条 fixture，使用 `fixtures(...)`：

```java
FlowTestV2.scenario("batch-users")
    .given(g -> g.fixtures(TestUser.class, rows -> rows
        .defaults(f -> f
            .set(TestUser::setTenantId, 100L)
            .set(TestUser::setBalance, 100L))
        .row("alice", f -> f
            .set(TestUser::setId, 1L)
            .set(TestUser::setName, "Alice"))
        .row("bob", f -> f
            .set(TestUser::setId, 2L)
            .set(TestUser::setName, "Bob"))
        .row("charlie", f -> f
            .set(TestUser::setId, 3L)
            .set(TestUser::setName, "Charlie")
            .set(TestUser::setBalance, 200L))))
    .when(() -> "ok")
    .then(t -> t.success())
    .run();
```

语义规则：

- `defaults(...)` 先应用到整组 row
- `row(...)` 再叠加差异
- 同一字段重复设置时，以 `row(...)` 中最后生效的值为准
- 具名 `row("alias", ...)` 可以在 `then(...)` 里继续引用

也支持 trait 风格：

```java
.row("alice", FixtureTrait.set(TestUser::setId, 1L))
```

### 5.4 数据库断言怎么写

所有数据库断言都通过 `then(...)` 完成。有两种风格：

**声明式（推荐简单场景）：**

```java
.then(t -> t
    .success()
    .table("ft_order", order -> order.inserted(1))
    .entity(TestUser.class, user -> user.modified(1)))
```

**声明式 + 块内 inspect（推荐需要值断言时）：**

```java
.then(t -> t
    .table("ft_order", order -> order
        .inserted(1)
        .inspect(ctx -> {
            assertThat(ctx.insertedOne().getColumn("status")).isEqualTo("CREATED");
            assertThat(ctx.insertedOne().getColumn("tenant_id")).isEqualTo(100L);
        })))
```

**全局 inspect（跨资源关联断言）：**

```java
.then(t -> t
    .table("ft_order", order -> order.inserted(1))
    .table("ft_payment", payment -> payment.inserted(1))
    .inspect(ctx -> {
        Long orderId = (Long) ctx.table("ft_order").insertedOne().getColumn("id");
        assertThat(ctx.table("ft_payment").insertedOne().getColumn("order_id"))
            .isEqualTo(orderId);
    }))
```

### 5.5 常见断言模式

**新增**

```java
.then(t -> t.table("ft_order", order -> order
    .inserted(1)
    .insertedRow(RowAssertions.columnEquals("status", "CREATED"))))
```

**修改**

```java
.then(t -> t.table("ft_user", user -> user
    .modified(1)
    .modifiedRow(ModifiedRowAssertions.changed("balance", 100L, 80L))))
```

**删除**

```java
.then(t -> t.table("ft_order", order -> order.deleted(1)))
```

**同表混合操作**

```java
.then(t -> t.table("ft_order", order -> order
    .inserted(2)
    .modified(1)
    .deleted(1)))
```

**多条数据排序断言**

```java
.then(t -> t.table("ft_order", order -> order
    .inserted(2)
    .insertedRows(rows -> rows
        .sortBy("id")
        .row(0, RowAssertions.columnEquals("status", "CREATED"))
        .row(1, RowAssertions.columnEquals("status", "PAID")))))
```

**Fixture before/after 断言**

```java
.then(t -> t.fixture(user, u -> u
    .before(v -> assertThat(v.getBalance()).isEqualTo(100L))
    .after(v -> assertThat(v.getBalance()).isEqualTo(80L))
    .change((before, after) -> {
        assertThat(after.getBalance()).isLessThan(before.getBalance());
    })))
```

**基于 fixture before-state 的全字段断言**

如果你只关心少数字段发生变化，可以用 `afterMatches(...)`：

```java
.then(t -> t.fixture(user, u -> u
    .afterMatches(FixtureStatePatch.of(TestUser.class)
        .set(TestUser::getBalance, 80L)
        .ignore(TestUser::getUpdatedAt))))
```

这段断言的语义是：

- `balance` 应该变成 `80L`
- `updatedAt` 不参与比较
- 其余受管理的持久化字段默认都必须和 `before()` 保持一致

**返回值断言**

```java
.then(t -> t
    .success()                    // 无异常
    .returns(10L))                // 返回值等于 10L

// 或者自定义断言
.then(t -> t.returnsSatisfying((result, failure) -> {
    assertThat(result).isGreaterThan(0L);
}))
```

**预期异常**

```java
.then(t -> t.failure(IllegalArgumentException.class))

// 或者带断言
.then(t -> t.failureSatisfying(IllegalArgumentException.class,
    ex -> assertThat(ex.getMessage()).contains("invalid")))
```

**完整复杂场景**

```java
FlowTestV2.scenario("create-order")
    .given(g -> g.fixture(user,
        FixtureTrait.mutate(v -> { v.setId(1L); v.setBalance(100L); })))
    .observe(o -> o.table("ft_order", r -> r.route("tenant_id", 100L)))
    .when(() -> orderService.createOrder(100L, 1L, 10L))
    .then(t -> t
        .success()
        .returns(10L)
        .fixture(user, u -> u
            .afterMatches(FixtureStatePatch.of(TestUser.class)
                .set(TestUser::getBalance, 80L)))
        .entity(TestUser.class, e -> e.modified(1))
        .table("ft_order", order -> order
            .inserted(1)
            .inspect(ctx -> {
                assertThat(ctx.insertedOne().getColumn("status")).isEqualTo("CREATED");
            })))
    .run();
```

## 6. 第四步：接入测试框架

### 6.1 Spring Boot + TestNG

```java
@SpringBootTest
@Listeners(FlowTestV2Listener.class)
public class OrderFlowTest extends AbstractTestNGSpringContextTests {
}
```

Spring Boot 场景下，`FlowTestV2Listener` 会自动从 `ApplicationContext` 解析 `ScenarioExecutor`。

### 6.2 Spring Boot + JUnit 5

推荐直接标注 `@FlowTestV2Test` 然后调用 `.run()`：

```java
@SpringBootTest
@FlowTestV2Test
class OrderFlowTest {
}
```

纯 JUnit 5 builder 方式：

```java
@RegisterExtension
static final FlowTestV2Extension FLOW = FlowTestV2Extension.builder()
    .dataSource(dataSource)
    .registerObservedEntity(TestUser.class)
    .registerObservedTable("ft_order", "id")
    .build();
```

### 6.3 纯 TestNG

不走 Spring 时，你需要自己构造 `ScenarioExecutor`，然后让 `FlowTestV2Listener` 注入。

## 7. 常用场景

### 7.1 只有 act 产生数据

没有 fixture 也可以，观测自动从 `then(...)` 推导：

```java
FlowTestV2.scenario("act-only")
    .when(() -> orderService.createOrder(100L, 1L, 10L))
    .then(t -> t
        .success()
        .table("ft_order", order -> order.inserted(1)))
    .run();
```

如果需要 route 条件：

```java
FlowTestV2.scenario("act-only-sharded")
    .observe(o -> o.table("ft_order", r -> r.route("tenant_id", 100L)))
    .when(() -> orderService.createOrder(100L, 1L, 10L))
    .then(t -> t
        .success()
        .table("ft_order", order -> order.inserted(1)))
    .run();
```

### 7.2 fixture-backed + 表观测混合

```java
FlowTestV2.scenario("mixed")
    .given(g -> g.fixture(user, UserTraits.id(1L), UserTraits.balance(100L)))
    .observe(o -> o.table("ft_order", r -> r.route("tenant_id", 100L)))
    .when(() -> orderService.createOrder(100L, 1L, 10L))
    .then(t -> t
        .success()
        .fixture(user, u -> u.after(v -> assertThat(v.getBalance()).isEqualTo(80L)))
        .table("ft_order", order -> order.inserted(1)))
    .run();
```

### 7.3 分库分表

如果 SQL 必须带分片条件，就在 `observe(...)` 里追加 `.route(...)`：

```java
.observe(o -> o.table("ft_order", r -> r.route("tenant_id", 100L)))
```

`RouteScope` 负责 SQL 路由条件，不负责动态表名。

## 8. 动态表名

动态表名和 SQL route 是两个独立维度：

- `TableRouteScope`：逻辑表 -> 物理表
- `RouteScope`：SQL 条件 / 路由条件

### 8.1 实体方式

```java
@JdbcDynamicTable(property = "bucket")
@TableName("ft_mp_order_dynamic")
public class DynamicOrderEntity {

    @TableId
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    private String status;

    @TableField(exist = false)
    private String bucket;
}
```

### 8.2 场景写法

只切物理表，不加 SQL route：

```java
.observe(o -> o.table("ft_mp_order_dynamic", r -> r.dynamicTableBy("bucket", "a")))
```

同时切物理表并带 SQL route：

```java
.observe(o -> o.table("ft_mp_order_dynamic", r -> r
    .dynamicTableBy("bucket", "a")
    .route("tenant_id", 100L)))
```

## 9. 多数据源

多数据源不要在测试 DSL 里逐表声明。应该在 Spring Boot 配置里一次性绑定。

### 9.1 `application.yml`

```yaml
flowtest:
  v2:
    datasource:
      default-name: orderDs
      bindings:
        - name: orderDs
          tables:
            - ft_order
            - ft_order_item
          patterns:
            - ft_mp_order_dynamic_*
        - name: accountDs
          tables:
            - ft_user
            - ft_account
```

### 9.2 测试写法

配置完之后，测试里不需要再写数据源名：

```java
.observe(o -> o.table("ft_mp_order_dynamic", r -> r
    .dynamicTableBy("bucket", "a")
    .route("tenant_id", 100L)))
.when(...)
.then(t -> t
    .fixture(user, u -> u.after(v -> assertThat(v.getBalance()).isEqualTo(80L)))
    .entity(TestUser.class, e -> e.modified(1))
    .table("ft_mp_order_dynamic", order -> order.inserted(1)))
```

## 10. Cleanup 策略

默认策略是 `DELETE_INSERTED`。

- 只有新增数据：`DELETE_INSERTED`
- 会修改/删除存量数据：`RESTORE_BEFORE_IMAGE`
- `ROLLBACK` 只在你自己已经提供外部事务边界时才成立

## 11. 自动数据填充

`v2` 默认使用 Instancio 自动填充 fixture 实体的字段。你只需要通过 trait 设置业务相关的字段，其他字段会自动生成随机数据。

自动填充会排除以下字段：
- ID 字段（`id`、`@Id`、`@TableId`）
- 忽略字段（`@JdbcIgnore`、JPA/Jakarta/Spring `@Transient`、`@TableField(exist = false)`）
- 动态表路由属性（`@JdbcDynamicTable(property = "...")` 指定的字段）

如果需要关闭自动填充：
- Spring Boot：设置 `flowtest.v2.data-filler=none`
- JUnit 5 builder：`.dataFiller(NoOpDataFiller.INSTANCE)`

## 12. Traits 怎么用

`FixtureTrait` 用来描述差异化测试数据。新版本提供多种创建方式：

### 12.1 创建 Trait 的方式

**单字段 setter（推荐）：**

```java
FixtureTrait.set(TestUser::setBalance, 100L)
```

**简单 lambda：**

```java
FixtureTrait.mutate(v -> v.setBalance(100L))
```

**多字段 builder（推荐复杂场景）：**

```java
FixtureTrait.draft(f -> f
    .set(TestUser::setId, 1L)
    .set(TestUser::setName, "Alice")
    .set(TestUser::setBalance, 100L))
```

**组合多个 trait：**

```java
FixtureTrait.all(
    UserTraits.inTenant(100L),
    UserTraits.balance(100L)
)
```

> **注意**：`FixtureTrait.of(...)` 和 `FixtureTrait.compose(...)` 已标记为 `@Deprecated`，请分别使用 `mutate(...)` 和 `all(...)` 替代。

### 12.2 推荐做法：按领域建立 Traits 类

```java
public final class UserTraits {

    public static FixtureTrait<TestUser> id(final Long id) {
        return FixtureTrait.set(TestUser::setId, id);
    }

    public static FixtureTrait<TestUser> inTenant(final Long tenantId) {
        return FixtureTrait.set(TestUser::setTenantId, tenantId);
    }

    public static FixtureTrait<TestUser> balance(final Long balance) {
        return FixtureTrait.set(TestUser::setBalance, balance);
    }

    public static FixtureTrait<TestUser> vipBuyer(final Long tenantId) {
        return FixtureTrait.all(
            inTenant(tenantId),
            FixtureTrait.set(TestUser::setName, "VIP_BUYER"),
            balance(1000L)
        );
    }
}
```

使用时：

```java
.given(g -> g.fixture(user,
    UserTraits.id(1L),
    UserTraits.vipBuyer(100L)))
```

### 12.3 Fixture 之间有依赖时，用 `TraitContext`

```java
public final class BelongsToUserTrait implements FixtureTrait<TestOrder> {

    private final String userAlias;

    public BelongsToUserTrait(String userAlias) {
        this.userAlias = userAlias;
    }

    @Override
    public void apply(TestOrder target, TraitContext context) {
        TestUser user = context.fixture(userAlias, TestUser.class);
        target.setUserId(user.getId());
        target.setTenantId(user.getTenantId());
    }
}
```

### 12.4 FixtureBuilder — inline 构造

除了 trait，还可以使用 `FixtureBuilder` 做 inline 构造：

```java
.given(g -> g.fixture("user", TestUser.class, f -> f
    .set(TestUser::setId, 1L)
    .set(TestUser::setName, "Alice")
    .set(TestUser::setBalance, 100L)))
```

builder 和 trait 可以混合使用：

```java
.given(g -> g.fixture("user", TestUser.class, f -> f
    .apply(UserTraits.inTenant(100L))
    .set(TestUser::setBalance, 100L)))
```

## 13. 常见问题

### 13.1 什么时候需要写 `observe`

只在这些情况需要：

- 资源需要 route 条件（分库分表）
- 资源需要动态表参数
- 资源只在全局 `inspect(...)` 里访问，不在声明式断言中出现

其他情况，`then(...)` 中引用的资源会自动被观测。

### 13.2 不写自定义 JDBC adapter 可以吗

普通单表场景可以。只有这些场景才建议自定义：多表写入、非标准主键、特殊 JDBC 类型。

### 13.3 MyBatis-Plus 还要写 `@JdbcEntity` 吗

常规场景不需要。动态表名仍然需要 `@JdbcDynamicTable(property = "bucket")`。

## 14. 示例索引

建议直接从这些测试文件抄起：

- Spring Boot + TestNG 最小接入：[FlowTestV2SpringBootTestNgExampleTest.java](../flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2SpringBootTestNgExampleTest.java)
- Spring Boot + TestNG 简化写法：[FlowTestV2SimpleSpringBootTest.java](../flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2SimpleSpringBootTest.java)
- Spring Boot + TestNG + 多数据源：[FlowTestV2MultiDataSourceSpringBootTestNgExampleTest.java](../flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2MultiDataSourceSpringBootTestNgExampleTest.java)
- Spring Boot + TestNG + MyBatis-Plus + 动态表：[FlowTestV2MybatisPlusDynamicTableSpringBootTestNgExampleTest.java](../flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2MybatisPlusDynamicTableSpringBootTestNgExampleTest.java)
- Spring Boot + TestNG + MyBatis-Plus + 动态表 + 多数据源：[FlowTestV2MybatisPlusDynamicTableMultiDataSourceSpringBootTestNgExampleTest.java](../flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2MybatisPlusDynamicTableMultiDataSourceSpringBootTestNgExampleTest.java)
- Spring Boot + TestNG + 分库分表动态表参考：[FlowTestV2ShardedDynamicTableReferenceSpringBootTestNgExampleTest.java](../flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2ShardedDynamicTableReferenceSpringBootTestNgExampleTest.java)
- Spring Boot + TestNG + 分库分表动态实体参考：[FlowTestV2ShardedDynamicEntityReferenceSpringBootTestNgExampleTest.java](../flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2ShardedDynamicEntityReferenceSpringBootTestNgExampleTest.java)
- Spring Boot + JUnit 5 简化写法：[FlowTestV2SimpleSpringBootTest.java](../flowtest-v2-junit5/src/test/java/com/github/sailfishc/flowtest/v2/junit5/FlowTestV2SimpleSpringBootTest.java)

## 15. 下一步阅读

- 架构说明：[flowtest-v2-architecture.md](flowtest-v2-architecture.md)
- 按集成方式查细节：[flowtest-v2-integrations.md](flowtest-v2-integrations.md)
