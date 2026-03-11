# FlowTest V2 Integrations

如果你是第一次接入，先看从 0 到 1 的完整手册：

- [flowtest-v2-user-manual.md](/Users/zhangcheng/CodeProjects/flowtest/docs/flowtest-v2-user-manual.md)

`flowtest-v2` is split into a small runtime plus explicit integrations. The runtime does not guess routes, and it still requires explicit table/entity registration. For common JDBC usage, fixture adapters and datasource routing can now be derived from registration metadata plus Spring Boot properties.

> **API 变更说明**：当前推荐 API 为 `given -> observe? -> when -> then -> run`。`observe(...)` 替代了旧的 `watch(...)`，`then(...)` 替代了旧的 `verify(...)`，`fixture(...)` 替代了旧的 `persist(...)`。`then(...)` 中提到的资源会被自动推导为观察目标，大多数情况不需要显式写 `observe(...)`。

## JUnit 5

Use `FlowTestV2Extension` when you want JUnit 5 to bind the default executor for `.run()`. Parameter injection for `ScenarioExecutor` remains available, but it is no longer required in the common path.

```java
@JdbcEntity(table = "ft_user", keyColumns = "id")
public class TestUser {
}

@RegisterExtension
static final FlowTestV2Extension FLOW = FlowTestV2Extension.builder()
    .dataSource(dataSource)
    .registerObservedEntity(TestUser.class)
    .registerObservedTable("ft_order", "id")
    .build();

@Test
void shouldCreateOrder() throws Exception {
    FlowTestV2.scenario("create-order")
        .observe(o -> o.table("ft_order", r -> r.route("tenant_id", 100L)))
        .when(() -> service.create(...))
        .then(t -> t
            .success()
            .table("ft_order", order -> order.inserted(1)))
        .run();
}
```

In Spring Boot tests, you can also annotate the class with `@FlowTestV2Test` and call `.run()` directly. The extension will resolve `ScenarioExecutor` from the Spring `ApplicationContext` automatically.

## TestNG

Register `FlowTestV2Listener` and call `.run()`. In Spring Boot tests, the listener resolves `ScenarioExecutor` from the Spring container automatically.

```java
@Listeners(FlowTestV2Listener.class)
public class OrderFlowTest extends AbstractTestNGSpringContextTests {
}
```

`@FlowTestV2Executor` is optional. Keep it only when you explicitly need direct field access to the current executor. `ScenarioExecutorProvider` remains supported only as a backward-compatible fallback.

## Spring Boot

Add `flowtest-v2-spring-boot-starter`. The starter auto-configures:
- `FlowTestDataSourceRegistry`
- `FixtureAdapterRegistry`
- `JdbcObservationRegistry`
- `FixtureExecutor`
- `ObservationExecutor`
- `ScenarioExecutor`

For normal bean-style fixtures, the starter derives a default fixture adapter from entity metadata using `camelCase -> snake_case`.

**Auto data filling:** The starter automatically configures `InstancioDataFiller` so fixture entities are pre-filled with random data before traits are applied. Set `flowtest.v2.data-filler=none` to disable.

**Auto entity registration:** Fixture entities declared in `given(g -> g.fixture(...))` are automatically registered in `JdbcObservationRegistry` if not already registered, and `then(t -> t.entity(Foo.class, ...))` will also lazily introspect and register `Foo.class` on first use. In the common path, you only need to manually register watch-only tables or explicit overrides.
`registerEntity(Class<?>)` can now resolve metadata from:
- `@JdbcEntity`
- MyBatis-Plus `@TableName`, `@TableId`, `@TableField`
- convention fallback (`CamelCase -> snake_case`, `id` property as key)

You still need to provide business registrations for watch-only tables:

```java
@JdbcEntity(table = "ft_user", keyColumns = "id")
public class TestUser {
}

@Bean
JdbcObservationRegistry jdbcObservationRegistry() {
    return new JdbcObservationRegistry()
        .registerTable("ft_order", "id");
}
```

When a few fields do not follow the default naming rule, register only the differences:

```java
@Bean
JdbcObservationRegistry jdbcObservationRegistry() {
    return new JdbcObservationRegistry()
        .entity(TestUser.class, "ft_user", "id")
        .column("displayName", "display_name")
        .ignore("transientFlag")
        .register()
        .registerTable("ft_order", "id");
}
```

Keep a custom `FixtureEntityAdapter` only for special persistence logic such as multi-table inserts, generated keys, or non-standard JDBC types.

### Dynamic Table Names

When the logical table name is stable but the physical table changes by runtime routing value, register the logical table once and let FlowTest resolve the physical suffix.

`flowtest-v2` now treats dynamic table routing and SQL routing as two separate concerns:
- `TableRouteScope`: resolves logical table -> physical table
- `RouteScope`: contributes SQL route predicates or shard conditions

This matters because the value used to pick the physical table does not have to be a real database column.

Entity-based registration can use annotations:

