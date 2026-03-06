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
- Added `flowtest-v2-spring-boot-starter` that auto-configures JDBC registry/executor beans and `ScenarioExecutor`.

## Active Decisions
- Default cleanup in `v2` is `DELETE_INSERTED`, not `ROLLBACK`, because base runtime does not own transaction boundaries.
- Traits belong only to fixture construction.
- Route scope must be explicit for sharded observations.
- `RESTORE_BEFORE_IMAGE` cleanup runs route-aware SQL and is now the recommended policy for watch-only scenarios that mutate existing rows.

## Next Likely Steps
- Add richer row-content assertions on top of the existing diff model.
- Add docs/examples for JUnit 5, TestNG, and Spring Boot usage patterns.
- Consider transaction-aware `ROLLBACK` support for Spring-managed tests.
