# FlowTest V2 Integrations

`flowtest-v2` is split into a small runtime plus explicit integrations. The runtime does not guess routes, and it still requires explicit table/entity registration. For common JDBC usage, fixture adapters and datasource routing can now be derived from registration metadata plus Spring Boot properties.

## JUnit 5

Use `FlowTestV2Extension` when you want parameter injection for `ScenarioExecutor`.

```java
@RegisterExtension
static final FlowTestV2Extension FLOW = FlowTestV2Extension.builder()
    .dataSource(dataSource)
    .registerObservedTable("ft_order", "id")
    .registerFixtureAdapter(new OrderAdapter())
    .build();

@Test
void shouldCreateOrder(ScenarioExecutor executor) throws Exception {
    FlowTestV2.scenario("create-order")
        .observe(o -> o.shardedTable("ft_order", RouteScope.of(RouteCondition.eq("tenant_id", 100L))))
        .when(() -> service.create(...))
        .then(t -> t.expectNoException().inserted("ft_order", 1))
        .execute(executor);
}
```

You can also annotate the class with `@FlowTestV2Test` and implement `ScenarioExecutorProvider`.

## TestNG

Register `FlowTestV2Listener` and inject the executor into a field.

```java
@Listeners(FlowTestV2Listener.class)
public class OrderFlowTest implements ScenarioExecutorProvider {

    @FlowTestV2Executor
    private ScenarioExecutor executor;
}
```

## Spring Boot

Add `flowtest-v2-spring-boot-starter`. The starter auto-configures:
- `FlowTestDataSourceRegistry`
- `FixtureAdapterRegistry`
- `JdbcObservationRegistry`
- `FixtureExecutor`
- `ObservationExecutor`
- `ScenarioExecutor`

For normal bean-style fixtures, you only need to register entities and observed tables. The starter now derives a default fixture adapter from entity metadata using `camelCase -> snake_case`.

You still need to provide business registrations:

```java
@JdbcEntity(table = "ft_user", keyColumns = "id")
public class TestUser {
}

@Bean
JdbcObservationRegistry jdbcObservationRegistry() {
    return new JdbcObservationRegistry()
        .registerEntity(TestUser.class)
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

When the logical table name is stable but the physical table changes by routing field, register the logical table once and let FlowTest resolve the physical suffix at runtime.

Entity-based registration can use annotations:

```java
@JdbcEntity(table = "ft_order", keyColumns = "id")
@JdbcDynamicTable(column = "bucket")
public class OrderEntity {
}
```

Table-only observation can use the registry builder:

```java
@Bean
JdbcObservationRegistry jdbcObservationRegistry() {
    return new JdbcObservationRegistry()
        .table("ft_order", "id")
        .dynamicByColumn("bucket")
        .register();
}
```

Then the scenario still observes the logical table name:

```java
.observe(o -> o.shardedTable("ft_order", RouteScope.of(RouteCondition.eq("bucket", "a"))))
```

With the default resolver, FlowTest maps:
- `ft_order` + `bucket = a` -> `ft_order_a`
- `ft_order` + `bucket = b` -> `ft_order_b`

If you need a different naming rule, use `.dynamicByColumn("bucket", customResolver)`.

Current constraint:
- dynamic table observation must have an explicit route scope
- fixture persistence/reload/delete supports dynamic table resolution
- fixture-backed observation should use `shardedEntity(...)` or `table(..., route)` rather than `fixture(handle)` when the entity itself is stored in a dynamic table

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
.observe(o -> o
    .fixture(user)
    .shardedTable("ft_order", RouteScope.of(RouteCondition.eq("tenant_id", 100L))))
```

The framework resolves `ft_user` and `ft_order` to the correct `DataSource` automatically.

## Manual JDBC Wiring

The same convention-based adapter generation is available without Spring Boot:

```java
@JdbcEntity(table = "ft_user", keyColumns = "id")
public class TestUser {
}

JdbcObservationRegistry observationRegistry = new JdbcObservationRegistry()
    .registerEntity(TestUser.class)
    .registerTable("ft_order", "id");

ScenarioExecutor executor = new ScenarioExecutor(
    new JdbcFixtureExecutor(dataSource, observationRegistry),
    new JdbcObservationExecutor(dataSource, observationRegistry)
);
```

This is the lowest-level path that still avoids handwritten JDBC fixture adapters for normal entities.

### Complete Spring Boot + TestNG Example

Use `flowtest-v2/flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2SpringBootTestNgExampleTest.java`
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
4. Implement `ScenarioExecutorProvider` and return the Spring-managed `ScenarioExecutor`.
5. Inject `ScenarioExecutor` into a field annotated with `@FlowTestV2Executor`.
6. Register `JdbcObservationRegistry` in a test configuration. Only special persistence cases still need a custom `FixtureAdapterRegistry`.
7. Build the scenario with `given -> observe -> when -> then`, then execute it with the injected executor.

The example uses:
- fixture-backed `ft_user`
- watch-only sharded `ft_order`
- TestNG listener-based executor injection
- Spring Boot auto-configured `ScenarioExecutor`
- row-level assertion for the inserted order row

### Complete Spring Boot + TestNG Multi-DataSource Example

Use `flowtest-v2/flowtest-v2-testng/src/test/java/com/github/sailfishc/flowtest/v2/testng/springboot/FlowTestV2MultiDataSourceSpringBootTestNgExampleTest.java`
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
    .insertedRow("ft_order", RowAssertions.columnEquals("status", "CREATED"))
    .modifiedRow("ft_order", ModifiedRowAssertions.changed("status", "CREATED", "PAID")));
```

Use `.change(resourceName, assertion)` when you need direct access to the full `ResourceChange`.

## Cleanup Notes

- Default cleanup is `DELETE_INSERTED`
- Use `RESTORE_BEFORE_IMAGE` for watch-only update/delete scenarios
- `ROLLBACK` still requires an external transaction boundary
