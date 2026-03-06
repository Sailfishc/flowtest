package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.assertion.ExpectationSet;
import com.github.sailfishc.flowtest.v2.assertion.FixtureAssertion;
import com.github.sailfishc.flowtest.v2.assertion.FixtureStateExpectation;
import com.github.sailfishc.flowtest.v2.assertion.ResourceChangeExpectation;
import com.github.sailfishc.flowtest.v2.assertion.ResultAssertion;
import com.github.sailfishc.flowtest.v2.spec.CleanupPolicy;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureSpec;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;
import com.github.sailfishc.flowtest.v2.spec.RouteScope;
import com.github.sailfishc.flowtest.v2.spec.ThrowingSupplier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Default mutable builder used by the public DSL.
 */
public final class DefaultScenarioBuilder implements ScenarioBuilder {

    private final String name;
    private final List<FixtureSpec<?>> fixtures = new ArrayList<FixtureSpec<?>>();
    private final List<ObservationSpec> observations = new ArrayList<ObservationSpec>();
    private CleanupPolicy cleanupPolicy = CleanupPolicy.DELETE_INSERTED;

    public DefaultScenarioBuilder(String name) {
        this.name = name;
    }

    @Override
    public ScenarioBuilder given(Consumer<GivenSpec> given) {
        given.accept(new DefaultGivenSpec(fixtures));
        return this;
    }

    @Override
    public ScenarioBuilder observe(Consumer<ObserveSpec> observe) {
        observe.accept(new DefaultObserveSpec(observations));
        return this;
    }

    @Override
    public ScenarioBuilder cleanup(CleanupPolicy cleanupPolicy) {
        this.cleanupPolicy = cleanupPolicy;
        return this;
    }

    @Override
    public <R> ScenarioPlan<R> when(ThrowingSupplier<R> action) {
        return new DefaultScenarioPlan<R>(name, fixtures, observations, cleanupPolicy, action);
    }

    private static final class DefaultGivenSpec implements GivenSpec {

        private final List<FixtureSpec<?>> fixtures;

        private DefaultGivenSpec(List<FixtureSpec<?>> fixtures) {
            this.fixtures = fixtures;
        }

        @Override
        public <T> FixtureHandle<T> persist(Class<T> entityType, FixtureTrait<? super T>... traits) {
            FixtureHandle<T> handle = FixtureHandle.anonymous(entityType);
            persist(handle, traits);
            return handle;
        }

        @Override
        public <T> GivenSpec persist(FixtureHandle<T> handle, FixtureTrait<? super T>... traits) {
            List<FixtureTrait<? super T>> declaredTraits = new ArrayList<FixtureTrait<? super T>>();
            if (traits != null) {
                for (FixtureTrait<? super T> trait : traits) {
                    declaredTraits.add(trait);
                }
            }
            fixtures.add(new FixtureSpec<T>(handle, handle.getType(), declaredTraits));
            return this;
        }
    }

    private static final class DefaultObserveSpec implements ObserveSpec {

        private final List<ObservationSpec> observations;

        private DefaultObserveSpec(List<ObservationSpec> observations) {
            this.observations = observations;
        }

        @Override
        public ObserveSpec fixture(FixtureHandle<?> handle) {
            observations.add(ObservationSpec.fixture(handle));
            return this;
        }

        @Override
        public ObserveSpec table(String tableName) {
            observations.add(ObservationSpec.table(tableName, RouteScope.empty(), false));
            return this;
        }

        @Override
        public ObserveSpec table(String tableName, RouteScope routeScope) {
            observations.add(ObservationSpec.table(tableName, routeScope, false));
            return this;
        }

        @Override
        public ObserveSpec shardedTable(String tableName, RouteScope routeScope) {
            observations.add(ObservationSpec.table(tableName, routeScope, true));
            return this;
        }

        @Override
        public ObserveSpec entity(Class<?> entityType) {
            observations.add(ObservationSpec.entity(entityType, RouteScope.empty(), false));
            return this;
        }

