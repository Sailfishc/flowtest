package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.FlowTestV2;
import com.github.sailfishc.flowtest.v2.fixture.FixtureExecution;
import com.github.sailfishc.flowtest.v2.fixture.FixtureExecutor;
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

    @Test
    void shouldExecuteScenarioAndVerifyInsertedRows() throws Exception {
        List<ObservationSnapshot> snapshots = Arrays.asList(
            snapshot("orders"),
            snapshot("orders", row(1L, "status", "CREATED"))
        );
        RecordingObservationExecutor observationExecutor = new RecordingObservationExecutor(snapshots);
        ScenarioExecutor executor = new ScenarioExecutor(observationExecutor);

        ScenarioExecutionResult<String> result = FlowTestV2.scenario("act-only")
            .watch(w -> w.table("orders"))
            .when(() -> "done")
            .then(t -> t.expectNoException().inserted("orders", 1))
            .execute(executor);

        assertThat(result.getResult()).isEqualTo("done");
        assertThat(result.getDiff().getChange("orders").getInsertedCount()).isEqualTo(1L);
        assertThat(observationExecutor.getLastCleanupPolicy()).isEqualTo(CleanupPolicy.DELETE_INSERTED);
    }

    @Test
    void shouldRunScenarioThroughThreadBoundExecutor() throws Exception {
        List<ObservationSnapshot> snapshots = Arrays.asList(
            snapshot("orders"),
            snapshot("orders", row(1L, "status", "CREATED"))
        );
        ScenarioExecutor executor = new ScenarioExecutor(new RecordingObservationExecutor(snapshots));

        ScenarioExecutors.bind(executor);
        try {
            ScenarioExecutionResult<String> result = FlowTestV2.scenario("thread-bound")
                .watch(w -> w.table("orders"))
                .when(() -> "done")
                .verify(ctx -> {
                    ctx.success();
                    assertThat(ctx.table("orders").insertedCount()).isEqualTo(1L);
                })
                .run();

            assertThat(result.getResult()).isEqualTo("done");
        } finally {
            ScenarioExecutors.clear();
        }
    }

    @Test
    void shouldReloadFixtureForFixtureAssertions() throws Exception {
        final FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");
        RecordingFixtureExecutor fixtureExecutor = new RecordingFixtureExecutor(user, new TestUser(1L, "before"), new TestUser(1L, "after"));
        RecordingObservationExecutor observationExecutor = new RecordingObservationExecutor(Arrays.asList(
            snapshot(TestUser.class.getName()),
            snapshot(TestUser.class.getName())
        ));
        ScenarioExecutor executor = new ScenarioExecutor(fixtureExecutor, observationExecutor);

        ScenarioExecutionResult<String> result = FlowTestV2.scenario("mixed")
            .given(g -> g.persist(user, nameTrait("before")))
            .watch(w -> w.fixture(user))
            .cleanup(CleanupPolicy.DELETE_FIXTURE)
            .when(() -> "ok")
            .then(t -> t.fixture(user, value -> assertThat(value.getName()).isEqualTo("after")))
            .execute(executor);

        assertThat(result.getResult()).isEqualTo("ok");
        assertThat(fixtureExecutor.isReloaded()).isTrue();
        assertThat(fixtureExecutor.isCleaned()).isTrue();
    }

    @Test
    void shouldVerifyRowLevelChangeAssertions() throws Exception {
        List<ObservationSnapshot> snapshots = Arrays.asList(
            snapshot("orders", row(1L, "status", "CREATED", "tenant_id", 100L)),
            snapshot(
                "orders",
                row(1L, "status", "PAID", "tenant_id", 100L),
                row(2L, "status", "CREATED", "tenant_id", 100L)
            )
        );
        RecordingObservationExecutor observationExecutor = new RecordingObservationExecutor(snapshots);
        ScenarioExecutor executor = new ScenarioExecutor(observationExecutor);

        ScenarioExecutionResult<String> result = FlowTestV2.scenario("row-level")
            .watch(w -> w.table("orders"))
            .when(() -> "done")
            .then(t -> t.expectNoException()
                .insertedRow("orders", RowAssertions.allOf(
                    RowAssertions.columnEquals("id", 2L),
                    RowAssertions.columnEquals("status", "CREATED")
                ))
                .modifiedRow("orders", ModifiedRowAssertions.changed("status", "CREATED", "PAID"))
                .change("orders", change -> assertThat(change.getModifiedRows()).hasSize(1)))
            .execute(executor);

        assertThat(result.getResult()).isEqualTo("done");
    }

    @Test
    void shouldVerifyScenarioThroughContext() throws Exception {
        final FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");
        RecordingFixtureExecutor fixtureExecutor = new RecordingFixtureExecutor(user, new TestUser(1L, "before"), new TestUser(1L, "after"));
        List<ObservationSnapshot> snapshots = Arrays.asList(
            new ObservationSnapshot(Arrays.asList(
                new ResourceSnapshot(TestUser.class.getName(), Collections.singletonList(row(1L, "name", "before"))),
                new ResourceSnapshot("orders", Collections.<RowSnapshot>emptyList())
            )),
            new ObservationSnapshot(Arrays.asList(
                new ResourceSnapshot(TestUser.class.getName(), Collections.singletonList(row(1L, "name", "after"))),
                new ResourceSnapshot("orders", Arrays.asList(
                    row(10L, "tenant_id", 100L, "status", "CREATED"),
                    row(11L, "tenant_id", 100L, "status", "CREATED")
                ))
            ))
        );
        RecordingObservationExecutor observationExecutor = new RecordingObservationExecutor(snapshots);
        ScenarioExecutor executor = new ScenarioExecutor(fixtureExecutor, observationExecutor);

        ScenarioExecutionResult<Long> result = FlowTestV2.scenario("verify-context")
            .given(g -> g.persist(user, nameTrait("before")))
            .watch(w -> w.fixture(user).table("orders"))
            .when(() -> 10L)
            .verify(ctx -> {
                ctx.success();
                assertThat(ctx.result()).isEqualTo(10L);
                assertThat(ctx.fixture(user).before().getName()).isEqualTo("before");
                assertThat(ctx.fixture(user).after().getName()).isEqualTo("after");
                assertThat(ctx.entity(TestUser.class).modifiedCount()).isEqualTo(1L);
                assertThat(ctx.table("orders").insertedCount()).isEqualTo(2L);
                assertThat(ctx.table("orders").insertedRows())
                    .extracting(row -> row.getColumn("status"))
                    .containsExactly("CREATED", "CREATED");
            })
            .execute(executor);

        assertThat(result.getResult()).isEqualTo(10L);
    }

    @Test
    void shouldAllowVerifyToHandleExpectedFailure() throws Exception {
        RecordingObservationExecutor observationExecutor = new RecordingObservationExecutor(Arrays.asList(
            snapshot("orders"),
            snapshot("orders")
        ));
        ScenarioExecutor executor = new ScenarioExecutor(observationExecutor);

        ScenarioExecutionResult<String> result = FlowTestV2.scenario("verify-failure")
            .watch(w -> w.table("orders"))
            .<String>when(() -> {
                throw new IllegalStateException("boom");
            })
            .verify(ctx -> {
                ctx.failure(IllegalStateException.class);
                assertThat(ctx.failure()).hasMessage("boom");
            })
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

        assertThatThrownBy(() -> FlowTestV2.scenario("failure")
            .watch(w -> w.table("orders"))
            .when(() -> {
                throw new IllegalStateException("boom");
            })
            .execute(executor))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("boom");
    }

    @Test
    void shouldRejectRunWithoutCurrentExecutor() {
        assertThatThrownBy(() -> FlowTestV2.scenario("no-runner")
            .watch(w -> w.table("orders"))
            .when(() -> "ok")
            .run())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No active ScenarioExecutor");
    }

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

    private static FixtureTrait<TestUser> nameTrait(final String name) {
        return new FixtureTrait<TestUser>() {
            @Override
            public void apply(TestUser target, com.github.sailfishc.flowtest.v2.spec.TraitContext context) {
                target.setName(name);
            }
        };
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
                public void cleanup() {
                    cleaned = true;
                }
            };
        }

        public boolean isReloaded() {
            return reloadedFlag;
        }

        public boolean isCleaned() {
            return cleaned;
        }
    }

    private static final class TestUser {

        private Long id;
        private String name;

        private TestUser() {
        }

        private TestUser(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