```java
@JdbcEntity(table = "ft_order", keyColumns = "id")
@JdbcDynamicTable(property = "bucket")
public class OrderEntity {

    @JdbcIgnore
    private String bucket;
}
```

Table-only observation can use the registry builder:

```java
@Bean
JdbcObservationRegistry jdbcObservationRegistry() {
    return new JdbcObservationRegistry()
        .table("ft_order", "id")
        .dynamicByKey("bucket")
        .register();
}
```

If only the physical table is dynamic, prefer the high-level observe DSL:

```java
.observe(o -> o.table("ft_order", r -> r.dynamicTableBy("bucket", "a")))
```

If the table is dynamic and the SQL still needs shard predicates, pass both scopes:

```java
.observe(o -> o.table("ft_order", r -> r
    .dynamicTableBy("bucket", "a")
    .route("tenant_id", 100L)))
```

`TableRouteScope` is still available as the lower-level escape hatch when you need to prebuild a reusable scope object.

With the default suffix resolver, FlowTest maps:
- `ft_order` + `bucket = a` -> `ft_order_a`
- `ft_order` + `bucket = b` -> `ft_order_b`

If you need a different naming rule, use `.dynamicByKey("bucket", customResolver)` or `.dynamicByProperty("bucket", customResolver)`.

Built-in ignore support covers:
- `@JdbcIgnore`
- `javax.persistence.Transient`
- `jakarta.persistence.Transient`
- `org.springframework.data.annotation.Transient`
- MyBatis-Plus `@TableField(exist = false)`

You can also register custom ignore metadata when your ORM uses a different annotation:

```java
@Bean
JdbcObservationRegistry jdbcObservationRegistry() {
    return new JdbcObservationRegistry()
        .addIgnorePropertyResolver(JdbcIgnorePropertyResolvers.annotation("com.example.orm.IgnoreField"))
        .registerEntity(OrderEntity.class);
}
```

Current behavior:
- fixture-backed observation **automatically infers** `TableRouteScope` from the materialized fixture instance when the entity uses `@JdbcDynamicTable` and the routing property is set via traits
- fixture persistence/reload/delete supports dynamic table resolution
- dynamic table resolution can use entity-only properties that are ignored from database mapping
- use `entity(..., tableRouteScope)` or `shardedEntity(..., tableRouteScope, routeScope)` when the entity itself is stored in a dynamic table

### Multi-DataSource Routing

In Spring Boot, datasource routing should live in configuration, not in the scenario DSL. Bind tables or glob patterns to datasource bean names once:

```yaml
flowtest:
  v2:
    datasource:
      default-name: orderDs
      bindings:
        - name: orderDs
          tables: [ft_order, ft_order_item]
          patterns: [t_order_*]
        - name: accountDs
          tables: [ft_user, ft_account]
```

Matching rules are:
1. Exact table name
2. Glob pattern
3. `default-name`
4. Fail fast if nothing matches

If multiple patterns match the same table, startup fails fast when the table is first resolved.

Once routing is configured, the test DSL stays unchanged:

```java
.observe(o -> o.table("ft_order", r -> r.route("tenant_id", 100L)))
// fixture 会从 then(...) 自动推导，不需要在 observe 里声明
```

The framework resolves `ft_user` and `ft_order` to the correct `DataSource` automatically.
Dynamic tables are resolved before datasource lookup, so a logical table such as `ft_order_dynamic` can still route to different datasource bindings after becoming `ft_order_dynamic_a` or `ft_order_dynamic_b`.

## Manual JDBC Wiring

The same convention-based adapter generation is available without Spring Boot:

```java
@JdbcEntity(table = "ft_user", keyColumns = "id")
public class TestUser {
}

JdbcObservationRegistry observationRegistry = new JdbcObservationRegistry()
    .registerTable("ft_order", "id");

ScenarioExecutor executor = new ScenarioExecutor(
    new JdbcFixtureExecutor(dataSource, observationRegistry),
    new JdbcObservationExecutor(dataSource, observationRegistry)
);
```

This is the lowest-level path that still avoids handwritten JDBC fixture adapters for normal entities.

### Complete Spring Boot + TestNG Example

Use `flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2SpringBootTestNgExampleTest.java`
as the copyable reference. It shows the full wiring:

1. Add test dependencies:
   - `flowtest-v2-testng`
   - `flowtest-v2-spring-boot-starter`
   - `spring-boot-starter-test`
   - `spring-boot-starter-jdbc`
   - `testng`
   - `h2`
2. Make the test class extend `AbstractTestNGSpringContextTests`.
3. Add `@SpringBootTest` and `@Listeners(FlowTestV2Listener.class)`.
4. Register `JdbcObservationRegistry` in a test configuration. Only special persistence cases still need a custom `FixtureAdapterRegistry`.
5. Build the scenario with `given -> observe? -> when -> then`, then finish with `.run()`.
6. If you need direct executor access for a special case, inject `ScenarioExecutor` or use `@FlowTestV2Executor`. Do not implement `ScenarioExecutorProvider` in new Spring Boot tests.

