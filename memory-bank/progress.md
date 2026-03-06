# Progress

## What Works
- `v2` module layout is in place and builds cleanly.
- Scenario compiler validates core structural rules.
- Traits, fixture handles, route scope, and observation scope are modeled.
- JDBC snapshot/diff/cleanup works for inserted-row cleanup.
- JDBC cleanup now also supports `RESTORE_BEFORE_IMAGE` for inserted, modified, and deleted rows.
- Runtime executes expectations for outcome, fixture state, and change counts.
- H2-backed case module proves act-only, mixed fixture/watch-only, and watch-only restore flows.

## Validation
- Main `v2` command: `mvn -f flowtest-v2/pom.xml test`
- Latest known result: passing.

## What Is Not Done Yet
- `ROLLBACK` needs an external transaction-aware executor.
- `CUSTOM_COMPENSATOR` is not implemented.
- No dedicated `v2` JUnit 5 or Spring integration module yet.
- Row-content assertions are still count-oriented; there is no first-class DSL yet for before/after field assertions.

## Known Constraints
- JDBC fixture support relies on explicit adapters per entity type.
- Sharded observation requires route scope before action execution.
- `RESTORE_BEFORE_IMAGE` assumes rows are fully writable from snapshot columns and does not yet handle database-generated or non-updatable columns specially.
