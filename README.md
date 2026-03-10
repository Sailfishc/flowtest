# FlowTest

FlowTest 是一个面向数据库集成测试的 Java 8 框架。当前仓库的主线是 **V2**：一个 **observation-first** 的测试 DSL，用 `given -> watch -> when -> verify` 描述场景，自动完成快照、diff 和 cleanup。

如果你是第一次接触这个项目，可以先记住一句话：

> FlowTest V2 不是先“造很多数据再猜会变什么”，而是先声明“我要观察什么”，再执行业务动作，并对结果和数据变化做统一校验。

V1 模块仍然保留在仓库中，但根目录 README 现在以 V2 为准。

## 1. 这个项目是什么

FlowTest V2 适合写这类测试：

- 业务逻辑会新增、修改、删除数据库记录
- 测试不仅要校验返回值，还要校验数据库变化
- 测试环境可能存在分库分表、动态表名、多数据源
- 你希望测试结束后自动清理，而不是手写回滚和删数据脚本

它的核心模型只有 4 个概念：

- `given`：准备前置数据，可选
- `watch`：声明要观察的表、实体或 fixture，必填
- `when`：执行业务动作
- `verify`：在一个上下文里统一断言返回值、异常、fixture 前后状态和数据库 diff

推荐 API 形态：

```java
FlowTestV2.scenario("create-order")
    .given(g -> g.persist(...))
    .watch(w -> w.fixture(...).table("ft_order").route("tenant_id", 100L))
    .when(() -> orderService.createOrder(...))
    .verify(ctx -> {
        ctx.success();
        // assert result / fixture before-after / table diff
    })
    .run();
```

## 2. 有什么作用，解决什么问题

### 它主要解决 3 类问题

#### 2.1 返回值断言和数据库断言分裂

传统集成测试常见写法是：

1. 手动准备数据
2. 调业务方法
3. 再手写 SQL 查询数据库
4. 测试结束自己清理

这会导致测试代码被样板逻辑淹没。FlowTest V2 把这些动作收敛到一个场景里，让测试重点回到业务意图。

#### 2.2 act-only 场景很难写

很多框架默认围绕 arrange 设计，但真实业务里常见的是：

- 测试前根本不需要插入数据
- 数据只会在 `act` 之后出现
- 你只想观察某张表是否新增/修改/删除了什么

V2 的 `watch(...)` 就是为这种场景设计的。即使没有 `given(...)`，也可以先声明观察范围，再执行 `when(...)`。

#### 2.3 分库分表、动态表、多数据源下很难稳定清理

当数据库访问带有这些约束时：

- SQL 必须带路由条件
- 逻辑表名会映射成运行时物理表名
- 不同表分布在不同 `DataSource`

测试最容易出错的地方不是断言，而是快照和清理。FlowTest V2 把这些规则显式建模：

- `.route(...)`：SQL 路由条件
- `.dynamicTableBy(...)`：动态表路由
- `flowtest.v2.datasource.*`：多数据源绑定

这样快照、diff 和 cleanup 走的是同一套解析逻辑，不需要测试自己重复实现。

## 3. 快速开始

### 推荐接入方式

优先推荐：

1. Spring Boot + JUnit 5
2. Spring Boot + TestNG
3. 纯 JUnit 5

### Maven 依赖

Spring Boot + JUnit 5：

```xml
<dependency>
    <groupId>com.github.Sailfishc</groupId>
    <artifactId>flowtest-v2-junit5</artifactId>
    <version>${flowtest.version}</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>com.github.Sailfishc</groupId>
    <artifactId>flowtest-v2-spring-boot-starter</artifactId>
    <version>${flowtest.version}</version>
    <scope>test</scope>
</dependency>
```

Spring Boot + TestNG：

```xml
<dependency>
    <groupId>com.github.Sailfishc</groupId>
    <artifactId>flowtest-v2-testng</artifactId>
    <version>${flowtest.version}</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>com.github.Sailfishc</groupId>
    <artifactId>flowtest-v2-spring-boot-starter</artifactId>
    <version>${flowtest.version}</version>
    <scope>test</scope>
</dependency>
```

### 第一个 V2 场景

