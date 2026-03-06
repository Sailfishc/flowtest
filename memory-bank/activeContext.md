# Active Context

## Current Focus
The `v2` core runtime is now executable. The immediate focus should shift from model scaffolding to production-hardening and integration layers.

## Recent Changes
- Added generic observation SPI and identity-based diff model.
- Added JDBC observation executor with route-aware snapshot capture and `DELETE_INSERTED` cleanup.
- Added fixture execution SPI and JDBC fixture executor with adapter registry.
- Added runtime orchestration for `given -> baseline -> act -> diff -> expectations -> cleanup`.
- Added `flowtest-v2-cases` with H2-backed act-only and mixed-scenario tests.
- Implemented `RESTORE_BEFORE_IMAGE` in the JDBC observation executor, including inserted-row deletion plus deleted-row reinsert and modified-row restore.
- Added H2-backed restore tests in both `observe-rdbms` and `cases` modules for watch-only update/delete scenarios.
- Added `flowtest-v2-junit5` with parameter resolution and explicit builder registration.
- Added `flowtest-v2-testng` with listener-based executor injection.
- Added a complete Spring Boot + TestNG example in `flowtest-v2-testng`, using `AbstractTestNGSpringContextTests`, starter auto-configuration, listener injection, H2, and row-level assertions.
- Added `flowtest-v2-spring-boot-starter` that auto-configures JDBC registry/executor beans and `ScenarioExecutor`.
- Added convention-based JDBC fixture adapter generation from `JdbcObservationRegistry.registerEntity(...)`, with optional per-property overrides and ignores.
- Added annotation-based entity mapping via `@JdbcEntity`, `@JdbcColumn`, and `@JdbcIgnore`, plus `registerEntity(Class<?>)` support.
- Added low-level `JdbcFixtureExecutor(DataSource, JdbcObservationRegistry)` support so manual wiring also gets auto-generated fixture adapters.
- Added row-level change assertions (`insertedRow`, `deletedRow`, `modifiedRow`, and `change(...)`) plus helper DSL classes.
- Added a `flowtest-v2` integration guide covering JUnit 5, TestNG, Spring Boot, row-level assertions, and the copyable Spring Boot + TestNG example.
- Added `FlowTestDataSourceRegistry` with exact-table and glob-pattern datasource bindings.
- Added `MultiDataSourceJdbcObservationExecutor` plus multi-datasource routing in `JdbcFixtureExecutor`.
- Added Spring Boot property binding for `flowtest.v2.datasource.default-name` and `flowtest.v2.datasource.bindings[*]`.
- Added Spring Boot starter and TestNG examples for multi-datasource scenarios driven by configuration properties.
- Added dynamic table name support. Logical tables can now resolve to physical tables like `ft_order_a` / `ft_order_b` from route or entity field values.
- Added `@JdbcDynamicTable`, `DynamicTableNameResolver`, and registry builder methods such as `.table(...).dynamicByColumn(...)`.
- Added dynamic-table-aware fixture adapter generation and multi-datasource routing based on the resolved physical table name.

## Active Decisions
- Default cleanup in `v2` is `DELETE_INSERTED`, not `ROLLBACK`, because base runtime does not own transaction boundaries.
- Traits belong only to fixture construction.
- Route scope must be explicit for sharded observations.
- Registered entity metadata is now the default source for both observation and simple JDBC fixture persistence.
- The same registered entity metadata now works across Spring Boot auto-config, JUnit5 builder wiring, and low-level manual JDBC wiring.
- `RESTORE_BEFORE_IMAGE` cleanup runs route-aware SQL and is now the recommended policy for watch-only scenarios that mutate existing rows.
- Datasource selection is now an infrastructure concern, configured once by table name/pattern, not a per-scenario DSL concern.
- Route scope and datasource routing stay separate: datasource is resolved by table binding first, then route conditions are applied to SQL.
- Dynamic table resolution is a table-metadata concern: the scenario still uses the logical table name, while runtime resolves the physical table from route or entity values.
- Dynamic fixture persistence/reload/delete is supported, but fixture-backed observation on dynamic tables should use explicit route-based observation instead of bare `observe.fixture(handle)`.

## Next Likely Steps
- Consider transaction-aware `ROLLBACK` support for Spring-managed tests.
- Add transaction-aware examples and utilities for Spring-managed rollback workflows.
- Decide whether row-level assertion DSL needs stronger field/path matchers beyond simple column equality.
- Decide whether datasource routing also needs entity-name bindings in addition to table names and glob patterns.
