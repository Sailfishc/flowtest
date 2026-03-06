# Progress

## What Works
- `v2` module layout is in place and builds cleanly.
- Scenario compiler validates core structural rules.
- Traits, fixture handles, route scope, and observation scope are modeled.
- JDBC snapshot/diff/cleanup works for inserted-row cleanup.
- JDBC cleanup now also supports `RESTORE_BEFORE_IMAGE` for inserted, modified, and deleted rows.
- Runtime executes expectations for outcome, fixture state, and change counts.
- Runtime now supports row-level inserted/deleted/modified assertions and custom full-diff assertions.
- Runtime now also supports context-style verification through `.verify(ctx -> { ... })`, with access to action result/failure, fixture before/after state, and resource diff collections.
- `@JdbcEntity`, `@JdbcColumn`, and `@JdbcIgnore` can now define entity mapping by annotation, with `JdbcObservationRegistry.registerEntity(Class<?>)` loading that metadata.
- JUnit 5 integration resolves `ScenarioExecutor` via extension builder or provider interface.
- JUnit 5 builder now auto-generates simple fixture adapters from registered entity metadata, so fixture scenarios do not require handwritten JDBC adapters in common cases.
- TestNG integration injects `ScenarioExecutor` via listener and annotated fields.
- TestNG module now contains a complete Spring Boot + TestNG example that runs under the TestNG provider and demonstrates starter-based wiring end to end.
- Spring Boot starter auto-configures JDBC registries, executors, and `ScenarioExecutor`.
- Spring Boot starter now auto-generates fixture adapters for registered entities and supports property-level override/ignore metadata.
- Manual `JdbcFixtureExecutor` wiring now also accepts `JdbcObservationRegistry` directly and auto-generates fixture adapters from it.
- Spring Boot starter now supports multi-datasource routing through `flowtest.v2.datasource.default-name` and `flowtest.v2.datasource.bindings[*]`.
- JDBC observation and fixture execution can now route by table name across multiple `DataSource` beans using exact bindings or glob patterns.
- JDBC observation and fixture execution now support dynamic table names, with logical resources resolving to physical tables by explicit `TableRouteScope` values or entity properties.
- `JdbcObservationRegistry` now supports `.table(...).dynamicByKey(...)`, `.entity(...).dynamicByProperty(...)`, and annotation-based dynamic entity mapping via `@JdbcDynamicTable(property = ...)`.
- Dynamic table routing and SQL routing are now modeled separately: `TableRouteScope` resolves the physical table, while `RouteScope` still drives SQL shard predicates.
- Ignore metadata is now extensible through `JdbcIgnorePropertyResolver`, with built-in compatibility for `@JdbcIgnore`, JPA/Jakarta `@Transient`, Spring Data `@Transient`, and MyBatis-Plus `@TableField(exist = false)`.
- `flowtest-v2-testng` now includes a Spring Boot + TestNG + MyBatis-Plus dynamic-table example using `BaseMapper` and `DynamicTableNameInnerInterceptor`.
- `JdbcObservationRegistry.registerEntity(Class<?>)` now supports MyBatis-Plus metadata directly, including table name, key column, explicit field mapping, and ignored transient fields.
- `flowtest-v2-testng` now also includes a Spring Boot + TestNG + MyBatis-Plus + dynamic-table + multi-datasource example.
- TestNG module now contains a second complete example for Spring Boot + TestNG + multi-datasource routing.
- H2-backed case module proves act-only, mixed fixture/watch-only, and watch-only restore flows.
- Docs now include both a dedicated `flowtest-v2` integrations guide and a zero-to-one user manual, with the integrations guide linking back to the manual as the primary entry point.
- The user manual now also documents scenario classification (`act-only`, mixed, sharded, dynamic-table, multi-datasource) and recommended Traits organization patterns.
- The primary user-facing example and manual now recommend `.verify(ctx -> { ... })` for mixed scenarios instead of stacking `fixture(...)`, `modified(...)`, `inserted(...)`, and external result assertions.

## Validation
- Main `v2` command: `mvn -f flowtest-v2/pom.xml test`
- Latest known result: passing.

## What Is Not Done Yet
- `ROLLBACK` needs an external transaction-aware executor.
- `CUSTOM_COMPENSATOR` is not implemented.
- There is no dedicated Spring Test/TestNG transaction bridge yet for rollback-style cleanup.
- There is no transaction-aware `ScenarioExecutor` wrapper yet for `CleanupPolicy.ROLLBACK`.
- Observation DSL still needs simplification; resource declaration remains overload-heavy even after verification DSL was improved.

## Known Constraints
- Simple JDBC fixtures can now use convention-based adapter generation, but special persistence behavior still requires a custom adapter.
- Sharded observation requires route scope before action execution.
- Multi-datasource routing currently binds by table name and glob pattern; entity-name bindings are not implemented.
- Bare fixture-backed observation does not infer dynamic table routes automatically; dynamic entities should currently be observed with explicit `TableRouteScope`.
- `RESTORE_BEFORE_IMAGE` assumes rows are fully writable from snapshot columns and does not yet handle database-generated or non-updatable columns specially.
