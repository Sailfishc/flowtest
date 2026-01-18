# AGENTS

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build all modules
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run tests for a specific module
mvn test -pl flowtest-demo

# Run a single test class
mvn test -pl flowtest-demo -Dtest=OrderServiceTest

# Run a single test method
mvn test -pl flowtest-demo -Dtest=OrderServiceTest#testNormalUserCreateOrder
```

## Architecture Overview

FlowTest is a code-first Java integration testing framework with a fluent DSL for database testing. It follows the Arrange-Act-Assert pattern with automatic test data generation and database change tracking.

### Module Structure

- **flowtest-core**: Core framework with fluent DSL, entity management, and snapshot diffing
- **flowtest-assertj-db**: AssertJ-DB integration for database assertions
- **flowtest-junit5**: JUnit 5 extension (`@FlowTest` annotation)
- **flowtest-spring-boot-starter**: Auto-configuration for Spring Boot applications
- **flowtest-demo**: Example application demonstrating framework usage

### Core Flow Pattern

Tests follow a fluent chain: `flow.arrange() → .add() → .persist() → .act() → .assertThat()`

```java
flow.arrange()
    .add(User.class, UserTraits.vip(), UserTraits.balance(100.00))
    .persist()
    .act(() -> service.doSomething(flow.get(User.class).getId()))
    .assertThat()
        .noException()
        .dbChanges(db -> db.table("t_order").hasNewRows(1));
```

### Key Components

- **TestFlow**: Main entry point, injected via `@Autowired`. Manages test context per thread.
- **ArrangeBuilder**: Builds test fixtures with `add()`, `addMany()`, supports aliases for multiple entities of same type
- **Trait<T>**: Functional interface for composable entity modifications. Traits are combined with varargs or `.and()`
- **AutoFiller**: Uses EasyRandom to auto-populate entity fields (excludes `@Id` fields)
- **SnapshotEngine**: Takes before/after database snapshots for change assertions
- **ActPhase/AssertPhase**: Execute business logic and provide fluent assertions

### Trait Pattern

Define reusable entity configurations in `*Traits` classes:

```java
public class UserTraits {
    public static Trait<User> vip() {
        return user -> user.setLevel(UserLevel.VIP);
    }
    public static Trait<User> balance(double amount) {
        return user -> user.setBalance(BigDecimal.valueOf(amount));
    }
    // Compose traits
    public static Trait<User> richVip() {
        return vip().and(balance(10000.00));
    }
}
```

### Entity Retrieval

```java
flow.get(User.class)              // First entity of type
flow.get("alias", User.class)     // By alias
flow.get(User.class, 0)           // By index
flow.getAll(User.class)           // All entities of type
```

### Test Setup

```java
@FlowTest                    // Enables FlowTest extension
@SpringBootTest              // Spring context
@Transactional               // Rollback after test
class MyServiceTest {
    @Autowired TestFlow flow;
    @Autowired MyService service;
}
```

## Common Issues & Lessons Learned

### 1. H2 Database Reserved Words

**Problem**: `user` and `order` are reserved words in H2, causing SQL syntax errors.

**Solution**: Use `t_` prefix for table names: `t_user`, `t_order`, `t_product`.

### 2. Enum Persistence in JDBC

**Problem**: JDBC `setObject()` serializes Java enums as binary objects, causing `Data conversion error`.

**Solution**: Convert enums to `String` using `.name()` before inserting.

### 3. H2 KeyHolder Returns Multiple Keys

**Problem**: H2's `GeneratedKeyHolder.getKey()` may throw exception when multiple columns are returned.

**Solution**: Use `getKeys()` and search for the ID column by name.

### 4. Package-Private Methods Across Packages

**Problem**: `TestContext` internal methods were package-private but accessed from other packages.

**Solution**: Make methods `public` when they need cross-package access.

### 5. AssertJ-DB Table API

**Problem**: `Changes.setTables(String...)` doesn't exist; it requires `Table[]`.

**Solution**: Convert table names to `Table` objects first.

### 6. LocalDateTime JDBC Conversion

**Problem**: `LocalDateTime` not directly supported by all JDBC drivers.

**Solution**: Convert to `java.sql.Timestamp` before inserting.

### 7. JUnit 5 @Nested Classes Cannot Find TestFlow

**Problem**: `FlowTestExtension.findTestFlow()` searches fields in the test instance class, but for `@Nested` inner classes, the `TestFlow` field is defined in the outer class, not the nested class itself.

**Solution**: Access the enclosing class instance via the synthetic `this$0` field that Java creates for non-static inner classes:

```java
private Object getEnclosingInstance(Object testInstance) {
    Class<?> clazz = testInstance.getClass();
    if (clazz.getEnclosingClass() == null) {
        return null;
    }
    Field enclosingField = clazz.getDeclaredField("this$0");
    enclosingField.setAccessible(true);
    return enclosingField.get(testInstance);
}
```

### 8. H2 AUTO_INCREMENT Not Reset After Rollback

**Problem**: When using `@Transactional` tests, H2's AUTO_INCREMENT counter does not reset after transaction rollback. This causes `SnapshotEngine` to incorrectly calculate new rows when using `MAX(id)` difference.

Example: Test A creates Order id=1, rollback. Test B's before snapshot has MAX(id)=null, creates Order id=2, after MAX(id)=2. Calculated newRows = 2 (wrong, should be 1).

**Solution**: Use `COUNT(*)` difference instead of `MAX(id)` difference to calculate new rows:

```java
long countBasedNewRows = Math.max(0, afterCount - beforeCount);
```

### 9. @Transactional vs SNAPSHOT_BASED Cleanup Conflict

**Problem**: Mixing `@Transactional` (auto-rollback) with `@FlowTest(cleanup = SNAPSHOT_BASED)` causes conflicts - both try to clean up data, and timing issues arise.

**Solution**: Choose one cleanup strategy:
- Use `@Transactional` for tests that can rely on rollback (most cases)
- Use `SNAPSHOT_BASED` with `@Transactional(propagation = NOT_SUPPORTED)` for tests that need real commits (e.g., testing with `FlowTestChanges`)

Never mix both in the same test.

### 10. Non-Transactional Tests Pollute Shared H2 Database

**Problem**: Tests with `@Transactional(propagation = NOT_SUPPORTED)` commit real data to H2. With `DB_CLOSE_DELAY=-1`, data persists across tests, causing pollution.

**Solution**:
1. Move non-transactional tests to separate `@Nested` classes
2. Always use `try-finally` to ensure cleanup:

```java
@Transactional(propagation = Propagation.NOT_SUPPORTED)
void testWithRealCommit() {
    Long baseline = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM t_order", Long.class);
    try {
        // test logic
    } finally {
        jdbcTemplate.update("DELETE FROM t_order WHERE id > ?", baseline);
        flow.cleanup();
    }
}
```

