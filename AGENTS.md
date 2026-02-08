# Repository Guidelines

## Project Structure & Module Organization
This is a multi-module Maven project targeting Java 8. The root `pom.xml` aggregates:
- `flowtest-core`: DSL, fixture engine, persistence, snapshot/cleanup logic.
- `flowtest-assertj-db`: AssertJ-DB integration.
- `flowtest-junit5`: JUnit 5 extension and `@FlowTest` support.
- `flowtest-testng`: TestNG integration.
- `flowtest-mockito`: Mockito-oriented helpers.
- `flowtest-spring-boot-starter`: Spring Boot auto-configuration.

Use standard Maven layout in each module: `src/main/java`, `src/main/resources`, `src/test/java`, `src/test/resources`.

## Build, Test, and Development Commands
- `mvn clean install`: build and test all modules.
- `mvn clean install -DskipTests`: full compile/package without tests.
- `mvn test`: run all tests.
- `mvn -pl flowtest-core test`: run tests for one module.
- `mvn -pl flowtest-testng -Dtest=FlowTestListenerTest test`: run one test class.

Run commands from the repository root unless you are working inside a single module.

## Coding Style & Naming Conventions
- Language level is Java 8 (`source/target 1.8`).
- Follow existing Java style: 4-space indentation, braces on same line, clear method-level Javadoc for public APIs in core modules.
- Naming: packages `com.flowtest...`, classes `PascalCase`, methods/fields `camelCase`, constants `UPPER_SNAKE_CASE`.
- Keep DSL and test code readable with Arrange-Act-Assert flow; prefer small, composable methods and traits.

No formatter or linter is enforced in Maven, so match surrounding code style before committing.

## Testing Guidelines
- Test stack includes JUnit 5, TestNG (module-specific), AssertJ, and H2.
- Add or update tests in the same module you change; mirror package structure under `src/test/java`.
- Test classes should end with `Test` (for example, `MockContextTest`).
- There is no configured coverage gate; contributors are expected to add meaningful regression tests for behavior changes.

## Commit & Pull Request Guidelines
- Follow the existing commit pattern: `feat: ...`, `fix: ...`, `chore: ...`, `build: ...`.
- Keep commits focused and atomic; avoid mixing refactors with functional changes.
- PRs should include a short problem/solution summary and a list of affected modules.
- Document validation commands in the PR description (for example, `mvn test` or module-scoped tests).
- Link related issues when applicable.