```java
import com.github.sailfishc.flowtest.v2.FlowTestV2;
import com.github.sailfishc.flowtest.v2.junit5.FlowTestV2Test;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcEntity;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationRegistry;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutionResult;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@FlowTestV2Test
class OrderFlowTest {

    @Autowired
    private OrderService orderService;

    @Bean
    JdbcObservationRegistry jdbcObservationRegistry() {
        return new JdbcObservationRegistry()
            .registerTable("ft_order", "id");
    }

    @Test
    void shouldCreateOrder() throws Exception {
        ScenarioExecutionResult<Long> result = FlowTestV2.scenario("create-order")
            .given(g -> g.persist("user", TestUser.class,
                FixtureTrait.of(v -> v.setId(1L)),
                FixtureTrait.of(v -> v.setTenantId(100L)),
                FixtureTrait.of(v -> v.setBalance(100L))))
            .watch(w -> w
                .fixture("user")
                .table("ft_order").route("tenant_id", 100L))
            .when(() -> orderService.createOrder(100L, 1L, 20L))
            .verify(ctx -> {
                ctx.success();
                assertThat(ctx.result()).isEqualTo(10L);
                assertThat(ctx.fixture("user", TestUser.class).before().getBalance()).isEqualTo(100L);
                assertThat(ctx.fixture("user", TestUser.class).after().getBalance()).isEqualTo(80L);
                assertThat(ctx.table("ft_order").insertedCount()).isEqualTo(1L);
                assertThat(ctx.table("ft_order").insertedOne().getColumn("status")).isEqualTo("CREATED");
            })
            .run();

        assertThat(result.getResult()).isEqualTo(10L);
    }

    @JdbcEntity(table = "ft_user", keyColumns = {"id"})
    static class TestUser {
        private Long id;
        private Long tenantId;
        private Long balance;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public Long getBalance() { return balance; }
        public void setBalance(Long balance) { this.balance = balance; }
    }
}
```

这个例子里：

- `given(...)` 只准备用户 fixture
- `watch(...)` 同时观察 fixture 和订单表
- `.route("tenant_id", 100L)` 让快照、diff、cleanup 都带上分片条件
- `verify(ctx -> { ... })` 在一个地方校验返回值和数据库变化
- `.run()` 使用测试框架提前绑定的 `ScenarioExecutor`

## 4. 核心能力

### act-only 场景

不需要 fixture 时，直接观察表即可：

```java
FlowTestV2.scenario("act-only")
    .watch(w -> w.table("ft_order").route("tenant_id", 100L))
    .when(() -> orderService.createOrder(100L, 1L, 20L))
    .verify(ctx -> {
        ctx.success();
        assertThat(ctx.table("ft_order").insertedCount()).isEqualTo(1L);
    })
    .run();
```

### watch fixture / table / entity

```java
.watch(w -> w.fixture("user"))
.watch(w -> w.table("ft_order"))
.watch(w -> w.entity(OrderEntity.class))
```

说明：

- `fixture(...)` 适合观察前置数据前后变化
- `table(...)` 适合 watch-only 表
- `entity(...)` 适合用实体类型推导表名、主键和列映射

对于 `watch(w -> w.entity(OrderEntity.class))` 这种 typed observation，框架会按注解和约定自动推导元数据，通常不需要手动 `registerEntity(...)`。

### 动态表名

```java
.watch(w -> w.table("ft_order_dynamic").dynamicTableBy("bucket", "a"))
```

如果同时需要 SQL 路由条件：

```java
.watch(w -> w.table("ft_order_dynamic")
    .dynamicTableBy("bucket", "a")
    .route("tenant_id", 100L))
```

### 多数据源

多数据源不建议在 DSL 里逐条声明。推荐一次性配置：

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
        - name: accountDs
          tables:
            - ft_user
            - ft_account
```

配置后，场景仍然只写逻辑资源，不写数据源名称。

## 5. 最佳实践

### 5.1 默认写法用 `watch + verify + run`

推荐主路径：

```java
FlowTestV2.scenario("...")
    .given(...)
    .watch(...)
    .when(...)
    .verify(ctx -> { ... })
    .run();
```

### 同一张表多条前置数据

高频单条场景继续用 `persist(...)`。只有当同一个 entity 需要多条 fixture，而且大部分字段相同、少数字段不同的时候，再进入 `persistRows(...)`：

```java
FlowTestV2.scenario("batch-users")
    .given(g -> g
        .persistRows(TestUser.class, rows -> rows
            .defaults(
                FixtureTrait.of(v -> v.setTenantId(100L)),
                FixtureTrait.of(v -> v.setBalance(100L)))
            .row("alice",
                FixtureTrait.of(v -> v.setId(1L)),
                FixtureTrait.of(v -> v.setName("Alice")))
            .row("bob",
                FixtureTrait.of(v -> v.setId(2L)),
                FixtureTrait.of(v -> v.setName("Bob")))
            .row("charlie",
                FixtureTrait.of(v -> v.setId(3L)),
                FixtureTrait.of(v -> v.setName("Charlie")),
                FixtureTrait.of(v -> v.setBalance(200L)))))
    .watch(w -> w.entity(TestUser.class))
    .verify(ctx -> {
        assertThat(ctx.fixture("alice", TestUser.class).before().getName()).isEqualTo("Alice");
        assertThat(ctx.fixture("charlie", TestUser.class).before().getBalance()).isEqualTo(200L);
    })
    .run();
