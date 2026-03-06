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

FlowTest is a code-first Java 8 integration testing framework with a fluent DSL for database testing. It follows Arrange-Act-Assert with automatic test data generation and database change tracking.

### Module Dependency Graph

```
flowtest-core  (foundation — no FlowTest module deps)
  ├── flowtest-assertj-db    (+ AssertJ-DB)
  ├── flowtest-junit5        (+ JUnit Jupiter)
  ├── flowtest-testng        (+ TestNG)
  ├── flowtest-mockito       (+ Mockito)
  └── flowtest-spring-boot-starter  (aggregates core + junit5 + assertj-db)
```

The demo module lives in a separate repository.

### Core Flow Pattern

```java
flow.arrange()
    .add(User.class, UserTraits.vip(), UserTraits.balance(100.00))
    .persist()
    .act(() -> service.doSomething(flow.get(User.class).getId()))
    .assertThat()
        .noException()
        .dbChanges(db -> db.table("t_order").hasNewRows(1));
```

### Key Components (flowtest-core)

| Component | Package | Role |
|-----------|---------|------|
| `TestFlow` | `core` | Main entry point. Uses `ThreadLocal<TestContext>` for per-test isolation. |
| `ArrangeBuilder` | `core.fixture` | Fluent builder for `add()`, `addMany()`, `persist()`, `build()`. |
| `Trait<T>` | `core.fixture` | Functional interface for composable entity modifications via `.and()` / `Trait.compose()`. |
| `DataFiller` | `core.fixture` | Interface for auto-populating entity fields. Implementations: `AutoFiller` (EasyRandom), `InstancioFiller` (Instancio). |
| `EntityMetadata` | `core.fixture` | Reflection-based metadata extraction. Supports both JPA (`@Table`, `@Id`, `@Column`) and MyBatis-Plus (`@TableName`, `@TableId`, `@TableField`) annotations. |
| `IdStrategy` | `core.fixture` | Enum: `AUTO`, `INPUT`, `ASSIGN_ID`, `ASSIGN_UUID`. |
| `EntityPersister` | `core.persistence` | Interface for DB insert/delete. `JdbcEntityPersister` uses Spring JdbcTemplate with PreparedStatement. |
| `SnapshotEngine` | `core.snapshot` | Captures before/after DB state. PK detection: user config → entity metadata → JDBC metadata → fallback "id". Also provides `findNewPrimaryKeys()` and `deleteRowsByPrimaryKeys()` for cleanup. |
| `CleanupStrategy` | `core.lifecycle` | Interface with 4 implementations matching `CleanupMode` enum. |
| `ActPhase` / `AssertBuilder` | `core.assertion` | Execute business logic, then fluent assertions (exception, returnValue, dbChanges, entity state, newRow). |

### Spring Boot Auto-Configuration

`FlowTestAutoConfiguration` (registered via `META-INF/spring.factories`) creates:
- `DataFiller` — `InstancioFiller` by default, `AutoFiller` when `flowtest.data-filler=easyrandom`
- `JdbcEntityPersister` — from DataSource
- `SnapshotEngine` — from DataSource + properties
- `TestFlow` — wires the above together

Config properties prefix: `flowtest.*` (cleanup-mode, clean-act-data, seed, string-length-min/max, collection-size-min/max, randomization-depth, snapshot-tables, id-column-name, data-filler).

### Persistence Internals

**Table name resolution order:** `@Table(name)` → `@TableName(value)` → `@Entity(name)` → CamelCase→snake_case.

**ID field resolution order:** `@Id` (JPA) → `@TableId` (MyBatis-Plus) → field named "id".

**Value conversions in `JdbcEntityPersister.convertValue()`:** Enum→`.name()`, `LocalDateTime`→`Timestamp`, `LocalDate`→`java.sql.Date`.

**Generated key retrieval:** Uses `getKeys()` map and searches by column name (not `getKey()`) because H2 may return multiple columns.

### Thread Safety

- `TestFlow`: `ThreadLocal<TestContext>` for per-test isolation
- `JdbcEntityPersister`: `ConcurrentHashMap` for metadata cache
- `SnapshotEngine`: `ConcurrentHashMap` for table primary key cache

### JUnit 5 / TestNG `@Nested` Class Support

Both extensions handle `@Nested` inner classes by traversing to the enclosing instance via the synthetic `this$0` field to find the `TestFlow` field defined in the outer class.

### Cleanup Architecture

Cleanup runs after each test to remove test data. Four modes are available via `@FlowTest(cleanup = ...)`:

