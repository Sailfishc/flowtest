# Latest API

## Recommended default

Use FlowTest V2 as an observation-first DSL with block-scoped assertions:

```java
FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");

FlowTestV2.scenario("scenario-name")
    .given(g -> g.fixture(user,
        FixtureTrait.mutate(v -> v.setId(1L)),
        FixtureTrait.mutate(v -> v.setTenantId(100L)),
        FixtureTrait.mutate(v -> v.setBalance(100L))))
    .observe(o -> o.table("ft_order", r -> r.route("tenant_id", 100L)))
    .when(() -> service.call(...))
    .then(t -> t
        .success()
        .fixture(user, u -> u.after(v -> assertThat(v.getBalance()).isEqualTo(80L)))
        .table("ft_order", order -> order.inserted(1)))
    .run();
```

## Current defaults

- Use `observe(...)` for explicit observation (route, dynamic table, inspect-only); resources mentioned in `then(...)` are auto-inferred.
- Use `then(...)` as the sole assertion entry point with block-scoped resource assertions.
- Use `.run()` when JUnit 5 or TestNG integration is active.
- Use `fixture(...)` (not `persist(...)`) for given data.
- Use `FixtureTrait.mutate(...)` (not `FixtureTrait.of(...)`).
- Use `fixtureRows(...)` for multiple rows of the same entity type.
- Use getter references in `FixtureStatePatch`:

```java
.fixture(user, u -> u.afterMatches(
    FixtureStatePatch.of(TestUser.class)
        .set(TestUser::getBalance, 80L)
        .ignore(TestUser::getUpdatedAt)
))
```

## Verification helpers (block-scoped)

- `t.success()` / `t.failure(ExType.class)` / `t.failureSatisfying(ExType.class, assertion)`
- `t.returns(expected)` / `t.returnsSatisfying(assertion)`
- `t.fixture(handle, u -> u.before(...))` / `u.after(...)` / `u.change(...)` / `u.afterMatches(...)`
- `t.table("ft_order", order -> order.inserted(1))` / `order.deleted(1)` / `order.modified(1)`
- `t.table("ft_order", order -> order.insertedRow(...))` / `order.modifiedRow(...)` / `order.deletedRow(...)`
- `t.table("ft_order", order -> order.inspect(ctx -> ...))` for imperative assertions
- `t.entity(OrderEntity.class, e -> e.inserted(1))`
- `t.inspect(ctx -> ...)` for cross-resource imperative assertions (global escape hatch)

## Registration rules

- Fixture entities declared in `given(...)` are auto-registered in the common path.
- Typed observation with `then(t -> t.entity(Foo.class, ...))` also auto-registers metadata on first use.
- Manually register watch-only tables with `JdbcObservationRegistry.registerTable(...)`.
- Use explicit registry registration when a table or entity needs column overrides, ignore rules, or dynamic table metadata beyond conventions.

## Migration map (old → new)

- `watch(...)` → `observe(...)` (or omit if auto-inferred from `then(...)`)
- `verify(ctx -> {...})` → `then(t -> t.success().table(...).fixture(...))`
- `persist(...)` → `fixture(...)`
- `FixtureTrait.of(...)` → `FixtureTrait.mutate(...)`
- `FixtureTrait.compose(...)` → `FixtureTrait.all(...)`
- `expectNoException()` → `success()`
- flat `.inserted("name", 1)` → block-scoped `.table("name", t -> t.inserted(1))`
- `.execute(executor)` → `.run()` under JUnit 5 or TestNG integration
