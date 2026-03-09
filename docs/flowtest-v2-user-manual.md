# FlowTest V2 使用手册

这份手册面向第一次接入 `flowtest-v2` 的用户，目标是让你从 0 到 1 完成集成，并写出第一条可运行的场景测试。

当前 `v2` 的核心设计是 4 个概念：

- `given`：准备前置数据，可选
- `observe`：声明要观测的表或实体，必填
- `when`：执行业务动作
- `then`：断言结果、数据变化和 fixture 状态

如果你只记一条规则，记这条：`observe` 决定框架会对哪些资源做快照、diff 和 cleanup，它不依赖 `given` 是否造数。

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
- **Fixture 实体自动注册**：`given(g -> g.persist(...))` 声明的实体类会自动注册到 JDBC 元数据，无需手动调用 `registerEntity`
- **动态表路由自动推导**：`observe.fixture(handle)` 可自动从 fixture 实例的动态表属性推导 `TableRouteScope`

当前限制：

- `CleanupPolicy.ROLLBACK` 需要外部事务边界，默认不要使用
- 自动推导 `TableRouteScope` 的前提是 trait 已设置动态表路由属性（如 `bucket`），否则需要手动 `.dynamicTableBy(...)`

推荐默认 cleanup：

- 只有新增数据：`DELETE_INSERTED`
- 会修改/删除存量数据：`RESTORE_BEFORE_IMAGE`

### 1.1 场景与能力对照

下面这张表先把几个最容易混淆的概念拆开：

| 概念 | 它解决的问题 | 典型特征 | 对应 API | 什么时候用 |
| --- | --- | --- | --- | --- |
| `act-only` | 测试前是否需要准备数据 | 没有 `given(...)`，数据只在 `when(...)` 中产生或变化 | `watch(...).when(...).verify(...)` | 只关心 act 产生的新数据或存量变化 |
| `混合场景` | 测试前是否需要准备数据 | 有 `given(...)`，同时观察 fixture 和 act 产生的数据 | `given(...).watch(...).when(...).verify(...)` | 先有前置数据，再执行 act 并校验变化 |
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
| `act-only + 普通表` | `.watch(w -> w.table("ft_order"))` |
| `act-only + 分库分表` | `.watch(w -> w.table("ft_order").route("tenant_id", 100L))` |
| `act-only + 动态表` | `.watch(w -> w.table("ft_order").dynamicTableBy("bucket", "a"))` |
| `act-only + 动态表 + 分库分表` | `.watch(w -> w.table("ft_order").dynamicTableBy("bucket", "a").route("tenant_id", 100L))` |
| `混合场景 + 普通表` | `.given(...).watch(w -> w.fixture(user).table("ft_order"))` |
| `混合场景 + 动态表 + 分库分表` | `.given(...).watch(w -> w.fixture(user).table("ft_order").dynamicTableBy("bucket", "a").route("tenant_id", 100L))` |

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

如果要跑 MyBatis-Plus 示例，再补：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <scope>test</scope>
</dependency>
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

所以最简场景下你不需要手动注册 fixture 用到的实体类；如果你写的是 `watch(w -> w.entity(TestUser.class))`，同样不需要手动 `registerEntity(...)`。

对于 **watch-only 表**（只在 `watch(...)` 中观测但不造数的表），你仍然需要显式注册。

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

常规场景不再要求额外写 `@JdbcEntity`。只要实体上已经有：

- `@TableName`
- `@TableId`
- `@TableField`

就可以直接：

```java
@TableName("ft_order")
public class OrderEntity {

    @TableId
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;
}
```

```java
@Bean
JdbcObservationRegistry jdbcObservationRegistry() {
    return new JdbcObservationRegistry();
}
```

如果你后面直接写：

```java
.watch(w -> w.entity(OrderEntity.class))
```

框架会按实体注解自动推导表名、主键、列名和动态表规则。

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
- `watch` 观察用户和订单表
- `when` 执行业务
- `verify` 在一个上下文里同时断言返回值、fixture 状态、表变化

