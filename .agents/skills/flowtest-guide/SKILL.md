---
name: flowtest-guide
description: >
  Guide users through installing and using FlowTest V2 — a Java 8 observation-first database
  integration testing framework. Use this skill whenever the user asks about FlowTest setup,
  integration, writing test scenarios, fixture preparation, table observation, assertion patterns,
  cleanup strategies, multi-datasource configuration, sharding/dynamic table testing, or any
  question about how to use FlowTest V2 in their project. Also trigger when the user mentions
  "flowtest", "database integration test", "observation-first testing", or wants to write
  tests that verify both return values and database changes together.
---

# FlowTest V2 Integration & Best Practices Guide

You are helping users integrate and use **FlowTest V2** — an observation-first Java 8 database integration testing framework. The framework describes test scenarios with `given -> observe? -> when -> then -> run`, automatically handling snapshots, diffs, and cleanup.

## Core Mental Model

FlowTest V2 doesn't "create lots of data and guess what changed." Instead, it **declares what to observe**, executes business actions, and **verifies results and data changes together** in a single scenario.

The 4 concepts:
- **`given`**: Prepare fixture data (optional)
- **`observe`**: Declare tables/entities to observe (optional — auto-derived from `then(...)`)
- **`when`**: Execute business action
- **`then`**: Unified verification — return values, exceptions, fixture state, and database diffs

## Step 1: Choose Integration Approach

Ask the user about their tech stack, then recommend the right dependency combination.

**Priority order:**
1. Spring Boot + JUnit 5 (recommended for most projects)
2. Spring Boot + TestNG
3. Pure JUnit 5 (no Spring)

### Spring Boot + JUnit 5

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

### Spring Boot + TestNG

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
```

If the project uses JUnit by default but wants TestNG, remind them to configure Surefire:

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

### Pure JUnit 5 (No Spring)

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

## Step 2: Annotate the Test Class

### JUnit 5 + Spring Boot

```java
@SpringBootTest
@FlowTestV2Test  // This is all you need — no ScenarioExecutor injection required
class OrderFlowTest {
    // ScenarioExecutor is automatically resolved from Spring context
}
```

### TestNG + Spring Boot

```java
@SpringBootTest
@Listeners(FlowTestV2Listener.class)
public class OrderFlowTest extends AbstractTestNGSpringContextTests {
}
```

## Step 3: Register Tables for Observation

The Spring Boot starter auto-configures most beans, but you need to tell the framework which tables to observe and their primary key columns. Register a `JdbcObservationRegistry` bean:

```java
@TestConfiguration
class FlowTestConfig {
    @Bean
    JdbcObservationRegistry jdbcObservationRegistry() {
        return new JdbcObservationRegistry()
            .registerTable("ft_order", "id")
            .registerTable("ft_user", "id");
    }
}
```

## Step 4: Define Entity Classes

Annotate entity classes with `@JdbcEntity` so the framework knows the table mapping:

```java
@JdbcEntity(table = "ft_order", keyColumns = {"id"})
public class TestOrder {
    private Long id;
    private Long userId;
    private String productName;
    private Long totalPrice;
    private String status;

    // getters and setters
}
```

Fields are auto-filled with random data by Instancio (the default data filler). You only need to set the fields that matter for your test via `FixtureTrait`.

## Step 5: Write Your First Scenario

### Complete Example (given + when + then)

```java
@Test
void shouldCreateOrderAndDeductBalance() throws Exception {
    FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");

    FlowTestV2.scenario("create-order")
        .given(g -> g.fixture(user,
            FixtureTrait.mutate(v -> v.setId(1L)),
            FixtureTrait.mutate(v -> v.setTenantId(100L)),
            FixtureTrait.mutate(v -> v.setBalance(100L))))
        .observe(o -> o.table("ft_order", r -> r.route("tenant_id", 100L)))
        .when(() -> orderService.createOrder(100L, 1L, 20L))
        .then(t -> t
            .success()
            .returns(10L)
            .fixture(user, u -> u
                .after(v -> assertThat(v.getBalance()).isEqualTo(80L)))
            .table("ft_order", order -> order
                .inserted(1)
                .inspect(ctx -> {
                    assertThat(ctx.insertedOne().getColumn("status")).isEqualTo("CREATED");
                })))
        .run();
}
```

### Act-Only Example (no fixture preparation)

When the business logic creates data from scratch, skip `given(...)`:

```java
@Test
void shouldInsertNewOrder() throws Exception {
    FlowTestV2.scenario("act-only-order")
        .when(() -> orderService.createOrder(100L, 1L, 20L))
        .then(t -> t
            .success()
            .table("ft_order", order -> order.inserted(1)))
        .run();
}
```

### Exception Verification

```java
@Test
void shouldRejectInsufficientBalance() throws Exception {
    FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");

    FlowTestV2.scenario("insufficient-balance")
        .given(g -> g.fixture(user,
            FixtureTrait.mutate(v -> v.setId(1L)),
            FixtureTrait.mutate(v -> v.setBalance(5L))))
        .when(() -> orderService.createOrder(100L, 1L, 20L))
        .then(t -> t.exception(InsufficientBalanceException.class))
        .run();
}
```

## Best Practices

### 1. Let `then(...)` drive observation — avoid redundant `observe(...)`

Resources referenced in `then(...)` are auto-derived as observation targets. Only use explicit `observe(...)` when you need:
- Routing conditions: `.route("tenant_id", 100L)`
- Dynamic table names: `.dynamicTableBy("bucket", "a")`
- Observing a table without asserting on it in `then(...)`

```java
// Good: auto-derived, no observe needed
.when(() -> service.doSomething())
.then(t -> t.success().table("ft_order", order -> order.inserted(1)))
.run();

