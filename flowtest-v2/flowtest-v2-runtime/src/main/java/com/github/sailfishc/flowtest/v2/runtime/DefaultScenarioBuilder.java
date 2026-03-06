package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.assertion.ExpectationSet;
import com.github.sailfishc.flowtest.v2.assertion.FixtureAssertion;
import com.github.sailfishc.flowtest.v2.assertion.FixtureStateExpectation;
import com.github.sailfishc.flowtest.v2.assertion.ModifiedRowAssertion;
import com.github.sailfishc.flowtest.v2.assertion.ResourceChangeAssertion;
import com.github.sailfishc.flowtest.v2.assertion.ResourceChangeAssertionExpectation;
import com.github.sailfishc.flowtest.v2.assertion.ResourceChangeExpectation;
import com.github.sailfishc.flowtest.v2.assertion.ResultAssertion;
import com.github.sailfishc.flowtest.v2.assertion.RowAssertion;
import com.github.sailfishc.flowtest.v2.spec.CleanupPolicy;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureSpec;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;
import com.github.sailfishc.flowtest.v2.spec.RouteCondition;
import com.github.sailfishc.flowtest.v2.spec.RouteScope;
import com.github.sailfishc.flowtest.v2.spec.TableRouteScope;
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
    public ScenarioBuilder watch(Consumer<WatchSpec> watch) {
        watch.accept(new DefaultWatchSpec(observations));
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

    private static final class DefaultWatchSpec implements WatchSpec {

        private final List<ObservationSpec> observations;

        private DefaultWatchSpec(List<ObservationSpec> observations) {
            this.observations = observations;
        }

        @Override
        public WatchSpec fixture(FixtureHandle<?> handle) {
            observations.add(ObservationSpec.fixture(handle));
            return this;
        }

        @Override
        public WatchResourceSpec table(String tableName) {
            ObservationSpec observation = ObservationSpec.table(tableName, TableRouteScope.empty(), RouteScope.empty(), false);
            observations.add(observation);
            return new DefaultWatchResourceSpec(observations, observations.size() - 1);
        }

        @Override
        public WatchResourceSpec entity(Class<?> entityType) {
            ObservationSpec observation = ObservationSpec.entity(entityType, TableRouteScope.empty(), RouteScope.empty(), false);
            observations.add(observation);
            return new DefaultWatchResourceSpec(observations, observations.size() - 1);
        }
    }

    private static final class DefaultWatchResourceSpec implements WatchResourceSpec {

        private final List<ObservationSpec> observations;
        private final int index;

        private DefaultWatchResourceSpec(List<ObservationSpec> observations, int index) {
            this.observations = observations;
            this.index = index;
        }

        @Override
        public WatchSpec fixture(FixtureHandle<?> handle) {
            return new DefaultWatchSpec(observations).fixture(handle);
        }

        @Override
        public WatchResourceSpec table(String tableName) {
            return new DefaultWatchSpec(observations).table(tableName);
        }

        @Override
        public WatchResourceSpec entity(Class<?> entityType) {
            return new DefaultWatchSpec(observations).entity(entityType);
        }

        @Override
        public WatchResourceSpec route(String columnName, Object value) {
            return route(RouteCondition.eq(columnName, value));
        }

        @Override
        public WatchResourceSpec route(RouteCondition condition) {
            ObservationSpec current = currentObservation();
            return replace(current.getTableRouteScope(), current.getRouteScope().append(condition), true);
        }

        @Override
        public WatchResourceSpec route(RouteScope routeScope) {
            ObservationSpec current = currentObservation();
            RouteScope merged = current.getRouteScope();
            for (RouteCondition condition : routeScope.getConditions()) {
                merged = merged.append(condition);
            }
            return replace(current.getTableRouteScope(), merged, true);
        }

        @Override
        public WatchResourceSpec dynamicTableBy(String key, Object value) {
            return tableBy(key, value);
        }

        @Override
        public WatchResourceSpec dynamicTable(TableRouteScope tableRouteScope) {
            return tableRoute(tableRouteScope);
        }

        @Override
        public WatchResourceSpec tableBy(String key, Object value) {
            ObservationSpec current = currentObservation();
            return replace(current.getTableRouteScope().append(key, value), current.getRouteScope(), current.isRouteRequired());
        }

        @Override
        public WatchResourceSpec tableRoute(TableRouteScope tableRouteScope) {
            ObservationSpec current = currentObservation();
            TableRouteScope merged = current.getTableRouteScope();
            for (com.github.sailfishc.flowtest.v2.spec.TableRouteValue value : tableRouteScope.getValues()) {
                merged = merged.append(value);
            }
            return replace(merged, current.getRouteScope(), current.isRouteRequired());
        }

        private ObservationSpec currentObservation() {
            return observations.get(index);
        }

        private WatchResourceSpec replace(TableRouteScope tableRouteScope, RouteScope routeScope, boolean routeRequired) {
            ObservationSpec current = currentObservation();
            observations.set(index, recreate(current, tableRouteScope, routeScope, routeRequired));
            return this;
        }

        private ObservationSpec recreate(ObservationSpec base,
                                         TableRouteScope tableRouteScope,
                                         RouteScope routeScope,
                                         boolean routeRequired) {
            if (base.getResourceKind() == com.github.sailfishc.flowtest.v2.spec.ResourceKind.TABLE) {
                return ObservationSpec.table(base.getResourceName(), tableRouteScope, routeScope, routeRequired);
            }
            return ObservationSpec.entity(base.getResourceType(), tableRouteScope, routeScope, routeRequired);
        }
    }

    private static final class DefaultScenarioPlan<R> implements ScenarioPlan<R>, ThenSpec<R> {

        private final String name;
        private final List<FixtureSpec<?>> fixtures;
        private final List<ObservationSpec> observations;
        private final CleanupPolicy cleanupPolicy;
        private final ThrowingSupplier<R> action;
        private final List<ResultAssertion<R>> resultAssertions = new ArrayList<ResultAssertion<R>>();
        private final List<ScenarioVerification<R>> scenarioVerifications = new ArrayList<ScenarioVerification<R>>();
        private final List<ResourceChangeExpectation> changeExpectations = new ArrayList<ResourceChangeExpectation>();
        private final List<ResourceChangeAssertionExpectation> changeAssertionExpectations =
            new ArrayList<ResourceChangeAssertionExpectation>();
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
        public ScenarioPlan<R> verify(ScenarioVerification<R> verification) {
            scenarioVerifications.add(verification);
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
                new ExpectationSet<R>(
                    resultAssertions,
                    changeExpectations,
                    changeAssertionExpectations,
                    fixtureExpectations
                ),
                scenarioVerifications
            );
        }

        @Override
        public CompiledScenario<R> compile() {
            return new ScenarioCompiler().compile(definition());
        }

        @Override
        public ScenarioExecutionResult<R> run() throws Exception {
            return compile().run();
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
        public ThenSpec<R> change(String resourceName, ResourceChangeAssertion assertion) {
            changeAssertionExpectations.add(new ResourceChangeAssertionExpectation(resourceName, assertion));
            return this;
        }

        @Override
        public ThenSpec<R> insertedRow(String resourceName, final RowAssertion assertion) {
            return change(resourceName, new ResourceChangeAssertion() {
                @Override
                public void verify(com.github.sailfishc.flowtest.v2.spec.ResourceChange change) {
                    assertAnyInsertedRowMatches(change, assertion);
                }
            });
        }

        @Override
        public ThenSpec<R> deletedRow(String resourceName, final RowAssertion assertion) {
            return change(resourceName, new ResourceChangeAssertion() {
                @Override
                public void verify(com.github.sailfishc.flowtest.v2.spec.ResourceChange change) {
                    assertAnyDeletedRowMatches(change, assertion);
                }
            });
        }

        @Override
        public ThenSpec<R> modifiedRow(String resourceName, final ModifiedRowAssertion assertion) {
            return change(resourceName, new ResourceChangeAssertion() {
                @Override
                public void verify(com.github.sailfishc.flowtest.v2.spec.ResourceChange change) {
                    assertAnyModifiedRowMatches(change, assertion);
                }
            });
        }

        @Override
        public <T> ThenSpec<R> fixture(FixtureHandle<T> handle, FixtureAssertion<T> assertion) {
            fixtureExpectations.add(new FixtureStateExpectation<T>(handle, assertion));
            return this;
        }

        private void assertAnyInsertedRowMatches(com.github.sailfishc.flowtest.v2.spec.ResourceChange change,
                                                 RowAssertion assertion) {
            assertAnyRowMatches("inserted", change.getInsertedRows(), assertion);
        }

        private void assertAnyDeletedRowMatches(com.github.sailfishc.flowtest.v2.spec.ResourceChange change,
                                                RowAssertion assertion) {
            assertAnyRowMatches("deleted", change.getDeletedRows(), assertion);
        }

        private void assertAnyModifiedRowMatches(com.github.sailfishc.flowtest.v2.spec.ResourceChange change,
                                                 ModifiedRowAssertion assertion) {
            AssertionError lastError = null;
            for (com.github.sailfishc.flowtest.v2.spec.ModifiedRow row : change.getModifiedRows()) {
                try {
                    assertion.verify(row);
                    return;
                } catch (AssertionError ex) {
                    lastError = ex;
                }
            }
            String message = "No modified row matched expectation for resource " + change.getResourceName();
            if (lastError == null) {
                throw new AssertionError(message);
            }
            throw new AssertionError(message + ": " + lastError.getMessage(), lastError);
        }

        private void assertAnyRowMatches(String rowType,
                                         List<com.github.sailfishc.flowtest.v2.spec.RowSnapshot> rows,
                                         RowAssertion assertion) {
            AssertionError lastError = null;
            for (com.github.sailfishc.flowtest.v2.spec.RowSnapshot row : rows) {
                try {
                    assertion.verify(row);
                    return;
                } catch (AssertionError ex) {
                    lastError = ex;
                }
            }
            String message = "No " + rowType + " row matched expectation";
            if (lastError == null) {
                throw new AssertionError(message);
            }
            throw new AssertionError(message + ": " + lastError.getMessage(), lastError);
        }
    }
}