```java
FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");

ScenarioExecutionResult<Long> result = FlowTestV2.scenario("create-order")
    .given(g -> g.persist(user,
        FixtureTrait.of(v -> v.setId(1L)),
        FixtureTrait.of(v -> v.setTenantId(100L)),
        FixtureTrait.of(v -> v.setName("Alice")),
        FixtureTrait.of(v -> v.setBalance(100L))))
    .watch(w -> w
        .fixture(user)
        .table("ft_order").route("tenant_id", 100L))
    .when(() -> orderService.createOrder(100L, 1L, 10L))
    .verify(ctx -> {
        ctx.success();
        assertThat(ctx.result()).isEqualTo(10L);
        assertThat(ctx.fixture(user).before().getBalance()).isEqualTo(100L);
        assertThat(ctx.fixture(user).after().getBalance()).isEqualTo(80L);
        assertThat(ctx.entity(TestUser.class).modifiedCount()).isEqualTo(1L);
        assertThat(ctx.table("ft_order").insertedCount()).isEqualTo(1L);
        assertThat(ctx.table("ft_order").insertedOne().getColumn("status")).isEqualTo("CREATED");
    })
    .run();
```

这里的 `.run()` 依赖测试框架集成在当前线程提前绑定默认 `ScenarioExecutor`。如果你还在手动装配阶段，先使用 `.execute(executor)`。

运行结束后，框架会按 cleanup 策略自动清理。

如果你更偏好旧的低层 DSL，`.observe(...)` 和 `.then(...)` 仍然可用；新版本推荐优先使用 `.watch(...) + .verify(ctx -> { ... })`，因为它能在一个地方同时拿到：

- `ctx.result()`：`act` 返回值
- `ctx.fixture(handle).before()/after()`：fixture 执行前后状态
- `ctx.table("...")` / `ctx.entity(...)`：diff 后的资源变化

完整可运行版本见：

- [FlowTestV2SpringBootTestNgExampleTest.java](/Users/zhangcheng/CodeProjects/flowtest/flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2SpringBootTestNgExampleTest.java)

## 6. 第四步：接入测试框架

### 6.1 Spring Boot + TestNG

推荐接入顺序：

1. 测试类继承 `AbstractTestNGSpringContextTests`
2. 标注 `@SpringBootTest`
3. 标注 `@Listeners(FlowTestV2Listener.class)`
4. 在场景里直接调用 `.run()`
5. 需要直接访问执行器时，再注入 `ScenarioExecutor` 或使用 `@FlowTestV2Executor`

```java
@SpringBootTest
@Listeners(FlowTestV2Listener.class)
public class OrderFlowTest extends AbstractTestNGSpringContextTests {
}
```

Spring Boot 场景下，`FlowTestV2Listener` 会自动从 `ApplicationContext` 解析 `ScenarioExecutor`。`ScenarioExecutorProvider` 仅作为兼容旧写法的 fallback，不再是推荐用法。

`@FlowTestV2Executor` 现在是可选的。只有在你确实要直接访问当前 `ScenarioExecutor` 字段时再加；常规场景直接 `.run()` 即可。

完整示例：

- [FlowTestV2SpringBootTestNgExampleTest.java](/Users/zhangcheng/CodeProjects/flowtest/flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2SpringBootTestNgExampleTest.java)

### 6.2 Spring Boot + JUnit 5

Spring Boot 场景下，推荐直接标注 `@FlowTestV2Test` 然后调用 `.run()`。扩展会自动从 Spring `ApplicationContext` 获取 `ScenarioExecutor`。只有在你确实需要手动访问执行器时，才额外注入 `ScenarioExecutor` 参数或字段。

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

没有 fixture 也可以：

```java
FlowTestV2.scenario("act-only")
    .watch(w -> w.table("ft_order").route("tenant_id", 100L))
    .when(() -> orderService.createOrder(100L, 1L, 10L))
    .verify(ctx -> {
        ctx.success();
        assertThat(ctx.table("ft_order").insertedCount()).isEqualTo(1L);
    })
    .run();
```

### 7.2 fixture-backed + watch-only 混合

```java
.watch(w -> w
    .fixture(user)
    .table("ft_order").route("tenant_id", 100L))
```