// Good: observe needed because of routing
.observe(o -> o.table("ft_order", r -> r.route("tenant_id", 100L)))
.when(() -> service.doSomething())
.then(t -> t.success().table("ft_order", order -> order.inserted(1)))
.run();
```

### 2. Use `FixtureTrait` for business semantics — only set what matters

The framework auto-fills all fields with random data. Only override the fields your test cares about:

```java
// Good: only set fields relevant to the test scenario
.given(g -> g.fixture(user,
    FixtureTrait.mutate(v -> v.setBalance(100L))))

// Bad: setting every field manually defeats auto-fill
.given(g -> g.fixture(user,
    FixtureTrait.mutate(v -> {
        v.setId(1L);
        v.setName("test");
        v.setEmail("test@test.com");  // irrelevant to balance test
        v.setAge(25);                 // irrelevant to balance test
        v.setBalance(100L);
    })))
```

### 3. Extract reusable traits as methods

When the same trait appears across multiple tests, extract it:

```java
// Define reusable traits
private FixtureTrait<TestUser> withBalance(long amount) {
    return FixtureTrait.mutate(v -> v.setBalance(amount));
}

private FixtureTrait<TestUser> withTenant(long tenantId) {
    return FixtureTrait.mutate(v -> v.setTenantId(tenantId));
}

// Use them concisely
.given(g -> g.fixture(user, withTenant(100L), withBalance(100L)))
```

### 4. Name scenarios meaningfully

Scenario names appear in test output and error messages. Use descriptive names:

```java
// Good
FlowTestV2.scenario("create-order-with-vip-discount")
FlowTestV2.scenario("reject-order-insufficient-inventory")

// Bad
FlowTestV2.scenario("test1")
FlowTestV2.scenario("order")
```

### 5. One scenario per behavior

Each test method should verify one business behavior:

```java
// Good: focused on one behavior
@Test
void shouldApplyVipDiscount() throws Exception { ... }

@Test
void shouldRejectExpiredCoupon() throws Exception { ... }

// Bad: testing multiple behaviors in one scenario
@Test
void shouldHandleVariousOrderScenarios() throws Exception { ... }
```

### 6. Use `FixtureHandle` for fixture reference in assertions

Named handles let you reference fixture state in `then(...)`:

```java
FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");

// Reference in then(...) to verify before/after state
.then(t -> t.fixture(user, u -> u
    .after(v -> assertThat(v.getBalance()).isEqualTo(80L))))
```

### 7. H2 table naming — avoid reserved words

H2 reserves `user`, `order`, etc. Always prefix test tables:

```java
// Good
@JdbcEntity(table = "ft_order", keyColumns = {"id"})
@JdbcEntity(table = "ft_user", keyColumns = {"id"})

// Bad — will fail on H2
@JdbcEntity(table = "order", keyColumns = {"id"})
@JdbcEntity(table = "user", keyColumns = {"id"})
```

## Advanced: Multi-DataSource Configuration

For projects with multiple databases, configure routing in `application-test.yml`:

```yaml
flowtest.v2:
  datasource:
    default-name: primaryDs
    bindings:
      - name: primaryDs
        tables:
          - ft_order
          - ft_user
      - name: secondaryDs
        tables:
          - ft_audit_log
```

## Advanced: Dynamic Table & Sharding

For sharded or dynamic-table scenarios, use routing in `observe(...)`:

```java
FlowTestV2.scenario("sharded-order")
    .observe(o -> o.table("ft_order", r -> r
        .dynamicTableBy("bucket", "a")          // physical table: ft_order_a
        .route("tenant_id", 100L)))             // SQL routing condition
    .when(() -> orderService.createOrder(100L, 1L, 20L))
    .then(t -> t.success().table("ft_order", order -> order.inserted(1)))
    .run();
```

## Troubleshooting

When users encounter issues, check these common causes:

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| `Table not found` | H2 reserved word used as table name | Use `ft_` prefix |
| `ScenarioExecutor not resolved` | Missing `@FlowTestV2Test` or Spring context | Add annotation, check Spring config |
| Enum insertion fails | JDBC binary serialization | Ensure `@JdbcEntity` mapping is correct |
| Observation shows no changes | Table not registered | Register in `JdbcObservationRegistry` |
| Auto-fill generates bad data | ID fields not cleared | Use `@JdbcEntity(keyColumns = {...})` |

## Compatibility Notes

- **Java version**: Java 8+ (the framework targets Java 8)
- **Spring Boot**: 2.7.x (Spring Boot 2 series)
- **Instancio**: 5.5.1 (last Java 8 compatible version — do NOT upgrade to 6.0+)
- **EasyRandom**: 4.3.0 (last Java 8 compatible version, opt-in via `flowtest.v2.data-filler=easyrandom`)