        @Override
        public ObserveSpec entity(Class<?> entityType, RouteScope routeScope) {
            observations.add(ObservationSpec.entity(entityType, routeScope, false));
            return this;
        }

        @Override
        public ObserveSpec shardedEntity(Class<?> entityType, RouteScope routeScope) {
            observations.add(ObservationSpec.entity(entityType, routeScope, true));
            return this;
        }
    }

    private static final class DefaultScenarioPlan<R> implements ScenarioPlan<R>, ThenSpec<R> {

        private final String name;
        private final List<FixtureSpec<?>> fixtures;
        private final List<ObservationSpec> observations;
        private final CleanupPolicy cleanupPolicy;
        private final ThrowingSupplier<R> action;
        private final List<ResultAssertion<R>> resultAssertions = new ArrayList<ResultAssertion<R>>();
        private final List<ResourceChangeExpectation> changeExpectations = new ArrayList<ResourceChangeExpectation>();
        private final List<FixtureStateExpectation<?>> fixtureExpectations = new ArrayList<FixtureStateExpectation<?>>();

        private DefaultScenarioPlan(String name,
                                    List<FixtureSpec<?>> fixtures,
                                    List<ObservationSpec> observations,
                                    CleanupPolicy cleanupPolicy,
                                    ThrowingSupplier<R> action) {
            this.name = name;
            this.fixtures = new ArrayList<FixtureSpec<?>>(fixtures);
            this.observations = new ArrayList<ObservationSpec>(observations);
            this.cleanupPolicy = cleanupPolicy;
            this.action = action;
        }

        @Override
        public ScenarioPlan<R> then(Consumer<ThenSpec<R>> then) {
            then.accept(this);
            return this;
        }

        @Override
        public ScenarioDefinition<R> definition() {
            return new ScenarioDefinition<R>(
                name,
                fixtures,
                observations,
                cleanupPolicy,
                action,
                new ExpectationSet<R>(resultAssertions, changeExpectations, fixtureExpectations)
            );
        }

        @Override
        public CompiledScenario<R> compile() {
            return new ScenarioCompiler().compile(definition());
        }

        @Override
        public ScenarioExecutionResult<R> execute(ScenarioExecutor executor) throws Exception {
            return compile().execute(executor);
        }

        @Override
        public ThenSpec<R> expectNoException() {
            resultAssertions.add(new ResultAssertion<R>() {
                @Override
                public void verify(R result, Throwable failure) {
                    if (failure != null) {
                        throw new AssertionError("Expected no exception but got " + failure.getClass().getName(), failure);
                    }
                }
            });
            return this;
        }

        @Override
        public ThenSpec<R> expectException(final Class<? extends Throwable> exceptionType) {
            resultAssertions.add(new ResultAssertion<R>() {
                @Override
                public void verify(R result, Throwable failure) {
                    if (failure == null) {
                        throw new AssertionError("Expected exception of type " + exceptionType.getName() + " but nothing was thrown");
                    }
                    if (!exceptionType.isInstance(failure)) {
                        throw new AssertionError("Expected exception of type " + exceptionType.getName()
                            + " but got " + failure.getClass().getName(), failure);
                    }
                }
            });
            return this;
        }

        @Override
        public ThenSpec<R> outcome(ResultAssertion<R> assertion) {
            resultAssertions.add(assertion);
            return this;
        }

        @Override
        public ThenSpec<R> inserted(String resourceName, long count) {
            changeExpectations.add(new ResourceChangeExpectation(resourceName, count, null, null));
            return this;
        }

        @Override
        public ThenSpec<R> deleted(String resourceName, long count) {
            changeExpectations.add(new ResourceChangeExpectation(resourceName, null, count, null));
            return this;
        }

        @Override
        public ThenSpec<R> modified(String resourceName, long count) {
            changeExpectations.add(new ResourceChangeExpectation(resourceName, null, null, count));
            return this;
        }

        @Override
        public <T> ThenSpec<R> fixture(FixtureHandle<T> handle, FixtureAssertion<T> assertion) {
            fixtureExpectations.add(new FixtureStateExpectation<T>(handle, assertion));
            return this;
        }
    }
}
