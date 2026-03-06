# Project Brief

## Overview
FlowTest is a multi-module Java 8 integration testing framework. The repository contains a legacy `v1` track under root modules and a new `v2` track under `flowtest-v2/`.

## Current Strategic Direction
`v2` is the active design path. It is an observation-first test framework that separates:
- Fixture: optional test data setup
- Observe: resources to snapshot and diff
- Route: shard or access constraints
- Expectation: outcome, fixture, and change assertions

## Key Goals
- Keep Java 8 compatibility.
- Use `com.github.sailfishc.flowtest.v2...` for new packages.
- Preserve traits as the main fixture composition mechanism.
- Make act-only, mixed-table, and sharded scenarios first-class.
- Evolve `v2` in parallel without forcing `v1` compatibility constraints.

## Important Repos/Modules
- Legacy: `flowtest-core`, `flowtest-junit5`, `flowtest-testng`, `flowtest-mockito`, `flowtest-spring-boot-starter`
- New: `flowtest-v2-spec`, `flowtest-v2-assertion`, `flowtest-v2-fixture`, `flowtest-v2-observe-rdbms`, `flowtest-v2-runtime`, `flowtest-v2-cases`