这里：

- `fixture(user)` 观察前置用户状态
- `ft_order` 是 act-only 的 watch-only 表

### 7.3 分库分表

如果 SQL 必须带分片条件，就给资源追加 `.route(...)`：

```java
.watch(w -> w.table("ft_order").route("tenant_id", 100L))
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

这里的 `bucket` 可以只存在于实体，不落数据库。

### 8.2 场景写法

只切物理表，不加 SQL route：

```java
.watch(w -> w.table("ft_mp_order_dynamic").dynamicTableBy("bucket", "a"))
```

同时切物理表并带 SQL route：

```java
.watch(w -> w.table("ft_mp_order_dynamic")
    .dynamicTableBy("bucket", "a")
    .route("tenant_id", 100L))
```

### 8.3 Fixture-backed 动态表自动推导

如果 fixture 实体使用了 `@JdbcDynamicTable`，并且 trait 设置了路由属性值，框架会自动推导 `TableRouteScope`。你不需要在 `watch` 里重复写 `.dynamicTableBy(...)`：

```java
FixtureHandle<DynamicOrderEntity> order = FixtureHandle.named(DynamicOrderEntity.class, "order");

FlowTestV2.scenario("auto-derive-dynamic-table")
    .given(g -> g.persist(order,
        FixtureTrait.of(v -> v.setBucket("a")),  // 设置路由属性
        FixtureTrait.of(v -> v.setStatus("CREATED"))))
    .watch(w -> w.fixture(order))  // 自动推导 TableRouteScope
    .when(() -> service.process(order))
    .verify(ctx -> {
        ctx.success();
    })
    .run();
```

前提：trait 必须设置动态表路由属性。如果路由属性为 null，框架会抛出明确错误提示。

完整示例：

- [FlowTestV2MybatisPlusDynamicTableSpringBootTestNgExampleTest.java](/Users/zhangcheng/CodeProjects/flowtest/flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2MybatisPlusDynamicTableSpringBootTestNgExampleTest.java)

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

匹配顺序：

1. 精确表名
2. 通配符
3. `default-name`
4. 仍然无法匹配则报错

### 9.2 测试写法

配置完之后，测试里不需要再写数据源名：

```java
.watch(w -> w
    .fixture(user)
    .table("ft_mp_order_dynamic")
        .dynamicTableBy("bucket", "a")
        .route("tenant_id", 100L))
```

完整示例：

- [FlowTestV2MultiDataSourceSpringBootTestNgExampleTest.java](/Users/zhangcheng/CodeProjects/flowtest/flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2MultiDataSourceSpringBootTestNgExampleTest.java)
- [FlowTestV2MybatisPlusDynamicTableMultiDataSourceSpringBootTestNgExampleTest.java](/Users/zhangcheng/CodeProjects/flowtest/flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2MybatisPlusDynamicTableMultiDataSourceSpringBootTestNgExampleTest.java)

## 10. Cleanup 策略

默认策略是 `DELETE_INSERTED`。

### 10.1 只有新增数据

保持默认即可：

```java
.cleanup(CleanupPolicy.DELETE_INSERTED)
```

### 10.2 会修改或删除存量数据

使用：

```java
.cleanup(CleanupPolicy.RESTORE_BEFORE_IMAGE)
```

### 10.3 什么时候不要用 `ROLLBACK`

当前 JDBC 运行时没有内建事务边界，所以：

```java
.cleanup(CleanupPolicy.ROLLBACK)
```

只有在你自己已经提供外部事务边界时才成立。普通场景不要作为默认选项。

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

`FixtureTrait` 用来描述差异化测试数据，不用写大而全的 builder。因为字段已经被自动填充，trait 只需要覆盖业务语义相关的字段。

```java
private FixtureTrait<TestUser> tenantTrait(final Long tenantId) {
    return FixtureTrait.of(v -> v.setTenantId(tenantId));
}

private FixtureTrait<TestUser> balanceTrait(final Long balance) {
    return FixtureTrait.of(v -> v.setBalance(balance));
}
```

组合时：

```java
.given(g -> g.persist(user,
    idTrait(1L),
    tenantTrait(100L),
    balanceTrait(100L)))
