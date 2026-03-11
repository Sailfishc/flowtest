---
name: flowtest-v2
description: Integrate FlowTest V2 into Java 8 test projects and author or migrate database integration tests with the latest observation-first DSL. Use when adding FlowTest V2 dependencies and wiring, writing scenario tests with the current given/observe/when/then/run flow, configuring routes or dynamic tables or multi-datasource observation.
---

# FlowTest V2

## Overview

This skill helps integrate FlowTest V2 and write tests with the current V2 API. The recommended DSL shape is `given -> observe? -> when -> then -> run`, where `then(...)` is the sole verification entry point with block-scoped resource assertions.

## Use This Skill When

- A user wants to add FlowTest V2 to a Java or Spring Boot test project.
- A user wants a new FlowTest scenario for database side effects, fixture-backed flows, sharded tables, dynamic tables, or multi-datasource tests.
- A user wants to migrate older FlowTest V2 examples to the latest DSL.
- A user has a failing FlowTest test and the likely cause is route scope, table routing, cleanup policy, or registry wiring.

## Workflow

### 1. Pick the integration path first

Choose the smallest path that matches the project:

- Spring Boot + JUnit 5: use `flowtest-v2-junit5` and `flowtest-v2-spring-boot-starter`, annotate tests with `@FlowTestV2Test`, then finish scenarios with `.run()`.
- Spring Boot + TestNG: use `flowtest-v2-testng` and `flowtest-v2-spring-boot-starter`, annotate tests with `@Listeners(FlowTestV2Listener.class)`, then finish scenarios with `.run()`.
- Plain JUnit 5 or manual JDBC wiring: use `flowtest-v2-junit5` or construct `ScenarioExecutor` manually.

If you need exact dependency or wiring patterns, read [references/integration-recipes.md](references/integration-recipes.md).

### 2. Default to the latest DSL

Prefer this shape unless the user explicitly asks for the lower-level compatibility API:

```java
FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");

FlowTestV2.scenario("create-order")
    .given(g -> g.fixture(user,
        FixtureTrait.mutate(v -> v.setId(1L)),
        FixtureTrait.mutate(v -> v.setTenantId(100L)),
        FixtureTrait.mutate(v -> v.setBalance(100L))))
    .observe(o -> o.table("ft_order", r -> r.route("tenant_id", 100L)))
    .when(() -> orderService.createOrder(...))
    .then(t -> t
        .success()
        .fixture(user, u -> u.after(v -> assertThat(v.getBalance()).isEqualTo(80L)))
        .table("ft_order", order -> order.inserted(1)))
    .run();
```

Default rules:

- Prefer `given -> observe? -> when -> then -> run`.
- `then(...)` is the sole verification entry point; old `verify(...)` is removed.
- Resources mentioned in `then(...)` are auto-inferred for observation; `observe(...)` is only needed for route conditions, dynamic tables, or inspect-only resources.
- Use block-scoped assertions: `table("name", t -> t.inserted(1))` instead of flat `inserted("name", 1)`.
- Use `FixtureTrait.mutate(...)` instead of deprecated `FixtureTrait.of(...)`.
- Prefer `.run()` when the test framework integration is already binding the executor.
- Only use `.execute(executor)` in manual wiring.

For current API patterns and migration guidance, read [references/latest-api.md](references/latest-api.md).

### 3. Prefer alias-first fixtures and typed observation

Use the high-level fixture path first:

- `given(g -> g.fixture("alias", Entity.class, ...))` or `given(g -> g.fixture(handle, ...))`
- `then(t -> t.fixture(handle, u -> u.after(...)))` or `then(t -> t.fixture("alias", Entity.class, u -> u.after(...)))`
- `fixtureRows(...)` when the same entity type needs several rows with shared defaults

Observation defaults:

- Prefer `then(t -> t.entity(Foo.class, e -> e.modified(1)))` for typed observation — no explicit `observe(...)` needed.
- Do not add `registerEntity(Foo.class)` by default when the entity only appears in `given(...)` or typed `then(...)` assertions; common paths auto-register metadata.
- Manually register watch-only tables and explicit mapping overrides.

### 4. Model routing and cleanup explicitly

Keep these concerns separate:

- SQL shard predicates: `.route(...)`
- Physical dynamic table selection: `.dynamicTableBy(...)`
- Datasource selection: Spring Boot `flowtest.v2.datasource.*` config, not per-scenario DSL

Cleanup defaults:

- `DELETE_INSERTED` for pure insert flows
- `RESTORE_BEFORE_IMAGE` when the action updates or deletes existing rows

Read [references/integration-recipes.md](references/integration-recipes.md) when the test involves sharding, dynamic tables, MyBatis-Plus, or multiple datasources.

### 5. Verify in one place with block-scoped assertions

Use `then(...)` as the sole assertion surface with block-scoped resource assertions:

- `t.success()` / `t.failure(ExType.class)`
- `t.returns(expected)` / `t.returnsSatisfying(assertion)`
- `t.fixture(handle, u -> u.after(...))` / `u.before(...)` / `u.change(...)` / `u.afterMatches(...)`
- `t.table("name", order -> order.inserted(1).insertedRow(...).inspect(...))`
- `t.entity(Foo.class, e -> e.modified(1))`
- `t.inspect(ctx -> ...)` for cross-resource imperative assertions (global escape hatch)

Each resource gets its own lambda scope for assertions, keeping the verification organized and readable.

## Guardrails

- Do not recommend legacy V1 modules unless the user explicitly asks for V1.
- Do not use old `watch(...)`, `verify(...)`, `persist(...)`, or `FixtureTrait.of(...)` in new examples.
- Do not implement `ScenarioExecutorProvider` in new Spring Boot tests unless maintaining compatibility-only code.
- Do not put datasource routing in the scenario DSL.
- When docs and source disagree, follow the current source-backed API shape: `given`, `observe?`, `when`, `then`, `run`.
