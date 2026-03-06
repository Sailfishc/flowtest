# FlowTest V2 Integrations

`flowtest-v2` is split into a small runtime plus explicit integrations. The runtime does not guess tables, routes, or fixture adapters. You register those pieces yourself.

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
- `FixtureAdapterRegistry`
- `JdbcObservationRegistry`
- `FixtureExecutor`
- `ObservationExecutor`
- `ScenarioExecutor`

You still need to provide business registrations:

```java
@Bean
FixtureAdapterRegistry fixtureAdapterRegistry() {
    return new FixtureAdapterRegistry().register(new UserAdapter());
}

@Bean
JdbcObservationRegistry jdbcObservationRegistry() {
    return new JdbcObservationRegistry()
        .registerEntity(TestUser.class, "ft_user", "id")
        .registerTable("ft_order", "id");
}
```

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
6. Register `FixtureAdapterRegistry` and `JdbcObservationRegistry` in a test configuration.
7. Build the scenario with `given -> observe -> when -> then`, then execute it with the injected executor.

The example uses:
- fixture-backed `ft_user`
- watch-only sharded `ft_order`
- TestNG listener-based executor injection
- Spring Boot auto-configured `ScenarioExecutor`
- row-level assertion for the inserted order row

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