```

建议把 trait 设计成业务语义：

- `vip()`
- `inTenant(100L)`
- `balance(100L)`
- `frozen()`

不要把 trait 写成 JDBC 或 SQL 层面的操作。

### 11.1 `fixture` 和 `trait` 的关系

- `fixture`：这条前置数据本身
- `FixtureHandle`：这条前置数据的引用
- `trait`：把这条前置数据变成目标状态的规则

可以这样理解：

- `fixture` 是对象
- `trait` 是对象的变形规则

例如：

```java
FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");

.given(g -> g.persist(user,
    UserTraits.id(1L),
    UserTraits.inTenant(100L),
    UserTraits.balance(100L)))
```

这里：

- `user` 是 fixture
- `UserTraits.id(...)`、`UserTraits.inTenant(...)` 是 trait

### 11.2 不是只能写 `FixtureTrait.of(...)`

`FixtureTrait.of(...)` 只是最轻量的写法，适合：

- 临时样例
- 一次性测试
- 很简单的单字段赋值

例如：

```java
FixtureTrait.of(v -> v.setTenantId(100L))
```

但业务测试里，不建议长期堆很多内联 lambda。

### 11.3 推荐做法：按领域建立 Traits 类

推荐把常用 trait 收敛到单独的 traits 类里，比如：

- `UserTraits`
- `OrderTraits`
- `CouponTraits`

示例：

```java
public final class UserTraits {

    private UserTraits() {
    }

    public static FixtureTrait<TestUser> id(final Long id) {
        return FixtureTrait.of(v -> v.setId(id));
    }

    public static FixtureTrait<TestUser> inTenant(final Long tenantId) {
        return FixtureTrait.of(v -> v.setTenantId(tenantId));
    }

    public static FixtureTrait<TestUser> named(final String name) {
        return FixtureTrait.of(v -> v.setName(name));
    }

    public static FixtureTrait<TestUser> balance(final Long balance) {
        return FixtureTrait.of(v -> v.setBalance(balance));
    }

    public static FixtureTrait<TestUser> vipBuyer(final Long tenantId) {
        return FixtureTrait.compose(
            inTenant(tenantId),
            named("VIP_BUYER"),
            balance(1000L)
        );
    }
}
```

使用时：

```java
.given(g -> g.persist(user,
    UserTraits.id(1L),
    UserTraits.vipBuyer(100L)))
```

这种写法比一串内联 `FixtureTrait.of(...)` 更适合长期维护。

### 11.4 什么时候用静态 traits，什么时候写独立类

适合用 `UserTraits.xxx(...)` 这类静态工厂方法的场景：

- 只是简单设值
- 只是组合已有 trait
- 业务语义清晰
- 你希望测试代码短而稳定

适合单独实现一个 `FixtureTrait<T>` 类的场景：

- trait 逻辑比较复杂
- 需要参数校验
- 需要读取其他 fixture
- 需要封装领域规则而不是简单赋值

例如：

```java
public final class VipBuyerTrait implements FixtureTrait<TestUser> {

    private final Long tenantId;

    public VipBuyerTrait(Long tenantId) {
        this.tenantId = tenantId;
    }

    @Override
    public void apply(TestUser target, TraitContext context) {
        target.setTenantId(tenantId);
        target.setName("VIP_BUYER");
        target.setBalance(1000L);
    }
}
```

### 11.5 trait 之间可以组合

`FixtureTrait` 本身支持组合：

```java
FixtureTrait<TestUser> vip = UserTraits.inTenant(100L)
    .and(UserTraits.named("VIP_BUYER"))
    .and(UserTraits.balance(1000L));
```

或者：

```java
FixtureTrait<TestUser> vip = FixtureTrait.compose(
    UserTraits.inTenant(100L),
    UserTraits.named("VIP_BUYER"),
    UserTraits.balance(1000L)
);
```

建议：

- 原子 trait 负责单一职责
- 场景 trait 负责组合原子 trait

### 11.6 fixture 之间有依赖时，用 `TraitContext`

如果一个 fixture 依赖另一个 fixture，可以通过 `TraitContext` 解析 handle。

例如订单依赖用户：

```java
public final class BelongsToUserTrait implements FixtureTrait<TestOrder> {

