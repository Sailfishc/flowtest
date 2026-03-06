package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.FlowTestV2;
import com.github.sailfishc.flowtest.v2.assertion.FixtureAssertion;
import com.github.sailfishc.flowtest.v2.spec.CleanupPolicy;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
import com.github.sailfishc.flowtest.v2.spec.RouteCondition;
import com.github.sailfishc.flowtest.v2.spec.RouteScope;
import org.junit.jupiter.api.Test;

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
                    .observe(new java.util.function.Consumer<ObserveSpec>() {
                        @Override
                        public void accept(ObserveSpec observe) {
                            observe.shardedTable("t_order", RouteScope.empty());
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
            .observe(new java.util.function.Consumer<ObserveSpec>() {
                @Override
                public void accept(ObserveSpec observe) {
                    observe.fixture(user);
                    observe.shardedTable("t_order", RouteScope.of(RouteCondition.eq("tenant_id", 1001L)));
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

    private static final class User {
        private long tenantId;
    }
}
