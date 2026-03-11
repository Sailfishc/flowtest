package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.FlowTestV2;
import com.github.sailfishc.flowtest.v2.assertion.ModifiedRowAssertion;
import com.github.sailfishc.flowtest.v2.assertion.RowAssertions;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the new block-scoped ResourceExpectationSpec DSL.
 * Replaces the old TableChangeSpecTest.
 */
class ResourceExpectationSpecTest {

    // ==================== Single Table Scenarios ====================

    @Nested
    @DisplayName("Single table assertions")
    class SingleTable {

        @Test
        void shouldCompileInsertedCount() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("single-insert")
                .when(() -> "ok")
                .then(t -> t
                    .success()
                    .table("t_order", order -> order.inserted(2)))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getChangeExpectations()).hasSize(1);
            assertThat(compiled.getDefinition().getExpectations().getChangeExpectations().get(0).getResourceName())
                .isEqualTo("t_order");
            assertThat(compiled.getDefinition().getExpectations().getChangeExpectations().get(0).getExpectedInserted())
                .isEqualTo(2L);
            // Observation should be auto-inferred
            assertThat(compiled.getDefinition().getObservations()).hasSize(1);
            assertThat(compiled.getDefinition().getObservations().get(0).getResourceName()).isEqualTo("t_order");
        }

        @Test
        void shouldCompileDeletedCount() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("single-delete")
                .when(() -> "ok")
                .then(t -> t.table("t_order", order -> order.deleted(3)))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getChangeExpectations()).hasSize(1);
            assertThat(compiled.getDefinition().getExpectations().getChangeExpectations().get(0).getExpectedDeleted())
                .isEqualTo(3L);
        }

        @Test
        void shouldCompileModifiedCount() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("single-modify")
                .when(() -> "ok")
                .then(t -> t.table("t_order", order -> order.modified(1)))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getChangeExpectations()).hasSize(1);
            assertThat(compiled.getDefinition().getExpectations().getChangeExpectations().get(0).getExpectedModified())
                .isEqualTo(1L);
        }

        @Test
        void shouldCompileInsertedRowAssertion() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("single-inserted-row")
                .when(() -> "ok")
                .then(t -> t.table("t_order", order -> order
                    .inserted(1)
                    .insertedRow(RowAssertions.columns("STATUS", "CREATED"))))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getChangeExpectations()).hasSize(1);
            assertThat(compiled.getDefinition().getExpectations().getChangeAssertionExpectations()).hasSize(1);
        }

        @Test
        void shouldCompileInsertedRowsList() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("single-inserted-rows")
                .when(() -> "ok")
                .then(t -> t.table("t_order", order -> order
                    .inserted(2)
                    .insertedRows(rows -> rows
                        .sortBy("id")
                        .row(0, RowAssertions.columns("STATUS", "CREATED"))
                        .row(1, RowAssertions.columns("STATUS", "PAID")))))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getChangeExpectations()).hasSize(1);
            assertThat(compiled.getDefinition().getExpectations().getChangeAssertionExpectations()).hasSize(1);
        }

        @Test
        void shouldCompileMixedCountsInSameTable() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("single-mixed")
                .when(() -> "ok")
                .then(t -> t.table("t_order", order -> order
                    .inserted(2)
                    .modified(1)
                    .deleted(1)))
                .compile();

            // Each count call produces a separate expectation
            assertThat(compiled.getDefinition().getExpectations().getChangeExpectations()).hasSize(3);
        }

        @Test
        void shouldCompileResourceInspection() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("single-inspect")
                .when(() -> "ok")
                .then(t -> t.table("t_order", order -> order
                    .inserted(1)
                    .inspect(ctx -> {
                        // scoped imperative assertion
                    })))
                .compile();

            assertThat(compiled.getDefinition().getVerifications()).hasSize(1);
        }

        @Test
        void shouldCompileSatisfiesAssertion() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("single-satisfies")
                .when(() -> "ok")
                .then(t -> t.table("t_order", order -> order
                    .satisfies(change -> {
                        // custom resource change assertion
                    })))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getChangeAssertionExpectations()).hasSize(1);
        }
    }

    // ==================== Multi-Table Scenarios ====================

    @Nested
    @DisplayName("Multiple table assertions")
    class MultiTable {

        @Test
        void shouldCompileTwoTables() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("multi-two-tables")
                .when(() -> "ok")
                .then(t -> t
                    .table("t_order", order -> order.inserted(1))
                    .table("t_user", user -> user.modified(1)))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getChangeExpectations()).hasSize(2);
            // Both tables should be auto-inferred for observation
            assertThat(compiled.getDefinition().getObservations()).hasSize(2);
        }

        @Test
        void shouldCompileThreeTables() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("multi-three-tables")
                .when(() -> "ok")
                .then(t -> t
                    .table("t_order", order -> order.inserted(1))
                    .table("t_user", user -> user.modified(1))
                    .table("t_product", product -> product.deleted(2)))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getChangeExpectations()).hasSize(3);
            assertThat(compiled.getDefinition().getObservations()).hasSize(3);
        }

        @Test
        void shouldCompileEntityAndTableMixed() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("multi-entity-table")
                .when(() -> "ok")
                .then(t -> t
                    .entity(TestUser.class, user -> user.modified(1))
                    .table("t_order", order -> order.inserted(2)))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getChangeExpectations()).hasSize(2);
            assertThat(compiled.getDefinition().getObservations()).hasSize(2);
        }

        @Test
        void shouldCompileMultiTableWithGlobalInspect() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("multi-global-inspect")
                .when(() -> "ok")
                .then(t -> t
                    .table("t_order", order -> order.inserted(1))
                    .table("t_user", user -> user.modified(1))
                    .inspect(ctx -> {
                        // cross-resource correlation assertion
                    }))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getChangeExpectations()).hasSize(2);
            assertThat(compiled.getDefinition().getVerifications()).hasSize(1);
        }

        @Test
        void shouldCompileMultiTableWithScopedInspects() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("multi-scoped-inspect")
                .when(() -> "ok")
                .then(t -> t
                    .table("t_order", order -> order
                        .inserted(1)
                        .inspect(ctx -> { /* order-scoped */ }))
                    .table("t_user", user -> user
                        .modified(1)
                        .inspect(ctx -> { /* user-scoped */ })))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getChangeExpectations()).hasSize(2);
            assertThat(compiled.getDefinition().getVerifications()).hasSize(2);
        }
    }

    // ==================== Same Table Multi-Data Scenarios ====================

    @Nested
    @DisplayName("Same table with multiple change types and rows")
    class SameTableMultiData {

        @Test
        void shouldCompileInsertModifyDeleteOnSameTable() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("same-table-all-types")
                .when(() -> "ok")
                .then(t -> t.table("t_order", order -> order
                    .inserted(2)
                    .modified(1)
                    .deleted(1)))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getChangeExpectations()).hasSize(3);
            // All from the same table — only one observation
            assertThat(compiled.getDefinition().getObservations()).hasSize(1);
        }

        @Test
        void shouldCompileMultipleInsertedRowAssertions() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("same-table-multi-rows")
                .when(() -> "ok")
                .then(t -> t.table("t_order", order -> order
                    .inserted(3)
                    .insertedRow(RowAssertions.columns("STATUS", "CREATED"))
                    .insertedRow(RowAssertions.columns("STATUS", "PAID"))
                    .insertedRow(RowAssertions.columns("STATUS", "SHIPPED"))))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getChangeExpectations()).hasSize(1);
            assertThat(compiled.getDefinition().getExpectations().getChangeAssertionExpectations()).hasSize(3);
        }

        @Test
        void shouldCompileInsertedRowsAndModifiedRows() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("same-table-multi-change-rows")
                .when(() -> "ok")
                .then(t -> t.table("t_order", order -> order
                    .inserted(1)
                    .modified(2)
                    .insertedRows(rows -> rows
                        .row(0, RowAssertions.columns("STATUS", "CREATED")))
                    .modifiedRows(rows -> rows
                        .row(0, row -> { /* first modified */ })
                        .row(1, row -> { /* second modified */ }))))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getChangeExpectations()).hasSize(2);
            assertThat(compiled.getDefinition().getExpectations().getChangeAssertionExpectations()).hasSize(2);
        }
    }

    // ==================== Fixture Scenarios ====================

    @Nested
    @DisplayName("Fixture expectations")
    class FixtureExpectations {

        @Test
        void shouldCompileFixtureWithHandleAndBeforeAfter() {
            final FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");

            CompiledScenario<String> compiled = FlowTestV2.scenario("fixture-before-after")
                .given(g -> g.fixture(user, FixtureTrait.mutate(u -> u.name = "Alice")))
                .when(() -> "ok")
                .then(t -> t.fixture(user, u -> u
                    .before(before -> { /* assert before state */ })
                    .after(after -> { /* assert after state */ })))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getFixtureChangeExpectations()).hasSize(1);
            assertThat(compiled.getDefinition().getExpectations().getFixtureExpectations()).hasSize(1);
        }

        @Test
        void shouldCompileFixtureWithAlias() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("fixture-alias")
                .given(g -> g.fixture("user", TestUser.class, FixtureTrait.mutate(u -> u.name = "Bob")))
                .when(() -> "ok")
                .then(t -> t.fixture("user", TestUser.class, u -> u
                    .after(after -> { /* assert after state */ })))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getFixtureExpectations()).hasSize(1);
        }

        @Test
        void shouldCompileFixtureChangeAssertion() {
            final FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");

            CompiledScenario<String> compiled = FlowTestV2.scenario("fixture-change")
                .given(g -> g.fixture(user, FixtureTrait.mutate(u -> u.name = "Alice")))
                .when(() -> "ok")
                .then(t -> t.fixture(user, u -> u
                    .change((before, after) -> {
                        // compare before and after
                    })))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getFixtureChangeExpectations()).hasSize(1);
        }

        @Test
        void shouldCompileFixtureAfterMatches() {
            final FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");

            CompiledScenario<String> compiled = FlowTestV2.scenario("fixture-after-matches")
                .given(g -> g.fixture(user, FixtureTrait.mutate(u -> u.balance = 100L)))
                .when(() -> "ok")
                .then(t -> t.fixture(user, u -> u
                    .afterMatches(FixtureStatePatch.of(TestUser.class)
                        .set(TestUser::getBalance, 80L))))
                .compile();

            assertThat(compiled.getDefinition().getVerifications()).hasSize(1);
        }

        @Test
        void shouldCompileFixtureInspect() {
            final FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");

            CompiledScenario<String> compiled = FlowTestV2.scenario("fixture-inspect")
                .given(g -> g.fixture(user, FixtureTrait.mutate(u -> u.name = "Alice")))
                .when(() -> "ok")
                .then(t -> t.fixture(user, u -> u
                    .inspect(ctx -> {
                        // fixture-scoped imperative assertion
                    })))
                .compile();

            assertThat(compiled.getDefinition().getVerifications()).hasSize(1);
        }

        @Test
        void shouldRejectUndeclaredFixtureHandle() {
            final FixtureHandle<TestUser> ghost = FixtureHandle.named(TestUser.class, "ghost");

            assertThatThrownBy(() -> FlowTestV2.scenario("undeclared-fixture")
                .when(() -> "ok")
                .then(t -> t.fixture(ghost, u -> u
                    .after(after -> { /* nope */ })))
                .compile())
                .isInstanceOf(ScenarioValidationException.class)
                .hasMessageContaining("ghost");
        }
    }

    // ==================== Combined Scenarios ====================

    @Nested
    @DisplayName("Combined fixture + table + result assertions")
    class Combined {

        @Test
        void shouldCompileFullScenario() {
            final FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");

            CompiledScenario<Long> compiled = FlowTestV2.<Long>scenario("full-combined")
                .given(g -> g.fixture(user, FixtureTrait.mutate(u -> {
                    u.name = "Alice";
                    u.balance = 100L;
                })))
                .when(() -> 42L)
                .then(t -> t
                    .success()
                    .returns(42L)
                    .table("t_order", order -> order
                        .inserted(1)
                        .insertedRow(RowAssertions.columns("STATUS", "CREATED")))
                    .table("t_payment", payment -> payment
                        .inserted(1))
                    .fixture(user, u -> u
                        .afterMatches(FixtureStatePatch.of(TestUser.class)
                            .set(TestUser::getBalance, 58L)))
                    .inspect(ctx -> {
                        // cross-resource correlation
                    }))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getChangeExpectations()).hasSize(2);
            assertThat(compiled.getDefinition().getExpectations().getChangeAssertionExpectations()).hasSize(1);
            assertThat(compiled.getDefinition().getExpectations().getResultAssertions()).hasSize(2);
            assertThat(compiled.getDefinition().getVerifications()).hasSize(2); // afterMatches + inspect
        }

        @Test
        void shouldCompileResultOnlyScenario() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("result-only")
                .when(() -> "hello")
                .then(t -> t
                    .success()
                    .returns("hello"))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getResultAssertions()).hasSize(2);
            assertThat(compiled.getDefinition().getObservations()).isEmpty();
        }

        @Test
        void shouldCompileExpectedFailure() {
            CompiledScenario<?> compiled = FlowTestV2.scenario("expected-failure")
                .<String>when(() -> { throw new IllegalArgumentException("bad input"); })
                .then(t -> t.failure(IllegalArgumentException.class))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getResultAssertions()).hasSize(1);
        }

        @Test
        void shouldCompileFailureSatisfying() {
            CompiledScenario<?> compiled = FlowTestV2.scenario("expected-failure-satisfying")
                .<String>when(() -> { throw new IllegalArgumentException("bad input"); })
                .then(t -> t.failureSatisfying(IllegalArgumentException.class,
                    ex -> { /* custom assertion on the exception */ }))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getResultAssertions()).hasSize(1);
        }

        @Test
        void shouldCompileReturnsSatisfying() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("returns-satisfying")
                .when(() -> "hello world")
                .then(t -> t.returnsSatisfying((result, failure) -> {
                    // custom result assertion
                }))
                .compile();

            assertThat(compiled.getDefinition().getExpectations().getResultAssertions()).hasSize(1);
        }
    }

    // ==================== Observation Merging ====================

    @Nested
    @DisplayName("Observation auto-inference and merging")
    class ObservationMerging {

        @Test
        void shouldNotDuplicateExplicitObservation() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("merge-no-dup")
                .observe(obs -> obs.table("t_order"))
                .when(() -> "ok")
                .then(t -> t.table("t_order", order -> order.inserted(1)))
                .compile();

            // Explicit t_order + inferred t_order should not duplicate
            assertThat(compiled.getDefinition().getObservations()).hasSize(1);
        }

        @Test
        void shouldMergeExplicitWithInferred() {
            CompiledScenario<String> compiled = FlowTestV2.scenario("merge-mixed")
                .observe(obs -> obs.table("t_order", o -> o.route("tenant_id", 1L)))
                .when(() -> "ok")
                .then(t -> t
                    .table("t_order", order -> order.inserted(1))
                    .table("t_user", user -> user.modified(1)))
                .compile();

            // Explicit t_order (with route) stays; t_user is inferred
            assertThat(compiled.getDefinition().getObservations()).hasSize(2);
        }
    }

    // ==================== Test entities ====================

    static class TestUser {
        String name;
        long balance;

        public String getName() { return name; }
        public Long getBalance() { return balance; }
    }
}
