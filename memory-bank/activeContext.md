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
- Added dynamic table name support. Logical tables can now resolve to physical tables like `ft_order_a` / `ft_order_b` from explicit `TableRouteScope` values or entity properties.
- Added `TableRouteScope` / `TableRouteValue` so physical table routing is independent from SQL `RouteScope`.
- Added `@JdbcDynamicTable(property=...)`, `DynamicTableNameResolver`, `DynamicTableRule`, and registry builder methods such as `.table(...).dynamicByKey(...)` and `.entity(...).dynamicByProperty(...)`.
- Added dynamic-table-aware fixture adapter generation and multi-datasource routing based on the resolved physical table name.
- Added pluggable ignore-property resolution with built-in support for `@JdbcIgnore`, JPA/Jakarta `@Transient`, Spring Data `@Transient`, and MyBatis-Plus `@TableField(exist = false)`.
- Added a Spring Boot + TestNG + MyBatis-Plus example that uses `BaseMapper`, `DynamicTableNameInnerInterceptor`, and FlowTest dynamic-table observation against logical table names.
- `JdbcObservationRegistry.registerEntity(Class<?>)` now resolves entity metadata from MyBatis-Plus annotations (`@TableName`, `@TableId`, `@TableField`) and falls back to conventions when `@JdbcEntity` is absent.
- Added a second Spring Boot + TestNG example for MyBatis-Plus dynamic tables across multiple datasources.
- Added a dedicated zero-to-one user manual at `docs/flowtest-v2-user-manual.md`, structured around dependency setup, registry wiring, first scenario, Spring Boot/TestNG/JUnit 5 integration, cleanup policy selection, sharding, dynamic tables, multi-datasource routing, and MyBatis-Plus examples.
- Expanded the user manual with a scenario comparison table and a dedicated Traits best-practices section covering inline traits, domain trait libraries, composition, and `TraitContext` usage for fixture relationships.
- Added a new context-style runtime verification DSL via `.verify(ctx -> { ... })`, exposing `result`, `failure`, fixture `before/after`, and resource-level diff access in one place.
- Updated the primary Spring Boot + TestNG example and the user manual to recommend `.verify(ctx -> { ... })` over fragmented `.then(...)` assertions for mixed scenarios.
- Added a new high-level `.watch(...)` DSL with resource-oriented chaining (`fixture(...)`, `table(...)`, `entity(...)`, `.route(...)`, `.dynamicTableBy(...)`) to reduce overload-heavy `observe(...)` usage.
- Added thread-bound executor support through `ScenarioExecutors` plus `.run()` on `ScenarioPlan`/`CompiledScenario`, so JUnit 5 and TestNG integrations can execute scenarios without explicit `.execute(executor)` in the common path.
- Updated JUnit 5 and TestNG integrations to bind the active `ScenarioExecutor` into the current thread before each test and clear it afterward.
- Updated JUnit 5, TestNG, and Spring Boot example tests to use `.watch(...) + .verify(...) + .run()` as the primary DSL path.
- Updated JUnit 5 and TestNG integrations to resolve `ScenarioExecutor` from Spring `ApplicationContext` reflectively, so Spring Boot tests no longer need to implement `ScenarioExecutorProvider` in the recommended path.
- Updated the user manual, integration guide, and Spring Boot example tests to remove `ScenarioExecutorProvider` from recommended Spring Boot usage; it remains only as a deprecated compatibility fallback.
- Tightened fixture-backed observation enrichment so dynamic-table fixtures derive both physical-table route and complete identity route predicates, including composite-key handling and regression coverage.
- Updated JDBC observation resolution so typed watch-only resources (`watch(w -> w.entity(Foo.class))`) auto-register entity metadata on first use rather than requiring upfront `registerEntity(Foo.class)`.
- Updated Spring Boot, JUnit 5, TestNG, and integration-guide examples to remove redundant `registerEntity(...)` calls where the entity is only used via fixture-backed or typed observation paths.
- Added a new Spring Boot + TestNG reference example for the common mixed scenario: single-table fixture data in `given(...)`, empty sharded dynamic table before `act`, and act-only insert into the resolved physical shard table.
- Added a paired typed-entity version of that reference example so docs can now contrast `watch(...table(...))` versus `watch(...entity(...))` for the same sharded dynamic-table business flow.
- Expanded the user manual with a practical database-assertion cookbook covering inserted / modified / deleted / multi-row verification and when to prefer `.verify(...)` versus `.then(...)`.
- Rewrote the root `README.md` as a V2-first onboarding document, centered on `watch(...) + verify(ctx -> { ... }) + run()`, with V1 explicitly positioned as legacy.
- Added fixture whole-state verification through `ctx.fixture(handle).matchesAfter(FixtureStatePatch.of(...).set(...).ignore(...))`, backed by fixture metadata so unmanaged/ignored properties can stay out of comparison.
- Added type-safe getter-reference support for `FixtureStatePatch`, so the recommended form is now `.set(Foo::getBar, ...)` / `.ignore(Foo::getBaz)` rather than string property names.
- Added alias-first fixture DSL: `persist("user", User.class, ...)`, `watch(w -> w.fixture("user"))`, `ctx.fixture("user", User.class)`, and `TraitContext.fixture("user", User.class)` now cover the recommended fixture reference path without exposing `FixtureHandle`.
- Added `persistRows(Class<T>, rows -> rows.defaults(...).row("a", ...).row("b", ...))` for same-entity multi-row fixture setup with shared defaults plus per-row overrides.
- Added compile-time fixture-alias resolution for `watch(w -> w.fixture("alias"))`, so alias-based watch declarations can be compiled against the final fixture set.

