package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.FlowTestV2;
import com.github.sailfishc.flowtest.v2.fixture.FixtureMaterializer;
import com.github.sailfishc.flowtest.v2.spec.CleanupPolicy;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
import com.github.sailfishc.flowtest.v2.spec.RouteScope;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioCompilerTest {

    @Test
    void compilesScenarioWithAutoInferredObservation() {
        CompiledScenario<String> compiled = FlowTestV2.scenario("auto-infer")
            .when(() -> "ok")
            .then(t -> t.table("t_order", order -> order.inserted(1)))
            .compile();

        assertThat(compiled.getDefinition().getObservations()).hasSize(1);
        assertThat(compiled.getDefinition().getObservations().get(0).getResourceName()).isEqualTo("t_order");
    }

    @Test
    void compilesScenarioWithoutObservationWhenNoThenResources() {
        // A scenario that only asserts result, no resource observation needed
        CompiledScenario<String> compiled = FlowTestV2.scenario("result-only")
            .when(() -> "ok")
            .then(t -> t.success().returns("ok"))
            .compile();

        assertThat(compiled.getDefinition().getObservations()).isEmpty();
    }

    @Test
    void rejectsShardedObservationWithoutRouteScope() {
        assertThatThrownBy(() ->
            FlowTestV2.scenario("missing-route")
                .observe(o -> o.table("t_order", r -> r.route(RouteScope.empty())))
                .when(() -> "ok")
                .compile()
        ).isInstanceOf(ScenarioValidationException.class)
            .hasMessageContaining("Route scope is required");
    }

    @Test
    void compilesObserveDslWithRouteAndDynamicTable() {
        CompiledScenario<String> compiled = FlowTestV2.scenario("observe-dsl")
            .observe(o -> o.table("t_order", r -> r
                .dynamicTableBy("bucket", "a")
                .route("tenant_id", 1001L)))
            .when(() -> "ok")
            .then(t -> t.success())
            .compile();

        assertThat(compiled.getDefinition().getObservations()).hasSize(1);
        assertThat(compiled.getDefinition().getObservations().get(0).isRouteRequired()).isTrue();
        assertThat(compiled.getDefinition().getObservations().get(0).getRouteScope().getConditions()).hasSize(1);
        assertThat(compiled.getDefinition().getObservations().get(0).getTableRouteScope().getValues()).hasSize(1);
    }

    @Test
    void compilesMixedFixtureAndTableScenario() {
        CompiledScenario<String> compiled = FlowTestV2.scenario("create-order")
            .given(g -> g.fixture("buyer", User.class, FixtureTrait.set(User::setTenantId, 1001L)))
            .observe(o -> o.table("t_order", r -> r.route("tenant_id", 1001L)))
            .cleanup(CleanupPolicy.DELETE_INSERTED)
            .when(() -> "order-1")
            .then(t -> t
                .success()
                .table("t_order", order -> order.inserted(1))
                .fixture("buyer", User.class, user -> user.after(v -> {})))
            .compile();

        assertThat(compiled.getDefinition().getFixtures()).hasSize(1);
        // 1 explicit (t_order with route) + auto-inferred fixture observation
        assertThat(compiled.getDefinition().getObservations()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(compiled.getDefinition().getCleanupPolicy()).isEqualTo(CleanupPolicy.DELETE_INSERTED);
    }

    @Test
    void mergesExplicitAndInferredObservations() {
        CompiledScenario<String> compiled = FlowTestV2.scenario("merge-obs")
            .observe(o -> o.table("t_order", r -> r.route("tenant_id", 100L)))
            .when(() -> "ok")
            .then(t -> t
                .table("t_order", order -> order.inserted(1))
                .table("t_user", user -> user.modified(1)))
            .compile();

        assertThat(compiled.getDefinition().getObservations()).hasSize(2);
        // t_order should keep its route from explicit observe
        assertThat(compiled.getDefinition().getObservations().get(0).getResourceName()).isEqualTo("t_order");
        assertThat(compiled.getDefinition().getObservations().get(0).isRouteRequired()).isTrue();
        // t_user should be auto-inferred without route
        assertThat(compiled.getDefinition().getObservations().get(1).getResourceName()).isEqualTo("t_user");
        assertThat(compiled.getDefinition().getObservations().get(1).isRouteRequired()).isFalse();
    }

    @Test
    void supportsFixturesWithBuilderBasedConstruction() {
        CompiledScenario<String> compiled = FlowTestV2.scenario("builder-fixture")
            .given(g -> g.fixture("alice", User.class, f -> f
                .set(User::setId, 1L)
                .set(User::setTenantId, 100L)
                .set(User::setName, "Alice")))
            .observe(o -> o.entity(User.class))
            .when(() -> "ok")
            .compile();

        Map<FixtureHandle<?>, Object> values = new FixtureMaterializer().materialize(compiled.getDefinition().getFixtures());
        User alice = (User) values.get(compiled.getDefinition().getFixtures().get(0).getHandle());

        assertThat(alice.id).isEqualTo(1L);
        assertThat(alice.tenantId).isEqualTo(100L);
        assertThat(alice.name).isEqualTo("Alice");
    }

    @Test
    void supportsBatchFixturesWithDefaults() {
        CompiledScenario<String> compiled = FlowTestV2.scenario("batch-fixtures")
            .given(g -> g.fixtures(User.class, rows -> rows
                .defaults(f -> f
                    .set(User::setTenantId, 100L)
                    .set(User::setBalance, 100L))
                .row("alice", f -> f
                    .set(User::setId, 1L)
                    .set(User::setName, "Alice"))
                .row("bob", f -> f
                    .set(User::setId, 2L)
                    .set(User::setName, "Bob")
                    .set(User::setBalance, 200L))))
            .observe(o -> o.entity(User.class))
            .when(() -> "ok")
            .compile();

        Map<FixtureHandle<?>, Object> values = new FixtureMaterializer().materialize(compiled.getDefinition().getFixtures());
        User alice = (User) values.get(compiled.getDefinition().getFixtures().get(0).getHandle());
        User bob = (User) values.get(compiled.getDefinition().getFixtures().get(1).getHandle());

        assertThat(compiled.getDefinition().getFixtures()).hasSize(2);
        assertThat(compiled.getDefinition().getFixtures().get(0).getHandle().getName()).isEqualTo("alice");
        assertThat(compiled.getDefinition().getFixtures().get(1).getHandle().getName()).isEqualTo("bob");
        assertThat(alice.tenantId).isEqualTo(100L);
        assertThat(alice.balance).isEqualTo(100L);
        assertThat(alice.name).isEqualTo("Alice");
        assertThat(bob.tenantId).isEqualTo(100L);
        assertThat(bob.balance).isEqualTo(200L);
        assertThat(bob.name).isEqualTo("Bob");
    }

    @Test
    void supportsMixedTraitAndBuilderInBatchFixtures() {
        CompiledScenario<String> compiled = FlowTestV2.scenario("mixed-batch")
            .given(g -> g.fixtures(User.class, rows -> rows
                .defaults(FixtureTrait.set(User::setTenantId, 100L))
                .row("alice", f -> f
                    .apply(FixtureTrait.set(User::setId, 1L))
                    .set(User::setName, "Alice"))))
            .observe(o -> o.entity(User.class))
            .when(() -> "ok")
            .compile();

        Map<FixtureHandle<?>, Object> values = new FixtureMaterializer().materialize(compiled.getDefinition().getFixtures());
        User alice = (User) values.get(compiled.getDefinition().getFixtures().get(0).getHandle());

        assertThat(alice.tenantId).isEqualTo(100L);
        assertThat(alice.id).isEqualTo(1L);
        assertThat(alice.name).isEqualTo("Alice");
    }

    @Test
    void rejectsDuplicateFixtureAlias() {
        assertThatThrownBy(() -> FlowTestV2.scenario("duplicate-alias")
            .given(g -> {
                g.fixture("user", User.class, FixtureTrait.set(User::setId, 1L));
                g.fixture("user", Account.class, FixtureTrait.set(Account::setId, 2L));
            })
            .observe(o -> o.entity(User.class))
            .when(() -> "ok")
            .compile())
            .isInstanceOf(ScenarioValidationException.class)
            .hasMessageContaining("Duplicate fixture alias declared: user");
    }

    @Test
    void rejectsFixtureExpectationReferencingUndeclaredHandle() {
        final FixtureHandle<User> ghost = FixtureHandle.named(User.class, "ghost");

        assertThatThrownBy(() ->
            FlowTestV2.scenario("undeclared-fixture-expectation")
                .observe(o -> o.table("t_order"))
                .when(() -> "ok")
                .then(t -> t.fixture(ghost, user -> user.after(v -> {})))
                .compile()
        ).isInstanceOf(ScenarioValidationException.class)
            .hasMessageContaining("references undeclared handle");
    }

    // ==================================================================================
    // P0-1: Duplicate observation resourceName detection
    // ==================================================================================

    @Test
    void rejectsDuplicateObservationResourceName() {
        assertThatThrownBy(() ->
            FlowTestV2.scenario("duplicate-observation")
                .observe(o -> {
                    o.table("t_order");
                    o.table("t_order");
                })
                .when(() -> "ok")
                .compile()
        ).isInstanceOf(ScenarioValidationException.class)
            .hasMessageContaining("Duplicate observed resource 't_order'");
    }

    @Test
    void rejectsDuplicateEntityObservationWithDifferentRoutes() {
        assertThatThrownBy(() ->
            FlowTestV2.scenario("duplicate-entity-observation")
                .observe(o -> {
                    o.entity(User.class, r -> r.route("tenant_id", 100L));
                    o.entity(User.class, r -> r.route("tenant_id", 200L));
                })
                .when(() -> "ok")
                .compile()
        ).isInstanceOf(ScenarioValidationException.class)
            .hasMessageContaining("Duplicate observed resource")
            .hasMessageContaining("unique within a scenario");
    }

    // ==================================================================================
    // P0-2: Unsupported CleanupPolicy early rejection
    // ==================================================================================

    @Test
    void rejectsRollbackCleanupPolicy() {
        assertThatThrownBy(() ->
            FlowTestV2.scenario("rollback-cleanup")
                .cleanup(CleanupPolicy.ROLLBACK)
                .when(() -> "ok")
                .then(t -> t.success())
                .compile()
        ).isInstanceOf(ScenarioValidationException.class)
            .hasMessageContaining("CleanupPolicy.ROLLBACK is not supported");
    }

    @Test
    void rejectsCustomCompensatorCleanupPolicy() {
        assertThatThrownBy(() ->
            FlowTestV2.scenario("custom-compensator")
                .cleanup(CleanupPolicy.CUSTOM_COMPENSATOR)
                .when(() -> "ok")
                .then(t -> t.success())
                .compile()
        ).isInstanceOf(ScenarioValidationException.class)
            .hasMessageContaining("CleanupPolicy.CUSTOM_COMPENSATOR is not supported");
    }

    @Test
    void acceptsDeleteInsertedCleanupPolicy() {
        CompiledScenario<String> compiled = FlowTestV2.scenario("delete-inserted")
            .cleanup(CleanupPolicy.DELETE_INSERTED)
            .when(() -> "ok")
            .then(t -> t.success())
            .compile();

        assertThat(compiled.getDefinition().getCleanupPolicy()).isEqualTo(CleanupPolicy.DELETE_INSERTED);
    }

    @Test
    void acceptsRestoreBeforeImageCleanupPolicy() {
        CompiledScenario<String> compiled = FlowTestV2.scenario("restore-before")
            .cleanup(CleanupPolicy.RESTORE_BEFORE_IMAGE)
            .when(() -> "ok")
            .then(t -> t.success())
            .compile();

        assertThat(compiled.getDefinition().getCleanupPolicy()).isEqualTo(CleanupPolicy.RESTORE_BEFORE_IMAGE);
    }

    // ==================================================================================
    // P0-2: Ambiguous same-type fixture expectations
    // ==================================================================================

    @Test
    void rejectsAmbiguousFixtureExpectationWithMultipleSameTypeFixtures() {
        assertThatThrownBy(() ->
            FlowTestV2.scenario("ambiguous-fixture")
                .given(g -> {
                    g.fixture("alice", User.class, FixtureTrait.set(User::setId, 1L));
                    g.fixture("bob", User.class, FixtureTrait.set(User::setId, 2L));
                })
                .when(() -> "ok")
                // Using Class-based shorthand is ambiguous when multiple same-type fixtures exist
                .then(t -> t.fixture(User.class, u -> u.after(v -> {})))
                .compile()
        ).isInstanceOf(ScenarioValidationException.class)
            .hasMessageContaining("ambiguous")
            .hasMessageContaining("multiple fixtures");
    }

    @Test
    void rejectsFixtureExpectationWithMismatchedDefaultAlias() {
        assertThatThrownBy(() ->
            FlowTestV2.scenario("mismatched-alias")
                .given(g -> g.fixture("primaryUser", User.class, FixtureTrait.set(User::setId, 1L)))
                .when(() -> "ok")
                // Class-based shorthand uses "User" as alias, but fixture is "primaryUser"
                .then(t -> t.fixture(User.class, u -> u.after(v -> {})))
                .compile()
        ).isInstanceOf(ScenarioValidationException.class)
            .hasMessageContaining("default alias")
            .hasMessageContaining("primaryUser");
    }

    @Test
    void acceptsExplicitAliasForMultipleSameTypeFixtures() {
        CompiledScenario<String> compiled = FlowTestV2.scenario("explicit-alias")
            .given(g -> {
                g.fixture("alice", User.class, FixtureTrait.set(User::setId, 1L));
                g.fixture("bob", User.class, FixtureTrait.set(User::setId, 2L));
            })
            .when(() -> "ok")
            .then(t -> t
                .fixture("alice", User.class, u -> u.after(v -> {}))
                .fixture("bob", User.class, u -> u.after(v -> {})))
            .compile();

        assertThat(compiled.getDefinition().getFixtures()).hasSize(2);
    }

    private static final class User {
        private long id;
        private long tenantId;
        private long balance;
        private String name;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public long getTenantId() { return tenantId; }
        public void setTenantId(long tenantId) { this.tenantId = tenantId; }
        public long getBalance() { return balance; }
        public void setBalance(long balance) { this.balance = balance; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    private static final class Account {
        private long id;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
    }
}
