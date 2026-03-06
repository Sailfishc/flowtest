# FlowTest V2 Architecture Baseline

## Positioning
FlowTest V2 is an observation-first integration testing framework for database-centric systems.
It does not treat fixture creation as the center of the model. Instead, it separates four concerns:

- Fixture: optional test data prepared before the business action.
- Observe: the resources whose state transitions must be tracked.
- Route: the routing constraints required to access those resources safely.
- Expectation: the result and state-change assertions for the scenario.

This design keeps `arrange` useful without making it a prerequisite for snapshots, diffing, or cleanup.

## Goals
- Support JDK 8 as the baseline runtime.
- Use `com.github.sailfishc` as the package root for the new implementation.
- Preserve the strengths of traits for fixture composition.
- Make act-only and mixed-table scenarios first-class.
- Treat sharding and routing as core concepts rather than add-on behavior.
- Keep V2 isolated from V1 so the new model can evolve without compatibility pressure.

## Core Concepts
### Fixture
Fixtures are optional. They define the state that must exist before the action runs.
Traits remain the primary mechanism for expressing business-specific data variation.

### Observation Scope
Observation scope defines which resources participate in baselines, diffs, and cleanup. A scenario can mix:

- fixture-backed resources: state exists before the action.
- watch-only resources: no pre-inserted test data exists, but the resource must still be observed.

### Route Scope
Each observed resource can carry an explicit route scope. In sharded environments, route scope is mandatory when the middleware requires shard keys or hints for safe access.

### Expectations
Assertions are split into:

- outcome expectations: return value and exception behavior.
- fixture expectations: assertions against known fixture handles.
- change expectations: inserted, deleted, and modified counts for observed resources.

### Cleanup Policy
Cleanup is scenario-driven rather than framework-global. The initial policy set is:

- `ROLLBACK`
- `DELETE_INSERTED`
- `DELETE_FIXTURE`
- `RESTORE_BEFORE_IMAGE`
- `CUSTOM_COMPENSATOR`

## Architectural Principles
1. Explicit scope beats implicit discovery.
2. Resource modeling happens per table or entity, not per test as a whole.
3. Route information must be available before the action when snapshot-based observation is enabled.
4. Diffing is identity-based, not row-count-based.
5. Cleanup and observation reuse the same declared scope.
6. Traits describe fixture state only; they do not own routing, observation, or cleanup.

## Scenario Lifecycle
1. Build fixture definitions.
2. Build observation scope.
3. Attach route scope to each sharded resource.
4. Compile and validate the scenario.
5. Materialize fixtures if the scenario uses them.
6. Capture the pre-action baseline for observed resources.
7. Execute the action.
8. Capture the post-action state and calculate diffs.
9. Evaluate expectations.
10. Execute cleanup according to policy.

## Target Module Layout
- `flowtest-v2-spec`: immutable scenario model, traits, routing primitives, and shared contracts.
- `flowtest-v2-assertion`: expectation contracts and assertion payload types.
- `flowtest-v2-fixture`: fixture drafts, trait application helpers, and fixture context implementations.
- `flowtest-v2-observe-rdbms`: relational observation abstractions such as identity and route-aware snapshot requests.
- `flowtest-v2-runtime`: the public DSL, scenario compiler, validation, and runtime orchestration.

## Supported Scenario Types
### Fixture-backed single-table scenario
A known entity is prepared before the action and its final state is asserted.

### Act-only single-table scenario
No fixture is inserted, but the table is observed and its inserted rows are asserted after the action.

### Mixed multi-table scenario
One table is fixture-backed and another is watch-only. The framework treats them independently inside the same scenario.

### Sharded act-only scenario
Observed tables must declare route scope explicitly before the action so that baseline, diff, and cleanup SQL remain valid.

## Initial Validation Rules
- A scenario must observe at least one resource.
- A sharded observation must declare route scope.
- A fixture-backed observation must reference a declared fixture handle.
- Duplicate fixture handles are invalid.

## Delivery Plan
### Milestone 1
- Establish the V2 module layout.
- Implement the immutable contracts and DSL skeleton.
- Implement compiler validation for observation and routing rules.
- Keep the output compile-safe on JDK 8.

### Milestone 2
- Add relational snapshot and diff execution.
- Add outcome and change evaluation.
- Add cleanup plan generation.

### Milestone 3
- Add JUnit 5 and Spring integrations.
- Add sharding resolvers and route adapters.
- Add before-image restore for non-transactional update scenarios.