```

语义上：

- `defaults(...)` 先应用
- 每个 `row(...)` 再叠加自己的差异 trait
- 如果同一属性重复设置，以 `row(...)` 内最后生效的值为准

不要再把新文档和新示例建立在旧的 `.observe(...) + .then(...) + .execute(executor)` 上。旧 API 仍可用，但它已经不是推荐入口。

### 5.2 `watch` 先写“资源”，再补“路由”

可以把几个概念分开理解：

- `watch`：我要观察什么
- `.route(...)`：SQL 该怎么查
- `.dynamicTableBy(...)`：实际查哪张物理表
- `flowtest.v2.datasource.*`：去哪个数据源查

把这 4 层分开后，分库分表测试会稳定很多，也更容易排查问题。

### 5.3 只为 watch-only 表显式注册元数据

推荐把 `JdbcObservationRegistry` 当成“覆盖和补充配置点”，不是“所有实体都手动登记的清单”。

实践建议：

- fixture 实体：直接在 `given(...)` 里用，通常不需要额外注册
- typed entity observation：直接 `watch(w -> w.entity(...))`
- watch-only 表：显式 `registerTable(...)`
- 字段映射不规则时：只补充差异配置

### 5.4 trait 只表达业务语义，不表达持久化细节

推荐：

- `vip()`
- `inTenant(100L)`
- `balance(100L)`
- `frozen()`

不推荐把 trait 写成 JDBC/SQL 操作集合。trait 的职责是描述 fixture 应该长成什么业务状态。

### 5.5 cleanup 策略按数据形态选

默认策略是 `DELETE_INSERTED`。

选择建议：

- 只有新增数据：`DELETE_INSERTED`
- 会修改或删除存量数据：`RESTORE_BEFORE_IMAGE`
- 没有外部事务边界时：不要默认使用 `ROLLBACK`

### 5.6 Spring Boot 集成下优先用框架自动绑定执行器

推荐：

- JUnit 5：`@FlowTestV2Test` + `.run()`
- TestNG：`@Listeners(FlowTestV2Listener.class)` + `.run()`

只有在你确实需要直接拿 `ScenarioExecutor` 时，才显式注入它。常规场景不需要实现 `ScenarioExecutorProvider`。

## 6. 什么时候适合用 FlowTest V2

适合：

- 下单、支付、扣减库存、账户变更这类数据库副作用明确的业务测试
- 既要验返回值，也要验数据库变化的集成测试
- 分片、动态表、多数据源场景

不太适合：

- 纯内存逻辑单测
- 只关心 service 返回值，不关心数据库副作用
- 完全依赖事务回滚、且不愿显式声明观察资源的测试风格

## 7. 文档和示例

推荐阅读顺序：

1. [docs/flowtest-v2-user-manual.md](docs/flowtest-v2-user-manual.md)
2. [docs/flowtest-v2-integrations.md](docs/flowtest-v2-integrations.md)
3. `flowtest-v2-*` 模块中的示例测试

可直接参考的测试：

- [flowtest-v2-junit5/src/test/java/com/github/sailfishc/flowtest/v2/junit5/FlowTestV2SimpleSpringBootTest.java](flowtest-v2-junit5/src/test/java/com/github/sailfishc/flowtest/v2/junit5/FlowTestV2SimpleSpringBootTest.java)
- [flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2SpringBootTestNgExampleTest.java](flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2SpringBootTestNgExampleTest.java)
- [flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2MultiDataSourceSpringBootTestNgExampleTest.java](flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2MultiDataSourceSpringBootTestNgExampleTest.java)
- [flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2MybatisPlusDynamicTableSpringBootTestNgExampleTest.java](flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2MybatisPlusDynamicTableSpringBootTestNgExampleTest.java)

## 8. 仓库结构

当前仓库同时包含 V1 和 V2：

- `flowtest-v2-*`：V2 主线模块
- `flowtest-core`、`flowtest-junit5`、`flowtest-testng`、`flowtest-spring-boot-starter`：V1 legacy 模块

如果你要新接入，请直接从 `flowtest-v2-*` 开始。
