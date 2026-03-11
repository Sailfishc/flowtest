# Integration Recipes

## Spring Boot + JUnit 5

Dependencies:

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

Test shape:

```java
@SpringBootTest
@FlowTestV2Test
class OrderFlowTest {
}
```

Use `.run()` in scenarios. For new Spring Boot tests, do not implement `ScenarioExecutorProvider`.

## Spring Boot + TestNG

Dependencies:

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

Test shape:

```java
@SpringBootTest
@Listeners(FlowTestV2Listener.class)
class OrderFlowTest extends AbstractTestNGSpringContextTests {
}
```

`@FlowTestV2Executor` is optional and only needed when direct `ScenarioExecutor` field access is required.

## Manual JDBC wiring

Use manual wiring only when the project does not want Spring Boot auto-configuration:

```java
JdbcObservationRegistry observationRegistry = new JdbcObservationRegistry()
    .registerTable("ft_order", "id");

ScenarioExecutor executor = new ScenarioExecutor(
    new JdbcFixtureExecutor(dataSource, observationRegistry),
    new JdbcObservationExecutor(dataSource, observationRegistry)
);

FlowTestV2.scenario("create-order")
    .observe(o -> o.table("ft_order", r -> r.route("tenant_id", 100L)))
    .when(() -> service.create(...))
    .then(t -> t
        .success()
        .table("ft_order", order -> order.inserted(1)))
    .execute(executor);
```

## Routing rules

- Use `.route(...)` when SQL must carry shard predicates such as `tenant_id`.
- Use `.dynamicTableBy("bucket", "a")` when the logical table resolves to a physical table such as `ft_order_a`.
- Keep datasource routing in configuration:

```yaml
flowtest:
  v2:
    datasource:
      default-name: orderDs
      bindings:
        - name: orderDs
          tables: [ft_order, ft_order_item]
        - name: accountDs
          tables: [ft_user, ft_account]
```

## Cleanup defaults

- `DELETE_INSERTED`: default for act-only insert scenarios
- `RESTORE_BEFORE_IMAGE`: use when existing rows are updated or deleted

## Entity metadata

Common choices:

- `@JdbcEntity(table = "ft_user", keyColumns = {"id"})`
- `@JdbcDynamicTable(property = "bucket")` for dynamic table entities
- MyBatis-Plus metadata can be inferred from `@TableName`, `@TableId`, and `@TableField`

Common-path guidance:

- Fixture entities and typed watch entities do not need manual `registerEntity(...)` in the usual path.
- Watch-only tables still need explicit registration.
