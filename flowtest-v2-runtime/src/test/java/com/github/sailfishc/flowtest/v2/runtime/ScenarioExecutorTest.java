package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.FlowTestV2;
import com.github.sailfishc.flowtest.v2.fixture.FixtureExecution;
import com.github.sailfishc.flowtest.v2.fixture.FixtureExecutor;
import com.github.sailfishc.flowtest.v2.fixture.FixtureStateMetadata;
import com.github.sailfishc.flowtest.v2.assertion.ModifiedRowAssertions;
import com.github.sailfishc.flowtest.v2.assertion.RowAssertions;
import com.github.sailfishc.flowtest.v2.spec.CleanupPolicy;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureSpec;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
import com.github.sailfishc.flowtest.v2.spec.ObservationDiff;
import com.github.sailfishc.flowtest.v2.spec.ObservationExecutor;
import com.github.sailfishc.flowtest.v2.spec.ObservationSnapshot;
import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;
import com.github.sailfishc.flowtest.v2.spec.ResourceSnapshot;
import com.github.sailfishc.flowtest.v2.spec.RowKey;
import com.github.sailfishc.flowtest.v2.spec.RowSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioExecutorTest {

    // ===== Scenario A: Single table simple assertion =====

    @Test
    void shouldVerifyInsertedRowsInSingleTable() throws Exception {
        RecordingObservationExecutor observationExecutor = new RecordingObservationExecutor(Arrays.asList(
            snapshot("orders"),
            snapshot("orders", row(1L, "status", "CREATED"))
        ));
        ScenarioExecutor executor = new ScenarioExecutor(observationExecutor);

        ScenarioExecutionResult<String> result = FlowTestV2.scenario("single-table")
            .when(() -> "done")
            .then(t -> t.table("orders", order -> order.inserted(1)))
            .execute(executor);

        assertThat(result.getResult()).isEqualTo("done");
        assertThat(result.getDiff().getChange("orders").getInsertedCount()).isEqualTo(1L);
        assertThat(observationExecutor.getLastCleanupPolicy()).isEqualTo(CleanupPolicy.DELETE_INSERTED);
    }

    // ===== Scenario B: Multiple tables =====

    @Test
    void shouldVerifyMultipleTablesWithInspect() throws Exception {
        RecordingObservationExecutor observationExecutor = new RecordingObservationExecutor(Arrays.asList(
            new ObservationSnapshot(Arrays.asList(
                new ResourceSnapshot("orders", Collections.<RowSnapshot>emptyList()),
                new ResourceSnapshot("order_items", Collections.<RowSnapshot>emptyList())
            )),
            new ObservationSnapshot(Arrays.asList(
                new ResourceSnapshot("orders", Arrays.asList(row(1L, "status", "CREATED"))),
                new ResourceSnapshot("order_items", Arrays.asList(
                    row(10L, "order_id", 1L, "sku", "A"),
                    row(11L, "order_id", 1L, "sku", "B")
                ))
            ))
        ));
        ScenarioExecutor executor = new ScenarioExecutor(observationExecutor);

        ScenarioExecutionResult<String> result = FlowTestV2.scenario("multi-table")
            .when(() -> "done")
            .then(t -> t
                .table("orders", order -> order
                    .inserted(1)
                    .inspect(ctx -> assertThat(ctx.insertedOne().getColumn("status")).isEqualTo("CREATED")))
                .table("order_items", items -> items.inserted(2)))
            .execute(executor);

        assertThat(result.getResult()).isEqualTo("done");
    }

    // ===== Scenario C: Same table, multiple change types =====

    @Test
    void shouldVerifySameTableWithMultipleChangeTypes() throws Exception {
        RecordingObservationExecutor observationExecutor = new RecordingObservationExecutor(Arrays.asList(
            snapshot("orders",
                row(1L, "status", "CREATED", "tenant_id", 100L),
                row(2L, "status", "PENDING", "tenant_id", 100L)),
            snapshot("orders",
                row(1L, "status", "PAID", "tenant_id", 100L),
                row(3L, "status", "CREATED", "tenant_id", 100L))
        ));
        ScenarioExecutor executor = new ScenarioExecutor(observationExecutor);

        ScenarioExecutionResult<String> result = FlowTestV2.scenario("multi-change-type")
            .when(() -> "done")
            .then(t -> t.table("orders", order -> order
                .inserted(1)
                .modified(1)
                .deleted(1)
                .modifiedRow(ModifiedRowAssertions.changed("status", "CREATED", "PAID"))))
            .execute(executor);

        assertThat(result.getResult()).isEqualTo("done");
    }

    // ===== Scenario D: Complex return value + multi-table =====

    @Test
    void shouldVerifyComplexReturnValueWithMultiTable() throws Exception {
        RecordingObservationExecutor observationExecutor = new RecordingObservationExecutor(Arrays.asList(
            new ObservationSnapshot(Arrays.asList(
                new ResourceSnapshot("orders", Collections.<RowSnapshot>emptyList()),
                new ResourceSnapshot("order_items", Collections.<RowSnapshot>emptyList())
            )),
            new ObservationSnapshot(Arrays.asList(
                new ResourceSnapshot("orders", Arrays.asList(row(10L, "status", "CREATED"))),
                new ResourceSnapshot("order_items", Arrays.asList(
                    row(100L, "order_id", 10L, "sku", "A"),
                    row(101L, "order_id", 10L, "sku", "B")
                ))
            ))
        ));
        ScenarioExecutor executor = new ScenarioExecutor(observationExecutor);

        ScenarioExecutionResult<Long> result = FlowTestV2.scenario("complex-return")
            .when(() -> 10L)
            .then(t -> t
                .returns(10L)
                .table("orders", order -> order.inserted(1))
                .table("order_items", items -> items.inserted(2))
                .inspect(ctx -> {
                    Long orderId = (Long) ctx.table("orders").insertedOne().getColumn("id");
                    assertThat(ctx.table("order_items").insertedRows())
                        .allMatch(row -> orderId.equals(row.getColumn("order_id")));
                }))
            .execute(executor);

        assertThat(result.getResult()).isEqualTo(10L);
    }

    // ===== Scenario E: Fixture before/after + table assertions =====

    @Test
    void shouldVerifyFixtureBeforeAfterWithTableAssertions() throws Exception {
        final FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");
        RecordingFixtureExecutor fixtureExecutor = new RecordingFixtureExecutor(
            user, new TestUser(1L, "Alice", 100L, "v1"), new TestUser(1L, "Alice", 80L, "v1"));
        RecordingObservationExecutor observationExecutor = new RecordingObservationExecutor(Arrays.asList(
            new ObservationSnapshot(Arrays.asList(
                new ResourceSnapshot(TestUser.class.getName(), Collections.singletonList(row(1L, "name", "Alice", "balance", 100L))),
                new ResourceSnapshot("orders", Collections.<RowSnapshot>emptyList())
            )),
            new ObservationSnapshot(Arrays.asList(
                new ResourceSnapshot(TestUser.class.getName(), Collections.singletonList(row(1L, "name", "Alice", "balance", 80L))),
                new ResourceSnapshot("orders", Arrays.asList(row(10L, "status", "CREATED")))
            ))
        ));
        ScenarioExecutor executor = new ScenarioExecutor(fixtureExecutor, observationExecutor);

        ScenarioExecutionResult<Long> result = FlowTestV2.scenario("fixture-and-table")
            .given(g -> g.fixture(user, FixtureTrait.set(TestUser::setName, "Alice")))
            .observe(o -> o.table("orders"))
            .when(() -> 10L)
            .then(t -> t
                .returns(10L)
                .fixture(user, u -> u
                    .before(v -> assertThat(v.getBalance()).isEqualTo(100L))
                    .after(v -> assertThat(v.getBalance()).isEqualTo(80L)))
                .table("orders", order -> order.inserted(1))
                .entity(TestUser.class, entity -> entity.modified(1)))
            .execute(executor);

        assertThat(result.getResult()).isEqualTo(10L);
        assertThat(fixtureExecutor.isReloaded()).isTrue();
    }

    // ===== Fixture alias-first assertions =====

    @Test
    void shouldVerifyFixtureByAlias() throws Exception {
        final FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");
        RecordingFixtureExecutor fixtureExecutor = new RecordingFixtureExecutor(
            user, new TestUser(1L, "before"), new TestUser(1L, "after"));
        RecordingObservationExecutor observationExecutor = new RecordingObservationExecutor(Arrays.asList(
            snapshot(TestUser.class.getName()),
            snapshot(TestUser.class.getName())
        ));
        ScenarioExecutor executor = new ScenarioExecutor(fixtureExecutor, observationExecutor);

        ScenarioExecutionResult<String> result = FlowTestV2.scenario("alias-fixture")
            .given(g -> g.fixture("user", TestUser.class, f -> f.set(TestUser::setName, "before")))
            .when(() -> "ok")
            .then(t -> t.fixture("user", TestUser.class, u -> u
                .inspect(ctx -> {
                    assertThat(ctx.before().getName()).isEqualTo("before");
                    assertThat(ctx.after().getName()).isEqualTo("after");
                })))
            .execute(executor);

        assertThat(result.getResult()).isEqualTo("ok");
        assertThat(fixtureExecutor.isReloaded()).isTrue();
    }

    // ===== Fixture afterMatches =====

    @Test
    void shouldVerifyFixtureAfterStateFromBeforePlusPatch() throws Exception {
        final FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");
        RecordingFixtureExecutor fixtureExecutor = new RecordingFixtureExecutor(
            user,
            new TestUser(1L, "Alice", 100L, "v1"),
            new TestUser(1L, "Alice", 80L, "v1")
        );
        RecordingObservationExecutor observationExecutor = new RecordingObservationExecutor(Arrays.asList(
            snapshot(TestUser.class.getName()),
            snapshot(TestUser.class.getName())
        ));
        ScenarioExecutor executor = new ScenarioExecutor(fixtureExecutor, observationExecutor);

        FlowTestV2.scenario("fixture-patch")
            .given(g -> g.fixture(user, FixtureTrait.set(TestUser::setName, "Alice")))
            .when(() -> "ok")
            .then(t -> t.fixture(user, u -> u.afterMatches(
                FixtureStatePatch.of(TestUser.class).set(TestUser::getBalance, 80L))))
            .execute(executor);

        assertThat(fixtureExecutor.isReloaded()).isTrue();
    }

    @Test
    void shouldDetectUnexpectedFixtureFieldChange() throws Exception {
        final FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");
        RecordingFixtureExecutor fixtureExecutor = new RecordingFixtureExecutor(
            user,
            new TestUser(1L, "Alice", 100L, "v1"),
            new TestUser(1L, "Bob", 80L, "v1")
        );
        RecordingObservationExecutor observationExecutor = new RecordingObservationExecutor(Arrays.asList(
            snapshot(TestUser.class.getName()),
            snapshot(TestUser.class.getName())
        ));
        ScenarioExecutor executor = new ScenarioExecutor(fixtureExecutor, observationExecutor);

        assertThatThrownBy(() -> FlowTestV2.scenario("fixture-patch-mismatch")
            .given(g -> g.fixture(user, FixtureTrait.set(TestUser::setName, "Alice")))
            .when(() -> "ok")
            .then(t -> t.fixture(user, u -> u.afterMatches(
                FixtureStatePatch.of(TestUser.class).set(TestUser::getBalance, 80L))))
            .execute(executor))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("name expected <Alice> but was <Bob>");
    }

    // ===== Expected failure handling =====

    @Test
    void shouldHandleExpectedFailure() throws Exception {
        RecordingObservationExecutor observationExecutor = new RecordingObservationExecutor(Arrays.asList(
            snapshot("orders"),
            snapshot("orders")
        ));
        ScenarioExecutor executor = new ScenarioExecutor(observationExecutor);

        ScenarioExecutionResult<String> result = FlowTestV2.scenario("expected-failure")
            .observe(o -> o.table("orders"))
            .<String>when(() -> {
                throw new IllegalStateException("boom");
            })
            .then(t -> t.failure(IllegalStateException.class)
                .inspect(ctx -> assertThat(ctx.failure()).hasMessage("boom")))
            .execute(executor);

        assertThat(result.getFailure()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRethrowUnhandledActionFailure() {
        RecordingObservationExecutor observationExecutor = new RecordingObservationExecutor(Arrays.asList(
            snapshot("orders"),
            snapshot("orders")
        ));
        ScenarioExecutor executor = new ScenarioExecutor(observationExecutor);

        assertThatThrownBy(() -> FlowTestV2.scenario("unhandled-failure")
            .observe(o -> o.table("orders"))
            .when(() -> {
                throw new IllegalStateException("boom");
            })
            .execute(executor))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("boom");
    }

    // ===== Thread-bound executor =====

    @Test
    void shouldRunScenarioThroughThreadBoundExecutor() throws Exception {
        RecordingObservationExecutor observationExecutor = new RecordingObservationExecutor(Arrays.asList(
            snapshot("orders"),
            snapshot("orders", row(1L, "status", "CREATED"))
        ));
        ScenarioExecutor executor = new ScenarioExecutor(observationExecutor);

        ScenarioExecutors.bind(executor);
        try {
            ScenarioExecutionResult<String> result = FlowTestV2.scenario("thread-bound")
                .when(() -> "done")
                .then(t -> t
                    .success()
                    .table("orders", order -> order.inserted(1)))
                .run();

            assertThat(result.getResult()).isEqualTo("done");
        } finally {
            ScenarioExecutors.clear();
        }
    }

    @Test
    void shouldRejectRunWithoutCurrentExecutor() {
        assertThatThrownBy(() -> FlowTestV2.scenario("no-runner")
            .observe(o -> o.table("orders"))
            .when(() -> "ok")
            .run())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No active ScenarioExecutor");
    }

    // ===== Row-level assertions =====

    @Test
    void shouldVerifyRowLevelInsertAndModify() throws Exception {
        RecordingObservationExecutor observationExecutor = new RecordingObservationExecutor(Arrays.asList(
            snapshot("orders", row(1L, "status", "CREATED", "tenant_id", 100L)),
            snapshot("orders",
                row(1L, "status", "PAID", "tenant_id", 100L),
                row(2L, "status", "CREATED", "tenant_id", 100L))
        ));
        ScenarioExecutor executor = new ScenarioExecutor(observationExecutor);

        ScenarioExecutionResult<String> result = FlowTestV2.scenario("row-level")
            .when(() -> "done")
            .then(t -> t.table("orders", order -> order
                .insertedRow(RowAssertions.allOf(
                    RowAssertions.columnEquals("id", 2L),
                    RowAssertions.columnEquals("status", "CREATED")))
                .modifiedRow(ModifiedRowAssertions.changed("status", "CREATED", "PAID"))))
            .execute(executor);

        assertThat(result.getResult()).isEqualTo("done");
    }

    // ===== FixtureTrait.draft and FixtureTrait.set =====

    @Test
    void shouldSupportTraitDraftForReusableTraits() throws Exception {
        RecordingObservationExecutor observationExecutor = new RecordingObservationExecutor(Arrays.asList(
            snapshot(TestUser.class.getName()),
            snapshot(TestUser.class.getName())
        ));
        final FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");
        RecordingFixtureExecutor fixtureExecutor = new RecordingFixtureExecutor(
            user, new TestUser(1L, "Alice", 100L, "v1"), new TestUser(1L, "Alice", 100L, "v1"));
        ScenarioExecutor executor = new ScenarioExecutor(fixtureExecutor, observationExecutor);

        // Reusable trait via draft
        FixtureTrait<TestUser> baseUser = FixtureTrait.draft(f -> f
            .set(TestUser::setId, 1L)
            .set(TestUser::setName, "Alice")
            .set(TestUser::setBalance, 100L));

        ScenarioExecutionResult<String> result = FlowTestV2.scenario("trait-draft")
            .given(g -> g.fixture(user, baseUser))
            .when(() -> "ok")
            .then(t -> t.success())
            .execute(executor);

        assertThat(result.getResult()).isEqualTo("ok");
    }

    // ===== returns() convenience =====

    @Test
    void shouldVerifyReturnValueWithReturns() throws Exception {
        RecordingObservationExecutor observationExecutor = new RecordingObservationExecutor(Arrays.asList(
            snapshot("orders"),
            snapshot("orders", row(1L, "status", "CREATED"))
        ));
        ScenarioExecutor executor = new ScenarioExecutor(observationExecutor);

        ScenarioExecutionResult<Long> result = FlowTestV2.scenario("returns-value")
            .when(() -> 42L)
            .then(t -> t
                .returns(42L)
                .table("orders", order -> order.inserted(1)))
            .execute(executor);

        assertThat(result.getResult()).isEqualTo(42L);
    }

    // ===== failureSatisfying =====

    @Test
    void shouldVerifyFailureWithCustomAssertion() throws Exception {
        RecordingObservationExecutor observationExecutor = new RecordingObservationExecutor(Arrays.asList(
            snapshot("orders"),
            snapshot("orders")
        ));
        ScenarioExecutor executor = new ScenarioExecutor(observationExecutor);

        FlowTestV2.scenario("failure-satisfying")
            .observe(o -> o.table("orders"))
            .<String>when(() -> {
                throw new IllegalStateException("invalid order");
            })
            .then(t -> t.failureSatisfying(IllegalStateException.class,
                ex -> assertThat(ex.getMessage()).contains("invalid")))
            .execute(executor);
    }

    // ========== Helpers ==========

    private static ObservationSnapshot snapshot(String resourceName, RowSnapshot... rows) {
        return new ObservationSnapshot(Collections.singletonList(new ResourceSnapshot(resourceName, Arrays.asList(rows))));
    }

    private static RowSnapshot row(Long id, Object... kv) {
        Map<String, Object> columns = new LinkedHashMap<String, Object>();
        columns.put("id", id);
        for (int i = 0; i < kv.length; i += 2) {
            columns.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return new RowSnapshot(RowKey.of(id), columns);
    }

    private static final class RecordingObservationExecutor implements ObservationExecutor {
        private final List<ObservationSnapshot> snapshots;
        private int index;
        private CleanupPolicy lastCleanupPolicy;

        private RecordingObservationExecutor(List<ObservationSnapshot> snapshots) {
            this.snapshots = new ArrayList<ObservationSnapshot>(snapshots);
        }

        @Override
        public ObservationSnapshot capture(List<ObservationSpec> observations) {
            ObservationSnapshot snapshot = snapshots.get(index);
            index++;
            return snapshot;
        }

        @Override
        public void cleanup(List<ObservationSpec> observations, ObservationDiff diff, CleanupPolicy cleanupPolicy) {
            this.lastCleanupPolicy = cleanupPolicy;
        }

        public CleanupPolicy getLastCleanupPolicy() {
            return lastCleanupPolicy;
        }
    }

    private static final class RecordingFixtureExecutor implements FixtureExecutor {
        private final FixtureHandle<TestUser> handle;
        private final TestUser created;
        private final TestUser reloaded;
        private boolean reloadedFlag;
        private boolean cleaned;

        private RecordingFixtureExecutor(FixtureHandle<TestUser> handle, TestUser created, TestUser reloaded) {
            this.handle = handle;
            this.created = created;
            this.reloaded = reloaded;
        }

        @Override
        public FixtureExecution prepare(List<FixtureSpec<?>> fixtures) {
            return new FixtureExecution() {
                @Override
                public <T> T resolve(FixtureHandle<T> target) {
                    return target.getType().cast(created);
                }

                @Override
                public <T> T reload(FixtureHandle<T> target) {
                    reloadedFlag = true;
                    return target.getType().cast(reloaded);
                }

                @Override
                public <T> FixtureStateMetadata describe(FixtureHandle<T> target) {
                    return FixtureStateMetadata.of(target.getType(),
                        Arrays.asList("id", "name", "balance", "version"));
                }

                @Override
                public void cleanup() {
                    cleaned = true;
                }
            };
        }

        public boolean isReloaded() { return reloadedFlag; }
        public boolean isCleaned() { return cleaned; }
    }

    private static final class TestUser {
        private Long id;
        private String name;
        private Long balance;
        private String version;

        private TestUser() {}
        private TestUser(Long id, String name) { this.id = id; this.name = name; }
        private TestUser(Long id, String name, Long balance, String version) {
            this.id = id; this.name = name; this.balance = balance; this.version = version;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getBalance() { return balance; }
        public void setBalance(Long balance) { this.balance = balance; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }
}