| Mode | Algorithm | Cleans arrange data | Cleans act data | PK type requirement |
|------|-----------|:-:|:-:|:-:|
| `TRANSACTION` (default) | Spring `@Transactional` rollback | ✅ | ✅ | None |
| `COMPENSATING` | Delete by `persistedIds` | ✅ | ❌ default / ✅ `cleanActData=true` | None |
| `SNAPSHOT_BASED` | Before/after PK set comparison | ✅ | ✅ | None (any PK type) |
| `NONE` | No-op | ❌ | ❌ | — |

**Row-data-based cleanup algorithm** (used by `SNAPSHOT_BASED` and `COMPENSATING` with `cleanActData=true`):
1. `beforeTest()`: call `SnapshotEngine.takeBeforeSnapshot()` — captures all rows indexed by primary key, stored in `TestContext.cleanupBeforeSnapshot`
2. `afterTest()`: call `SnapshotEngine.takeAfterSnapshot()` — captures current state
3. Compare primary key sets via `SnapshotEngine.findNewPrimaryKeys(before, after)` — works with any PK type (numeric, UUID, string)
4. Delete new rows via `SnapshotEngine.deleteRowsByPrimaryKeys()` (batched `DELETE WHERE pk IN (...)`)
5. Then delete `persistedIds` entities via `EntityPersister.deleteAll()`

**Key design decisions:**
- All ID column resolution is centralized in `SnapshotEngine.getIdColumnForTable()` (4-layer priority: user config → entity metadata → JDBC metadata → fallback "id")
- `ArrangeBuilder.recordCleanupSnapshot()` stores full `TableSnapshot` (not just `MAX(ID)`)
- `TestFlow.cleanup()` takes a before snapshot on-demand if none exists, then delegates to `SnapshotBasedCleanup`
- `CompensatingCleanup` accepts optional `(SnapshotEngine, cleanActData)` to bridge the gap between L1 (transaction) and L3 (snapshot-based)
- `@FlowTest` annotation has `cleanActData` attribute (default `false`), only effective in `COMPENSATING` mode
- Old `TestContext.cleanupSnapshot` (`Map<String, Object>` of MAX IDs) is `@Deprecated`; new field is `cleanupBeforeSnapshot` (`Map<String, TableSnapshot>`)

## Common Issues & Lessons Learned

### H2 Database Reserved Words
`user` and `order` are reserved in H2. Use `t_` prefix: `t_user`, `t_order`, `t_product`.

### Enum Persistence in JDBC
JDBC `setObject()` serializes Java enums as binary. Convert enums to `String` via `.name()` before inserting.

### H2 AUTO_INCREMENT Not Reset After Rollback
H2's AUTO_INCREMENT counter persists across rollbacks. Use `COUNT(*)` difference (not `MAX(id)`) to calculate new rows in `SnapshotEngine`.

### @Transactional vs SNAPSHOT_BASED Cleanup Conflict
Never mix `@Transactional` rollback with `SNAPSHOT_BASED`/`COMPENSATING` cleanup — they conflict. Use `@Transactional` for most tests. Use `SNAPSHOT_BASED` or `COMPENSATING(cleanActData=true)` with `@Transactional(propagation = NOT_SUPPORTED)` when real commits are needed.

### Non-Transactional Tests Pollute Shared H2
Tests with `NOT_SUPPORTED` propagation commit real data. Preferred approach: use `SNAPSHOT_BASED` or `COMPENSATING` with `cleanActData=true` for automatic cleanup. For manual control, use `try-finally`:

```java
// Preferred: automatic cleanup via annotation
@FlowTest(cleanup = CleanupMode.SNAPSHOT_BASED)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
void testWithRealCommit() {
    // all data (arrange + act) cleaned up automatically after test
}

// Alternative: COMPENSATING with act data cleanup
@FlowTest(cleanup = CleanupMode.COMPENSATING, cleanActData = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
void testWithRealCommit() {
    // arrange + act data cleaned up automatically
}

// Manual: try-finally pattern
void testWithManualCleanup() {
    try {
        // test logic
    } finally {
        flow.cleanup();  // takes before snapshot on-demand, then cleans up
    }
}
```

### LocalDateTime JDBC Conversion
Convert `LocalDateTime` to `java.sql.Timestamp` before inserting (handled by `JdbcEntityPersister`).

### AssertJ-DB Table API
`Changes.setTables(String...)` doesn't exist — convert table names to `Table` objects first.

### Java 8 Compatibility
This project targets Java 8. EasyRandom 4.3.0 and Instancio 5.5.1 are the last versions supporting Java 8 (Instancio 6.0+ requires Java 17). Do not upgrade these dependencies beyond these versions.
