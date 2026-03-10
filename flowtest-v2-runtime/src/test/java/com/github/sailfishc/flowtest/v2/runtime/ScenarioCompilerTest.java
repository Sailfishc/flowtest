package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.FlowTestV2;
import com.github.sailfishc.flowtest.v2.assertion.FixtureAssertion;
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
    void rejectsScenarioWithoutObservationScope() {
        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                FlowTestV2.scenario("missing-observation")
                    .when(new com.github.sailfishc.flowtest.v2.spec.ThrowingSupplier<String>() {
                        @Override
                        public String get() {
                            return "ok";
                        }
                    })
                    .compile();
            }
        }).isInstanceOf(ScenarioValidationException.class)
            .hasMessageContaining("At least one observed resource");
    }

    @Test
    void rejectsShardedObservationWithoutRouteScope() {
        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                FlowTestV2.scenario("missing-route")
                    .watch(new java.util.function.Consumer<WatchSpec>() {
                        @Override
                        public void accept(WatchSpec watch) {
                            watch.table("t_order").route(RouteScope.empty());
                        }
                    })
                    .when(new com.github.sailfishc.flowtest.v2.spec.ThrowingSupplier<String>() {
                        @Override
                        public String get() {
                            return "ok";
                        }
                    })
                    .compile();
            }
        }).isInstanceOf(ScenarioValidationException.class)
            .hasMessageContaining("Route scope is required");
    }

    @Test
    void compilesWatchDslWithRouteAndDynamicTable() {
        CompiledScenario<String> compiled = FlowTestV2.scenario("watch-dsl")
            .watch(new java.util.function.Consumer<WatchSpec>() {
                @Override
                public void accept(WatchSpec watch) {
                    watch.table("t_order")
                        .dynamicTableBy("bucket", "a")
                        .route("tenant_id", 1001L);
                }
            })
            .when(new com.github.sailfishc.flowtest.v2.spec.ThrowingSupplier<String>() {
                @Override
                public String get() {
                    return "ok";
                }
            })
            .verify(new ScenarioVerification<String>() {
                @Override
                public void verify(VerifyContext<String> context) {
                    context.success();
                }
            })
            .compile();

        assertThat(compiled.getDefinition().getObservations()).hasSize(1);
        assertThat(compiled.getDefinition().getObservations().get(0).isRouteRequired()).isTrue();
        assertThat(compiled.getDefinition().getObservations().get(0).getRouteScope().getConditions()).hasSize(1);
        assertThat(compiled.getDefinition().getObservations().get(0).getTableRouteScope().getValues()).hasSize(1);
    }

    @Test
    void compilesMixedFixtureAndWatchOnlyScenario() {
        final FixtureHandle<User> user = FixtureHandle.named(User.class, "buyer");

        CompiledScenario<String> compiled = FlowTestV2.scenario("create-order")
            .given(new java.util.function.Consumer<GivenSpec>() {
                @Override
                public void accept(GivenSpec given) {
                    given.persist(user, FixtureTrait.of(new java.util.function.Consumer<User>() {
                        @Override
                        public void accept(User value) {
                            value.tenantId = 1001L;
                        }
                    }));
                }
            })
            .watch(new java.util.function.Consumer<WatchSpec>() {
                @Override
                public void accept(WatchSpec watch) {
                    watch.fixture(user).table("t_order").route("tenant_id", 1001L);
                }
            })
            .cleanup(CleanupPolicy.DELETE_INSERTED)
            .when(new com.github.sailfishc.flowtest.v2.spec.ThrowingSupplier<String>() {
                @Override
                public String get() {
                    return "order-1";
                }
            })
            .then(new java.util.function.Consumer<ThenSpec<String>>() {
                @Override
                public void accept(ThenSpec<String> then) {
                    then.expectNoException();
                    then.inserted("t_order", 1L);
                    then.fixture(user, new FixtureAssertion<User>() {
                        @Override
                        public void verify(User value) {
                            // Placeholder for future fixture assertions.
                        }
                    });
                }
            })
            .compile();

        assertThat(compiled.getDefinition().getFixtures()).hasSize(1);
        assertThat(compiled.getDefinition().getObservations()).hasSize(2);
        assertThat(compiled.getDefinition().getCleanupPolicy()).isEqualTo(CleanupPolicy.DELETE_INSERTED);
    }

    @Test
    void resolvesFixtureAliasObservationDuringCompile() {
        CompiledScenario<String> compiled = FlowTestV2.scenario("alias-watch")
            .watch(w -> w.fixture("buyer"))
            .given(g -> g.persist("buyer", User.class, FixtureTrait.of(value -> value.tenantId = 1001L)))
            .when(() -> "ok")
            .compile();

        assertThat(compiled.getDefinition().getFixtures()).hasSize(1);
        assertThat(compiled.getDefinition().getObservations()).hasSize(1);
        assertThat(compiled.getDefinition().getObservations().get(0).getFixtureAlias()).isNull();
        assertThat(compiled.getDefinition().getObservations().get(0).getFixtureHandle().getName()).isEqualTo("buyer");
    }

    @Test
    void supportsPersistRowsWithDefaultsAndRowOverrides() {
        CompiledScenario<String> compiled = FlowTestV2.scenario("persist-rows")
            .given(g -> g.persistRows(User.class, rows -> rows
                .defaults(
                    FixtureTrait.of(value -> value.tenantId = 1001L),
                    FixtureTrait.of(value -> value.balance = 100L))
                .row("alice",
                    FixtureTrait.of(value -> value.id = 1L),
                    FixtureTrait.of(value -> value.name = "Alice"))
                .row("bob",
                    FixtureTrait.of(value -> value.id = 2L),
                    FixtureTrait.of(value -> value.name = "Bob"),
                    FixtureTrait.of(value -> value.balance = 200L))))
            .watch(w -> w.entity(User.class))
            .when(() -> "ok")
            .compile();

        Map<FixtureHandle<?>, Object> values = new FixtureMaterializer().materialize(compiled.getDefinition().getFixtures());
        User alice = (User) values.get(compiled.getDefinition().getFixtures().get(0).getHandle());
        User bob = (User) values.get(compiled.getDefinition().getFixtures().get(1).getHandle());

        assertThat(compiled.getDefinition().getFixtures()).hasSize(2);
        assertThat(compiled.getDefinition().getFixtures().get(0).getHandle().getName()).isEqualTo("alice");
        assertThat(compiled.getDefinition().getFixtures().get(1).getHandle().getName()).isEqualTo("bob");
        assertThat(alice.tenantId).isEqualTo(1001L);
        assertThat(alice.balance).isEqualTo(100L);
        assertThat(alice.name).isEqualTo("Alice");
        assertThat(bob.tenantId).isEqualTo(1001L);
        assertThat(bob.balance).isEqualTo(200L);
        assertThat(bob.name).isEqualTo("Bob");
    }

    @Test
    void rejectsDuplicateFixtureAlias() {
        assertThatThrownBy(() -> FlowTestV2.scenario("duplicate-alias")
            .given(g -> g
                .persist("user", User.class, FixtureTrait.of(value -> value.id = 1L))
                .persist("user", Account.class, FixtureTrait.of(value -> value.id = 2L)))
            .watch(w -> w.entity(User.class))
            .when(() -> "ok")
            .compile())
            .isInstanceOf(ScenarioValidationException.class)
            .hasMessageContaining("Duplicate fixture alias declared: user");
    }

    @Test
    void rejectsFixtureExpectationReferencingUndeclaredHandle() {
        final FixtureHandle<User> ghost = FixtureHandle.named(User.class, "ghost");

        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                FlowTestV2.scenario("undeclared-fixture-expectation")
                    .watch(new java.util.function.Consumer<WatchSpec>() {
                        @Override
                        public void accept(WatchSpec watch) {
                            watch.table("t_order");
                        }
                    })
                    .when(new com.github.sailfishc.flowtest.v2.spec.ThrowingSupplier<String>() {
                        @Override
                        public String get() {
                            return "ok";
                        }
                    })
                    .then(new java.util.function.Consumer<ThenSpec<String>>() {
                        @Override
                        public void accept(ThenSpec<String> then) {
                            then.fixture(ghost, new FixtureAssertion<User>() {
                                @Override
                                public void verify(User value) {
                                }
                            });
                        }
                    })
                    .compile();
            }
        }).isInstanceOf(ScenarioValidationException.class)
            .hasMessageContaining("Fixture expectation references undeclared handle");
    }

    private static final class User {
        private long id;
        private long tenantId;
        private long balance;
        private String name;
    }

    private static final class Account {
        private long id;
    }
}
