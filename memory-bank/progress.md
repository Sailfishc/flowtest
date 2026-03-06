# Progress

## What Works
- `v2` module layout is in place and builds cleanly.
- Scenario compiler validates core structural rules.
- Traits, fixture handles, route scope, and observation scope are modeled.
- JDBC snapshot/diff/cleanup works for inserted-row cleanup.
- JDBC cleanup now also supports `RESTORE_BEFORE_IMAGE` for inserted, modified, and deleted rows.
- Runtime executes expectations for outcome, fixture state, and change counts.
- Runtime now supports row-level inserted/deleted/modified assertions and custom full-diff assertions.
- JUnit 5 integration resolves `ScenarioExecutor` via extension builder or provider interface.
- TestNG integration injects `ScenarioExecutor` via listener and annotated fields.
- TestNG module now contains a complete Spring Boot + TestNG example that runs under the TestNG provider and demonstrates starter-based wiring end to end.
- Spring Boot starter auto-configures JDBC registries, executors, and `ScenarioExecutor`.
- H2-backed case module proves act-only, mixed fixture/watch-only, and watch-only restore flows.
- Docs now include a dedicated `flowtest-v2` integrations guide and point to the Spring Boot + TestNG example file.

## Validation
- Main `v2` command: `mvn -f flowtest-v2/pom.xml test`
- Latest known result: passing.

## What Is Not Done Yet
- `ROLLBACK` needs an external transaction-aware executor.
- `CUSTOM_COMPENSATOR` is not implemented.
- There is no dedicated Spring Test/TestNG transaction bridge yet for rollback-style cleanup.
- There is no transaction-aware `ScenarioExecutor` wrapper yet for `CleanupPolicy.ROLLBACK`.

## Known Constraints
- JDBC fixture support relies on explicit adapters per entity type.
- Sharded observation requires route scope before action execution.
- `RESTORE_BEFORE_IMAGE` assumes rows are fully writable from snapshot columns and does not yet handle database-generated or non-updatable columns specially.
