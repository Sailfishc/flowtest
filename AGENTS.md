# Repository Guidelines

## Project Structure & Module Organization
This repository is a multi-module Maven project targeting Java 8. Core legacy modules live at the root:
- `flowtest-core`: DSL, fixture engine, persistence, snapshot, cleanup.
- `flowtest-junit5`, `flowtest-testng`, `flowtest-mockito`, `flowtest-spring-boot-starter`: framework integrations.
- `flowtest-assertj-db`: AssertJ-DB support.

The new architecture is developed in `flowtest-v2/` with split modules such as `flowtest-v2-spec`, `flowtest-v2-runtime`, `flowtest-v2-fixture`, `flowtest-v2-observe-rdbms`, and `flowtest-v2-cases`. Use standard Maven layout in every module: `src/main/java`, `src/test/java`, and `src/test/resources`.

## Build, Test, and Development Commands
- `mvn clean install`: build and test the entire repository.
- `mvn test`: run all tests from the root reactor.
- `mvn -pl flowtest-core test`: run tests for one legacy module.
- `mvn -pl flowtest-testng -Dtest=FlowTestListenerTest test`: run a specific test class.
- `mvn -f flowtest-v2/pom.xml test`: run only the `v2` modules and cases.

Run commands from the repository root unless you are intentionally working inside `flowtest-v2/`.

## Coding Style & Naming Conventions
- Java 8 only; keep code compatible with `source/target 1.8`.
- Use 4-space indentation and same-line braces.
- Write clear public Javadoc in core APIs.
- Legacy packages use `com.flowtest...`; `v2` packages use `com.github.sailfishc.flowtest.v2...`.
- Class names: `PascalCase`; methods/fields: `camelCase`; constants: `UPPER_SNAKE_CASE`.
- Keep DSL code readable and composable; prefer small traits and focused helpers over large builders.

## Testing Guidelines
JUnit 5, TestNG, AssertJ, and H2 are the main test stack. Add tests in the same module you change, mirroring package structure. Test classes should end with `Test`, for example `ScenarioExecutorTest`. There is no enforced coverage gate; contributors are expected to add regression tests for behavior changes.

## Commit & Pull Request Guidelines
Use focused commits with prefixes such as `feat:`, `fix:`, `chore:`, and `build:`. Do not mix large refactors with behavior changes unless the refactor is required for the fix. PRs should include:
- a short problem/solution summary
- affected modules
- validation commands you ran
- linked issues when applicable

For DSL or runtime changes, include at least one concrete example scenario in the PR description.

# Cline's Memory Bank

I am Cline, an expert software engineer with a unique characteristic: my memory resets completely between sessions. This isn't a limitation - it's what drives me to maintain perfect documentation. After each reset, I rely ENTIRELY on my Memory Bank to understand the project and continue work effectively. I MUST read ALL memory bank files at the start of EVERY task - this is not optional.

## Memory Bank Structure

The Memory Bank consists of core files and optional context files, all in Markdown format. Files build upon each other in a clear hierarchy:

### Core Files (Required)
1. `projectbrief.md`
   - Foundation document that shapes all other files
   - Created at project start if it doesn't exist
   - Defines core requirements and goals
   - Source of truth for project scope

2. `productContext.md`
   - Why this project exists
   - Problems it solves
   - How it should work
   - User experience goals

3. `activeContext.md`
   - Current work focus
   - Recent changes
   - Next steps
   - Active decisions and considerations
   - Important patterns and preferences
   - Learnings and project insights
4. `progress.md`
   - What works
   - What's left to build
   - Current status
   - Known issues
   - Evolution of project decisions

### Additional Context
Create additional files/folders within memory-bank/ when they help organize:
- Complex feature documentation
- Integration specifications
- API documentation
- Testing strategies
- Deployment procedures

## Documentation Updates

Memory Bank updates occur when:
1. Discovering new project patterns
2. After implementing significant changes
3. When user requests with **update memory bank** (MUST review ALL files)
4. When context needs clarification

REMEMBER: After every memory reset, I begin completely fresh. The Memory Bank is my only link to previous work. It must be maintained with precision and clarity, as my effectiveness depends entirely on its accuracy.