    private final FixtureHandle<TestUser> userHandle;

    public BelongsToUserTrait(FixtureHandle<TestUser> userHandle) {
        this.userHandle = userHandle;
    }

    @Override
    public void apply(TestOrder target, TraitContext context) {
        TestUser user = context.resolve(userHandle);
        target.setUserId(user.getId());
        target.setTenantId(user.getTenantId());
    }
}
```

这样 trait 就不只是“填字段”，而是可以表达 fixture 关系。

### 11.7 最佳实践

推荐遵守这几条：

1. 样例代码可以用 `FixtureTrait.of(...)`，业务测试尽量沉淀成领域化 `Traits` 类。
2. 原子 trait 只做一件事，比如 `inTenant(...)`、`balance(...)`。
3. 场景 trait 只组合业务语义，不要混入 JDBC、SQL、查询逻辑。
4. trait 名称优先用业务词汇，不要用技术词汇。
5. 复杂依赖关系通过 `TraitContext` 处理，不要在测试里手工串字段。
6. 一个测试里如果开始反复出现 3 个以上相同 trait 组合，就应该抽成公共 trait。

## 13. 常见问题

### 12.1 为什么必须写 `observe`

因为 `v2` 是 observation-first 设计。框架只对你显式声明的资源做：

- baseline
- diff
- cleanup

### 12.2 不写自定义 JDBC adapter 可以吗

普通单表场景可以。框架会根据注册的实体元数据自动生成默认 adapter。

只有这些场景才建议自定义：

- 多表写入
- 非标准主键
- 特殊 JDBC 类型
- 特殊 reload / delete 逻辑

### 12.3 MyBatis-Plus 还要写 `@JdbcEntity` 吗

常规场景不需要。

如果你要动态表名，仍然需要 FlowTest 自己的：

```java
@JdbcDynamicTable(property = "bucket")
```

### 12.4 动态表字段必须是数据库列吗

不必须。它可以只存在于实体，例如：

```java
@TableField(exist = false)
private String bucket;
```

## 14. 示例索引

建议直接从这些测试文件抄起：

- Spring Boot + TestNG 最小接入：
  - [FlowTestV2SpringBootTestNgExampleTest.java](/Users/zhangcheng/CodeProjects/flowtest/flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2SpringBootTestNgExampleTest.java)
- Spring Boot + TestNG + 多数据源：
  - [FlowTestV2MultiDataSourceSpringBootTestNgExampleTest.java](/Users/zhangcheng/CodeProjects/flowtest/flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2MultiDataSourceSpringBootTestNgExampleTest.java)
- Spring Boot + TestNG + MyBatis-Plus + 动态表：
  - [FlowTestV2MybatisPlusDynamicTableSpringBootTestNgExampleTest.java](/Users/zhangcheng/CodeProjects/flowtest/flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2MybatisPlusDynamicTableSpringBootTestNgExampleTest.java)
- Spring Boot + TestNG + MyBatis-Plus + 动态表 + 多数据源：
  - [FlowTestV2MybatisPlusDynamicTableMultiDataSourceSpringBootTestNgExampleTest.java](/Users/zhangcheng/CodeProjects/flowtest/flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2MybatisPlusDynamicTableMultiDataSourceSpringBootTestNgExampleTest.java)
- Spring Boot + TestNG + 单表 fixture + 分库分表动态表参考：
  - [FlowTestV2ShardedDynamicTableReferenceSpringBootTestNgExampleTest.java](/Users/zhangcheng/CodeProjects/flowtest/flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2ShardedDynamicTableReferenceSpringBootTestNgExampleTest.java)

## 15. 下一步阅读

- 架构说明：[flowtest-v2-architecture.md](/Users/zhangcheng/CodeProjects/flowtest/docs/flowtest-v2-architecture.md)
- 按集成方式查细节：[flowtest-v2-integrations.md](/Users/zhangcheng/CodeProjects/flowtest/docs/flowtest-v2-integrations.md)
