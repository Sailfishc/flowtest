package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.FlowTestV2;
import com.github.sailfishc.flowtest.v2.assertion.FixtureChangeAssertion;
import com.github.sailfishc.flowtest.v2.assertion.ModifiedRowAssertions;
import com.github.sailfishc.flowtest.v2.assertion.RowAssertions;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TableChangeSpecTest {

    @Test
    void shouldCompileTableChangeSpecWithInsertedCount() {
        CompiledScenario<String> compiled = FlowTestV2.scenario("table-spec-inserted")
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
                    then.expectNoException();
                    then.table("t_order")
                        .inserted(2)
                        .and();
                }
            })
            .compile();

        assertThat(compiled.getDefinition().getExpectations().getChangeExpectations()).hasSize(1);
        assertThat(compiled.getDefinition().getExpectations().getChangeExpectations().get(0).getResourceName())
            .isEqualTo("t_order");
        assertThat(compiled.getDefinition().getExpectations().getChangeExpectations().get(0).getExpectedInserted())
            .isEqualTo(2L);
    }

    @Test
    void shouldCompileTableChangeSpecWithInsertedRows() {
        CompiledScenario<String> compiled = FlowTestV2.scenario("table-spec-rows")
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
                    then.table("t_order")
                        .inserted(2)
                        .insertedRows(new java.util.function.Consumer<com.github.sailfishc.flowtest.v2.assertion.RowListSpec>() {
                            @Override
                            public void accept(com.github.sailfishc.flowtest.v2.assertion.RowListSpec rows) {
                                rows.sortBy("id")
                                    .row(0, RowAssertions.columns("STATUS", "CREATED"))
                                    .row(1, RowAssertions.columns("STATUS", "PAID"));
                            }
                        })
                        .and();
                }
            })
            .compile();

        // 1 count expectation + 1 assertion expectation (insertedRows)
        assertThat(compiled.getDefinition().getExpectations().getChangeExpectations()).hasSize(1);
        assertThat(compiled.getDefinition().getExpectations().getChangeAssertionExpectations()).hasSize(1);
    }

    @Test
    void shouldCompileTableChangeSpecWithChainedCountsAndReturn() {
        CompiledScenario<String> compiled = FlowTestV2.scenario("table-spec-chain")
            .watch(new java.util.function.Consumer<WatchSpec>() {
                @Override
                public void accept(WatchSpec watch) {
                    watch.table("t_order").table("t_user");
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
                    then.table("t_order")
                            .inserted(1)
                            .and()
                        .table("t_user")
                            .modified(1)
                            .and();
                }
            })
            .compile();

        assertThat(compiled.getDefinition().getExpectations().getChangeExpectations()).hasSize(2);
    }

    @Test
    void shouldCompileFixtureChangeExpectation() {
        final FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");

        CompiledScenario<String> compiled = FlowTestV2.scenario("fixture-change")
            .given(new java.util.function.Consumer<GivenSpec>() {
                @Override
                public void accept(GivenSpec given) {
                    given.persist(user, FixtureTrait.of(new java.util.function.Consumer<TestUser>() {
                        @Override
                        public void accept(TestUser u) {
                            u.name = "Alice";
                        }
                    }));
                }
            })
            .watch(new java.util.function.Consumer<WatchSpec>() {
                @Override
                public void accept(WatchSpec watch) {
                    watch.fixture(user);
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
                    then.fixtureChange(user, new FixtureChangeAssertion<TestUser>() {
                        @Override
                        public void verify(TestUser before, TestUser after) {
                            // placeholder
                        }
                    });
                }
            })
            .compile();

        assertThat(compiled.getDefinition().getExpectations().getFixtureChangeExpectations()).hasSize(1);
    }

    @Test
    void shouldRejectFixtureChangeForUndeclaredHandle() {
        final FixtureHandle<TestUser> ghost = FixtureHandle.named(TestUser.class, "ghost");

        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                FlowTestV2.scenario("undeclared-fixture-change")
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
                            then.fixtureChange(ghost, new FixtureChangeAssertion<TestUser>() {
                                @Override
                                public void verify(TestUser before, TestUser after) {
                                }
                            });
                        }
                    })
                    .compile();
            }
        }).isInstanceOf(ScenarioValidationException.class)
            .hasMessageContaining("Fixture change expectation references undeclared handle");
    }

    @Test
    void shouldCompileEntityChangeSpec() {
        CompiledScenario<String> compiled = FlowTestV2.scenario("entity-spec")
            .watch(new java.util.function.Consumer<WatchSpec>() {
                @Override
                public void accept(WatchSpec watch) {
                    watch.entity(TestUser.class);
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
                    then.entity(TestUser.class)
                        .modified(1)
                        .and();
                }
            })
            .compile();

        assertThat(compiled.getDefinition().getExpectations().getChangeExpectations()).hasSize(1);
        assertThat(compiled.getDefinition().getExpectations().getChangeExpectations().get(0).getResourceName())
            .isEqualTo(TestUser.class.getName());
    }

    private static final class TestUser {
        String name;
    }
}