The example uses:
- fixture-backed `ft_user`
- watch-only sharded `ft_order`
- TestNG listener-based default executor binding
- Spring Boot auto-configured `ScenarioExecutor` resolved without provider boilerplate
- row-level assertion for the inserted order row

### Complete Spring Boot + TestNG + MyBatis-Plus Dynamic Table Example

Use `flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2MybatisPlusDynamicTableSpringBootTestNgExampleTest.java`
when you need to combine:
- Spring Boot
- TestNG
- MyBatis-Plus mapper execution
- dynamic physical table names

That example shows:
- `BaseMapper`-based insert through MyBatis-Plus
- `DynamicTableNameInnerInterceptor` selecting `ft_mp_order_dynamic_a` / `ft_mp_order_dynamic_b`
- `@TableField(exist = false)` on the dynamic bucket property
- FlowTest observing the logical table `ft_mp_order_dynamic`
- `dynamicTableBy(...)` and `.route(...)` used together in the same scenario
- cleanup only affecting the resolved physical table

### Complete Spring Boot + TestNG + MyBatis-Plus Dynamic Table Multi-DataSource Example

Use `flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2MybatisPlusDynamicTableMultiDataSourceSpringBootTestNgExampleTest.java`
when you need one scenario to touch:
- a fixture-backed table on one datasource
- a MyBatis-Plus dynamic table on another datasource

That example shows:
- `ft_user` fixture data routed to `accountDs`
- `ft_mp_order_dynamic_*` routed to `orderDs` through datasource pattern binding
- MyBatis-Plus using the primary `orderDs`
- FlowTest resolving the physical table before datasource lookup
- one TestNG scenario asserting cross-datasource update + insert cleanup

### Complete Spring Boot + TestNG Single-Table Fixture + Sharded Dynamic Table Reference Example

Use `flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2ShardedDynamicTableReferenceSpringBootTestNgExampleTest.java`
when you need a copyable reference for:
- one single-table fixture prepared in `given(...)`
- one sharded dynamic table that starts empty and only receives data during `act`
- Spring Boot + TestNG + MyBatis-Plus + multi-datasource routing
- the latest `.observe(...) + .then(...) + .run()` path

That example shows:
- `ft_user_profile` fixture data routed to `accountDs`
- logical table `ft_trade_order` resolving to `ft_trade_order_a` / `ft_trade_order_b`
- datasource pattern binding for `ft_trade_order_*`
- `dynamicTableBy("bucket", "a")` and `.route("tenant_id", 100L)` used together
- `observe(o -> o.table(...))` for resources needing route config; fixtures auto-inferred from `then(...)`
- default cleanup removing both the fixture row and the inserted dynamic-table row

### Complete Spring Boot + TestNG Single-Table Fixture + Sharded Dynamic Entity Reference Example

Use `flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2ShardedDynamicEntityReferenceSpringBootTestNgExampleTest.java`
when you want the same business scenario, but prefer typed observation:
- `observe(o -> o.entity(TradeOrderEntity.class, ...))`
- entity metadata inferred from `@TableName`, `@TableId`, and `@JdbcDynamicTable`
- no upfront `registerEntity(...)` or `registerTable(...)` for the order resource

That example shows:
- the same single-table fixture + dynamic sharded insert flow as the table-based reference
- `ctx.entity(TradeOrderEntity.class)` assertions instead of `ctx.table("ft_trade_order")`
- an empty `JdbcObservationRegistry` bean to make the auto-inference path explicit
- when `entity(...)` can reduce manual registry wiring compared with `table(...)`

### Complete Spring Boot + TestNG Multi-DataSource Example

Use `flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2MultiDataSourceSpringBootTestNgExampleTest.java`
when you need fixture and observation to span multiple datasource beans.

That example shows:
- two explicit `DataSource` beans: `orderDs` and `accountDs`
- `flowtest.v2.datasource.bindings[...]` in `@SpringBootTest(properties = ...)`
- fixture-backed `ft_user` routed to `accountDs`
- sharded `ft_order` routed to `orderDs`
- a single scenario that updates one datasource and inserts into another

## Row-Level Assertions

Count assertions are still available, but `v2` now supports before/after row assertions:

```java
.then(t -> t
    .success()
    .table("ft_order", order -> order
        .inserted(1)
        .insertedRow(RowAssertions.columnEquals("status", "CREATED"))
        .modifiedRow(ModifiedRowAssertions.changed("status", "CREATED", "PAID"))))
```

Use `.satisfies(assertion)` or `.inspect(ctx -> ...)` when you need direct access to the full `ResourceChange`.

## Cleanup Notes

- Default cleanup is `DELETE_INSERTED`
- Use `RESTORE_BEFORE_IMAGE` for watch-only update/delete scenarios
- `ROLLBACK` still requires an external transaction boundary