## Active Decisions
- Default cleanup in `v2` is `DELETE_INSERTED`, not `ROLLBACK`, because base runtime does not own transaction boundaries.
- Traits belong only to fixture construction.
- Route scope must be explicit for sharded observations.
- Registered entity metadata is now the default source for both observation and simple JDBC fixture persistence.
- The same registered entity metadata now works across Spring Boot auto-config, JUnit5 builder wiring, and low-level manual JDBC wiring.
- `RESTORE_BEFORE_IMAGE` cleanup runs route-aware SQL and is now the recommended policy for watch-only scenarios that mutate existing rows.
- Datasource selection is now an infrastructure concern, configured once by table name/pattern, not a per-scenario DSL concern.
- Route scope and datasource routing stay separate: datasource is resolved by table binding first, then route conditions are applied to SQL.
- Dynamic table resolution is a table-metadata concern and now has its own `TableRouteScope`; the scenario still uses the logical table name, while runtime resolves the physical table before SQL routing or datasource lookup.
- Dynamic fixture persistence/reload/delete is supported even when the dynamic property is entity-only and ignored from DB mapping, but fixture-backed observation on dynamic tables should still use explicit `tableRouteScope` rather than bare `observe.fixture(handle)`.
- MyBatis-Plus integration is currently demonstrated at the example level: entity classes can carry both FlowTest and MyBatis-Plus annotations, while dynamic table execution uses the MyBatis-Plus interceptor and FlowTest independently resolves the same logical table for observation/cleanup.
- For MyBatis-Plus entities, `@JdbcEntity` is no longer required in common cases; `@JdbcDynamicTable` still remains the explicit hook for FlowTest physical-table resolution.
- The recommended documentation reading order is now: `flowtest-v2-user-manual.md` first, then `flowtest-v2-integrations.md` for integration-specific details, then `flowtest-v2-architecture.md` for model rationale.
- The current recommended user-facing assertion path is context-based verification (`.verify(ctx -> { ... })`); the older declarative `.then(...)` API remains for compatibility and simple count-style checks.
- For fixture-backed mutation scenarios where the intent is “only these fields changed”, whole-state verification should now prefer `matchesAfter(...)` over manually asserting each unchanged field.
- Within `matchesAfter(...)`, getter method references are now the preferred property selector because they are refactor-safe and make patch definitions less brittle.
- The current recommended user-facing declaration path is `.watch(...) + .verify(ctx -> { ... }) + .run()` under test-framework integration; the older `.observe(...) + .then(...)` and explicit `.execute(executor)` path remains as a lower-level compatibility layer.
- The current recommended fixture declaration path is alias-first: use `persist("alias", ...)`, `persistRows(...)`, alias-based `watch(...)`, alias-based `VerifyContext`, and alias-based `TraitContext`; keep `FixtureHandle` only as a compatibility/lower-level API.
- The registry should now be explained primarily as an override/configuration point for watch-only tables and non-standard mappings; typed entity observations should default to annotation/convention inference.
- For Spring Boot integration, the recommended path is now zero-provider: `@FlowTestV2Test` for JUnit 5 or `@Listeners(FlowTestV2Listener.class)` for TestNG, then `.run()`; `ScenarioExecutorProvider` is compatibility-only.
- Fixture aliases are now scenario-global identifiers; duplicate alias names across fixtures are rejected during compilation.

## Next Likely Steps
- Consider transaction-aware `ROLLBACK` support for Spring-managed tests.
- Add transaction-aware examples and utilities for Spring-managed rollback workflows.
- Decide whether row-level assertion DSL needs stronger field/path matchers beyond simple column equality.
- Decide whether datasource routing also needs entity-name bindings in addition to table names and glob patterns.
- Decide whether runtime should auto-derive `TableRouteScope` from fixture-backed dynamic entities so `observe.fixture(handle)` can work without extra table-route declarations.
- Continue DSL cleanup after `run()`: dynamic-table naming is now softer via `.dynamicTableBy(...)`, but low-level `TableRouteScope` and compatibility aliases are still visible and could be pushed further behind the fluent API.
