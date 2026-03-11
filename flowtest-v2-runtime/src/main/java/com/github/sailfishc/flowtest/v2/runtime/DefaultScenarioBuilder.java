package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.assertion.ExpectationSet;
import com.github.sailfishc.flowtest.v2.assertion.FixtureAssertion;
import com.github.sailfishc.flowtest.v2.assertion.FixtureChangeAssertion;
import com.github.sailfishc.flowtest.v2.assertion.FixtureChangeExpectation;
import com.github.sailfishc.flowtest.v2.assertion.FixtureStateExpectation;
import com.github.sailfishc.flowtest.v2.assertion.ModifiedRowAssertion;
import com.github.sailfishc.flowtest.v2.assertion.ModifiedRowListSpec;
import com.github.sailfishc.flowtest.v2.assertion.ResourceChangeAssertion;
import com.github.sailfishc.flowtest.v2.assertion.ResourceChangeAssertionExpectation;
import com.github.sailfishc.flowtest.v2.assertion.ResourceChangeExpectation;
import com.github.sailfishc.flowtest.v2.assertion.ResultAssertion;
import com.github.sailfishc.flowtest.v2.assertion.RowAssertion;
import com.github.sailfishc.flowtest.v2.assertion.RowListAssertions;
import com.github.sailfishc.flowtest.v2.assertion.RowListSpec;
import com.github.sailfishc.flowtest.v2.spec.CleanupPolicy;
import com.github.sailfishc.flowtest.v2.spec.FixtureBuilder;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureSpec;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;
import com.github.sailfishc.flowtest.v2.spec.RouteCondition;
import com.github.sailfishc.flowtest.v2.spec.RouteScope;
import com.github.sailfishc.flowtest.v2.spec.TableRouteScope;
import com.github.sailfishc.flowtest.v2.spec.ThrowingSupplier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Default mutable builder used by the public DSL.
 */
public final class DefaultScenarioBuilder implements ScenarioBuilder {

    private final String name;
    private final List<FixtureSpec<?>> fixtures = new ArrayList<FixtureSpec<?>>();
    private final List<ObservationSpec> explicitObservations = new ArrayList<ObservationSpec>();
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
        observe.accept(new DefaultObserveSpec(explicitObservations));
        return this;
    }

    @Override
    public ScenarioBuilder cleanup(CleanupPolicy cleanupPolicy) {
        this.cleanupPolicy = cleanupPolicy;
        return this;
    }

    @Override
    public <R> ScenarioPlan<R> when(ThrowingSupplier<R> action) {
        return new DefaultScenarioPlan<R>(name, fixtures, explicitObservations, cleanupPolicy, action);
    }

    // ========== GivenSpec ==========

    private static final class DefaultGivenSpec implements GivenSpec {

        private final List<FixtureSpec<?>> fixtures;

        private DefaultGivenSpec(List<FixtureSpec<?>> fixtures) {
            this.fixtures = fixtures;
        }

        @Override
        public <T> FixtureHandle<T> fixture(Class<T> entityType, FixtureTrait<? super T>... traits) {
            FixtureHandle<T> handle = FixtureHandle.anonymous(entityType);
            fixture(handle, traits);
            return handle;
        }

        @Override
        public <T> GivenSpec fixture(String alias, Class<T> entityType, FixtureTrait<? super T>... traits) {
            return fixture(FixtureHandle.named(entityType, alias), traits);
        }

        @Override
        public <T> GivenSpec fixture(FixtureHandle<T> handle, FixtureTrait<? super T>... traits) {
            fixtures.add(new FixtureSpec<T>(handle, handle.getType(), asList(traits)));
            return this;
        }

        @Override
        public <T> FixtureHandle<T> fixture(Class<T> entityType, Consumer<FixtureBuilder<T>> builder) {
            FixtureHandle<T> handle = FixtureHandle.anonymous(entityType);
            fixture(handle, builder);
            return handle;
        }

        @Override
        public <T> GivenSpec fixture(String alias, Class<T> entityType, Consumer<FixtureBuilder<T>> builder) {
            return fixture(FixtureHandle.named(entityType, alias), builder);
        }

        @Override
        public <T> GivenSpec fixture(FixtureHandle<T> handle, Consumer<FixtureBuilder<T>> builder) {
            FixtureTrait<T> trait = builderToTrait(builder);
            List<FixtureTrait<? super T>> traits = new ArrayList<FixtureTrait<? super T>>();
            traits.add(trait);
            fixtures.add(new FixtureSpec<T>(handle, handle.getType(), traits));
            return this;
        }

        @Override
        public <T> GivenSpec fixtures(Class<T> entityType, Consumer<RowSetSpec<T>> rows) {
            DefaultRowSetSpec<T> rowSet = new DefaultRowSetSpec<T>(fixtures, entityType);
            rows.accept(rowSet);
            return this;
        }

        private <T> List<FixtureTrait<? super T>> asList(FixtureTrait<? super T>... traits) {
            List<FixtureTrait<? super T>> declaredTraits = new ArrayList<FixtureTrait<? super T>>();
            if (traits != null) {
                for (FixtureTrait<? super T> trait : traits) {
                    declaredTraits.add(trait);
                }
            }
            return declaredTraits;
        }
    }

    // ========== RowSetSpec ==========

    private static final class DefaultRowSetSpec<T> implements RowSetSpec<T> {

        private final List<FixtureSpec<?>> fixtures;
        private final Class<T> entityType;
        private final List<FixtureTrait<? super T>> defaultTraits = new ArrayList<FixtureTrait<? super T>>();

        private DefaultRowSetSpec(List<FixtureSpec<?>> fixtures, Class<T> entityType) {
            this.fixtures = fixtures;
            this.entityType = entityType;
        }

        @Override
        public RowSetSpec<T> defaults(FixtureTrait<? super T>... traits) {
            append(defaultTraits, traits);
            return this;
        }

        @Override
        public RowSetSpec<T> defaults(Consumer<FixtureBuilder<T>> builder) {
            defaultTraits.add(builderToTrait(builder));
            return this;
        }

        @Override
        public RowSetSpec<T> row(FixtureTrait<? super T>... traits) {
            return row(FixtureHandle.anonymous(entityType), traits);
        }

        @Override
        public RowSetSpec<T> row(String alias, FixtureTrait<? super T>... traits) {
            return row(FixtureHandle.named(entityType, alias), traits);
        }

        @Override
        public RowSetSpec<T> row(Consumer<FixtureBuilder<T>> builder) {
            return rowWithBuilder(FixtureHandle.anonymous(entityType), builder);
        }

        @Override
        public RowSetSpec<T> row(String alias, Consumer<FixtureBuilder<T>> builder) {
            return rowWithBuilder(FixtureHandle.named(entityType, alias), builder);
        }

        private RowSetSpec<T> row(FixtureHandle<T> handle, FixtureTrait<? super T>... traits) {
            List<FixtureTrait<? super T>> declaredTraits = new ArrayList<FixtureTrait<? super T>>(defaultTraits);
            append(declaredTraits, traits);
            fixtures.add(new FixtureSpec<T>(handle, entityType, declaredTraits));
            return this;
        }

        private RowSetSpec<T> rowWithBuilder(FixtureHandle<T> handle, Consumer<FixtureBuilder<T>> builder) {
            List<FixtureTrait<? super T>> declaredTraits = new ArrayList<FixtureTrait<? super T>>(defaultTraits);
            declaredTraits.add(builderToTrait(builder));
            fixtures.add(new FixtureSpec<T>(handle, entityType, declaredTraits));
            return this;
        }

        private void append(List<FixtureTrait<? super T>> target, FixtureTrait<? super T>... traits) {
            if (traits == null) {
                return;
            }
            for (FixtureTrait<? super T> trait : traits) {
                target.add(trait);
            }
        }
    }

    // ========== ObserveSpec ==========

    private static final class DefaultObserveSpec implements ObserveSpec {

        private final List<ObservationSpec> observations;

        private DefaultObserveSpec(List<ObservationSpec> observations) {
            this.observations = observations;
        }

        @Override
        public ObserveSpec table(String tableName) {
            observations.add(ObservationSpec.table(tableName, TableRouteScope.empty(), RouteScope.empty(), false));
            return this;
        }

        @Override
        public ObserveSpec table(String tableName, Consumer<ObservedResourceSpec> spec) {
            DefaultObservedResourceSpec resourceSpec = new DefaultObservedResourceSpec();
            spec.accept(resourceSpec);
            observations.add(ObservationSpec.table(tableName, resourceSpec.tableRouteScope, resourceSpec.routeScope,
                resourceSpec.routeCalled));
            return this;
        }

        @Override
        public ObserveSpec entity(Class<?> entityType) {
            observations.add(ObservationSpec.entity(entityType, TableRouteScope.empty(), RouteScope.empty(), false));
            return this;
        }

        @Override
        public ObserveSpec entity(Class<?> entityType, Consumer<ObservedResourceSpec> spec) {
            DefaultObservedResourceSpec resourceSpec = new DefaultObservedResourceSpec();
            spec.accept(resourceSpec);
            observations.add(ObservationSpec.entity(entityType, resourceSpec.tableRouteScope, resourceSpec.routeScope,
                resourceSpec.routeCalled));
            return this;
        }
    }

    private static final class DefaultObservedResourceSpec implements ObservedResourceSpec {

        private TableRouteScope tableRouteScope = TableRouteScope.empty();
        private RouteScope routeScope = RouteScope.empty();
        private boolean routeCalled = false;

        @Override
        public ObservedResourceSpec route(String columnName, Object value) {
            return route(RouteCondition.eq(columnName, value));
        }

        @Override
        public ObservedResourceSpec route(RouteCondition condition) {
            this.routeCalled = true;
            this.routeScope = this.routeScope.append(condition);
            return this;
        }

        @Override
        public ObservedResourceSpec route(RouteScope routeScope) {
            this.routeCalled = true;
            for (RouteCondition condition : routeScope.getConditions()) {
                this.routeScope = this.routeScope.append(condition);
            }
            return this;
        }

        @Override
        public ObservedResourceSpec dynamicTableBy(String key, Object value) {
            this.tableRouteScope = this.tableRouteScope.append(key, value);
            return this;
        }

        @Override
        public ObservedResourceSpec dynamicTable(TableRouteScope tableRouteScope) {
            for (com.github.sailfishc.flowtest.v2.spec.TableRouteValue value : tableRouteScope.getValues()) {
                this.tableRouteScope = this.tableRouteScope.append(value);
            }
            return this;
        }
    }

    // ========== ScenarioPlan + ThenSpec ==========

    private static final class DefaultScenarioPlan<R> implements ScenarioPlan<R> {

        private final String name;
        private final List<FixtureSpec<?>> fixtures;
        private final List<ObservationSpec> explicitObservations;
        private final CleanupPolicy cleanupPolicy;
        private final ThrowingSupplier<R> action;
        private final List<ResultAssertion<R>> resultAssertions = new ArrayList<ResultAssertion<R>>();
        private final List<ResourceChangeExpectation> changeExpectations = new ArrayList<ResourceChangeExpectation>();
        private final List<ResourceChangeAssertionExpectation> changeAssertionExpectations =
            new ArrayList<ResourceChangeAssertionExpectation>();
        private final List<FixtureStateExpectation<?>> fixtureExpectations = new ArrayList<FixtureStateExpectation<?>>();
        private final List<FixtureChangeExpectation<?>> fixtureChangeExpectations = new ArrayList<FixtureChangeExpectation<?>>();
        private final List<ScenarioVerification<R>> scenarioVerifications = new ArrayList<ScenarioVerification<R>>();
        // Track inferred observation resources from then(...)
        private final Set<String> inferredTableResources = new LinkedHashSet<String>();
        private final Set<Class<?>> inferredEntityResources = new LinkedHashSet<Class<?>>();
        // Track fixture aliases referenced in then(...)
        private final Set<String> inferredFixtureAliases = new LinkedHashSet<String>();
        private final Set<FixtureHandle<?>> inferredFixtureHandles = new LinkedHashSet<FixtureHandle<?>>();

        private DefaultScenarioPlan(String name,
                                    List<FixtureSpec<?>> fixtures,
                                    List<ObservationSpec> explicitObservations,
                                    CleanupPolicy cleanupPolicy,
                                    ThrowingSupplier<R> action) {
            this.name = name;
            this.fixtures = new ArrayList<FixtureSpec<?>>(fixtures);
            this.explicitObservations = new ArrayList<ObservationSpec>(explicitObservations);
            this.cleanupPolicy = cleanupPolicy;
            this.action = action;
        }

        @Override
        public ScenarioPlan<R> then(Consumer<ThenSpec<R>> then) {
            DefaultThenSpec<R> thenSpec = new DefaultThenSpec<R>(this);
            then.accept(thenSpec);
            return this;
        }

        @Override
        public ScenarioDefinition<R> definition() {
            List<ObservationSpec> mergedObservations = mergeObservations();
            return new ScenarioDefinition<R>(
                name,
                fixtures,
                mergedObservations,
                cleanupPolicy,
                action,
                new ExpectationSet<R>(
                    resultAssertions,
                    changeExpectations,
                    changeAssertionExpectations,
                    fixtureExpectations,
                    fixtureChangeExpectations
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

        /**
         * Merge explicit observations with inferred observations from then(...).
         * Inferred resources that already have explicit observation are not duplicated.
         */
        private List<ObservationSpec> mergeObservations() {
            Set<String> explicitResourceNames = new LinkedHashSet<String>();
            for (ObservationSpec obs : explicitObservations) {
                explicitResourceNames.add(obs.getResourceName());
            }

            List<ObservationSpec> merged = new ArrayList<ObservationSpec>(explicitObservations);

            // Infer table observations from then(...)
            for (String tableName : inferredTableResources) {
                if (!explicitResourceNames.contains(tableName)) {
                    merged.add(ObservationSpec.table(tableName, TableRouteScope.empty(), RouteScope.empty(), false));
                    explicitResourceNames.add(tableName);
                }
            }

            // Infer entity observations from then(...)
            for (Class<?> entityType : inferredEntityResources) {
                if (!explicitResourceNames.contains(entityType.getName())) {
                    merged.add(ObservationSpec.entity(entityType, TableRouteScope.empty(), RouteScope.empty(), false));
                    explicitResourceNames.add(entityType.getName());
                }
            }

            // Infer fixture observations from then(...)
            for (FixtureHandle<?> handle : inferredFixtureHandles) {
                if (!explicitResourceNames.contains(handle.getType().getName())) {
                    merged.add(ObservationSpec.fixture(handle));
                    explicitResourceNames.add(handle.getType().getName());
                }
            }
            for (String alias : inferredFixtureAliases) {
                boolean alreadyPresent = false;
                for (ObservationSpec obs : merged) {
                    if (alias.equals(obs.getFixtureAlias())
                        || (obs.getFixtureHandle() != null && alias.equals(obs.getFixtureHandle().getName()))) {
                        alreadyPresent = true;
                        break;
                    }
                }
                if (!alreadyPresent) {
                    merged.add(ObservationSpec.fixture(alias));
                }
            }

            return merged;
        }

        // --- Tracking for inferred observations ---

        void trackTable(String tableName) {
            inferredTableResources.add(tableName);
        }

        void trackEntity(Class<?> entityType) {
            inferredEntityResources.add(entityType);
        }

        void trackFixtureHandle(FixtureHandle<?> handle) {
            inferredFixtureHandles.add(handle);
        }

        void trackFixtureAlias(String alias) {
            inferredFixtureAliases.add(alias);
        }

        // --- Expectation registration ---

        void addResultAssertion(ResultAssertion<R> assertion) {
            resultAssertions.add(assertion);
        }

        void addChangeExpectation(ResourceChangeExpectation expectation) {
            changeExpectations.add(expectation);
        }

        void addChangeAssertionExpectation(ResourceChangeAssertionExpectation expectation) {
            changeAssertionExpectations.add(expectation);
        }

        <T> void addFixtureExpectation(FixtureStateExpectation<T> expectation) {
            fixtureExpectations.add(expectation);
        }

        <T> void addFixtureChangeExpectation(FixtureChangeExpectation<T> expectation) {
            fixtureChangeExpectations.add(expectation);
        }

        void addVerification(ScenarioVerification<R> verification) {
            scenarioVerifications.add(verification);
        }
    }

    // ========== ThenSpec ==========

    private static final class DefaultThenSpec<R> implements ThenSpec<R> {

        private final DefaultScenarioPlan<R> plan;

        DefaultThenSpec(DefaultScenarioPlan<R> plan) {
            this.plan = plan;
        }

        @Override
        public ThenSpec<R> success() {
            plan.addResultAssertion(new ResultAssertion<R>() {
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
        public ThenSpec<R> failure(final Class<? extends Throwable> exceptionType) {
            plan.addResultAssertion(new ResultAssertion<R>() {
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
        public ThenSpec<R> failureSatisfying(final Class<? extends Throwable> exceptionType,
                                              final Consumer<? super Throwable> assertion) {
            plan.addResultAssertion(new ResultAssertion<R>() {
                @Override
                public void verify(R result, Throwable failure) {
                    if (failure == null) {
                        throw new AssertionError("Expected exception of type " + exceptionType.getName() + " but nothing was thrown");
                    }
                    if (!exceptionType.isInstance(failure)) {
                        throw new AssertionError("Expected exception of type " + exceptionType.getName()
                            + " but got " + failure.getClass().getName(), failure);
                    }
                    assertion.accept(failure);
                }
            });
            return this;
        }

        @Override
        public ThenSpec<R> returns(final R expected) {
            plan.addResultAssertion(new ResultAssertion<R>() {
                @Override
                public void verify(R result, Throwable failure) {
                    if (failure != null) {
                        throw new AssertionError("Expected no exception but got " + failure.getClass().getName(), failure);
                    }
                    if (expected == null ? result != null : !expected.equals(result)) {
                        throw new AssertionError("Expected result " + expected + " but was " + result);
                    }
                }
            });
            return this;
        }

        @Override
        public ThenSpec<R> returnsSatisfying(final ResultAssertion<? super R> assertion) {
            plan.addResultAssertion(new ResultAssertion<R>() {
                @Override
                public void verify(R result, Throwable failure) {
                    if (failure != null) {
                        throw new AssertionError("Expected no exception but got " + failure.getClass().getName(), failure);
                    }
                    assertion.verify(result, failure);
                }
            });
            return this;
        }

        @Override
        public ThenSpec<R> table(String tableName, Consumer<ResourceExpectationSpec> spec) {
            plan.trackTable(tableName);
            DefaultResourceExpectationSpec resourceSpec = new DefaultResourceExpectationSpec<R>(plan, tableName);
            spec.accept(resourceSpec);
            return this;
        }

        @Override
        public ThenSpec<R> entity(Class<?> entityType, Consumer<ResourceExpectationSpec> spec) {
            plan.trackEntity(entityType);
            DefaultResourceExpectationSpec resourceSpec = new DefaultResourceExpectationSpec<R>(plan, entityType.getName());
            spec.accept(resourceSpec);
            return this;
        }

        @Override
        public <T> ThenSpec<R> fixture(FixtureHandle<T> handle, Consumer<FixtureExpectationSpec<T>> spec) {
            plan.trackFixtureHandle(handle);
            DefaultFixtureExpectationSpec<R, T> fixtureSpec = new DefaultFixtureExpectationSpec<R, T>(plan, handle);
            spec.accept(fixtureSpec);
            return this;
        }

        @Override
        public <T> ThenSpec<R> fixture(String alias, Class<T> type, Consumer<FixtureExpectationSpec<T>> spec) {
            plan.trackFixtureAlias(alias);
            FixtureHandle<T> handle = FixtureHandle.named(type, alias);
            DefaultFixtureExpectationSpec<R, T> fixtureSpec = new DefaultFixtureExpectationSpec<R, T>(plan, handle);
            spec.accept(fixtureSpec);
            return this;
        }

        @Override
        public ThenSpec<R> inspect(final ScenarioInspection<R> inspection) {
            plan.addVerification(new ScenarioVerification<R>() {
                @Override
                public void verify(VerifyContext<R> context) throws Exception {
                    inspection.inspect(context);
                }
            });
            return this;
        }
    }

    // ========== ResourceExpectationSpec ==========

    private static final class DefaultResourceExpectationSpec<R> implements ResourceExpectationSpec {

        private final DefaultScenarioPlan<R> plan;
        private final String resourceName;

        DefaultResourceExpectationSpec(DefaultScenarioPlan<R> plan, String resourceName) {
            this.plan = plan;
            this.resourceName = resourceName;
        }

        @Override
        public ResourceExpectationSpec inserted(long count) {
            plan.addChangeExpectation(new ResourceChangeExpectation(resourceName, count, null, null));
            return this;
        }

        @Override
        public ResourceExpectationSpec deleted(long count) {
            plan.addChangeExpectation(new ResourceChangeExpectation(resourceName, null, count, null));
            return this;
        }

        @Override
        public ResourceExpectationSpec modified(long count) {
            plan.addChangeExpectation(new ResourceChangeExpectation(resourceName, null, null, count));
            return this;
        }

        @Override
        public ResourceExpectationSpec insertedRow(final RowAssertion assertion) {
            plan.addChangeAssertionExpectation(new ResourceChangeAssertionExpectation(resourceName, new ResourceChangeAssertion() {
                @Override
                public void verify(com.github.sailfishc.flowtest.v2.spec.ResourceChange change) {
                    assertAnyRowMatches("inserted", change.getInsertedRows(), assertion);
                }
            }));
            return this;
        }

        @Override
        public ResourceExpectationSpec deletedRow(final RowAssertion assertion) {
            plan.addChangeAssertionExpectation(new ResourceChangeAssertionExpectation(resourceName, new ResourceChangeAssertion() {
                @Override
                public void verify(com.github.sailfishc.flowtest.v2.spec.ResourceChange change) {
                    assertAnyRowMatches("deleted", change.getDeletedRows(), assertion);
                }
            }));
            return this;
        }

        @Override
        public ResourceExpectationSpec modifiedRow(final ModifiedRowAssertion assertion) {
            plan.addChangeAssertionExpectation(new ResourceChangeAssertionExpectation(resourceName, new ResourceChangeAssertion() {
                @Override
                public void verify(com.github.sailfishc.flowtest.v2.spec.ResourceChange change) {
                    assertAnyModifiedRowMatches(change, assertion);
                }
            }));
            return this;
        }

        @Override
        public ResourceExpectationSpec insertedRows(Consumer<RowListSpec> spec) {
            plan.addChangeAssertionExpectation(
                new ResourceChangeAssertionExpectation(resourceName, RowListAssertions.insertedRows(spec)));
            return this;
        }

        @Override
        public ResourceExpectationSpec deletedRows(Consumer<RowListSpec> spec) {
            plan.addChangeAssertionExpectation(
                new ResourceChangeAssertionExpectation(resourceName, RowListAssertions.deletedRows(spec)));
            return this;
        }

        @Override
        public ResourceExpectationSpec modifiedRows(Consumer<ModifiedRowListSpec> spec) {
            plan.addChangeAssertionExpectation(
                new ResourceChangeAssertionExpectation(resourceName, RowListAssertions.modifiedRows(spec)));
            return this;
        }

        @Override
        public ResourceExpectationSpec satisfies(ResourceChangeAssertion assertion) {
            plan.addChangeAssertionExpectation(new ResourceChangeAssertionExpectation(resourceName, assertion));
            return this;
        }

        @Override
        public ResourceExpectationSpec inspect(final ResourceInspection inspection) {
            plan.addVerification(new ScenarioVerification() {
                @Override
                public void verify(VerifyContext context) throws Exception {
                    inspection.inspect(context.resource(resourceName));
                }
            });
            return this;
        }

        private void assertAnyRowMatches(String rowType,
                                         java.util.List<com.github.sailfishc.flowtest.v2.spec.RowSnapshot> rows,
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
    }

    // ========== FixtureExpectationSpec ==========

    private static final class DefaultFixtureExpectationSpec<R, T> implements FixtureExpectationSpec<T> {

        private final DefaultScenarioPlan<R> plan;
        private final FixtureHandle<T> handle;

        DefaultFixtureExpectationSpec(DefaultScenarioPlan<R> plan, FixtureHandle<T> handle) {
            this.plan = plan;
            this.handle = handle;
        }

        @Override
        public FixtureExpectationSpec<T> before(final FixtureAssertion<T> assertion) {
            // before uses resolve (the materialized entity before action)
            plan.addFixtureExpectation(new FixtureStateExpectation<T>(handle, new FixtureAssertion<T>() {
                @Override
                public void verify(T value) {
                    // Note: FixtureStateExpectation reloads; for "before" we need a different path.
                    // We'll use FixtureChangeExpectation to get access to both before and after.
                    // This is handled by converting to a change expectation.
                }
            }));
            // Actually, let's use fixtureChange to get before access
            plan.fixtureExpectations.remove(plan.fixtureExpectations.size() - 1);
            plan.addFixtureChangeExpectation(new FixtureChangeExpectation<T>(handle,
                new FixtureChangeAssertion<T>() {
                    @Override
                    public void verify(T before, T after) {
                        assertion.verify(before);
                    }
                }));
            return this;
        }

        @Override
        public FixtureExpectationSpec<T> after(FixtureAssertion<T> assertion) {
            plan.addFixtureExpectation(new FixtureStateExpectation<T>(handle, assertion));
            return this;
        }

        @Override
        public FixtureExpectationSpec<T> change(FixtureChangeAssertion<T> assertion) {
            plan.addFixtureChangeExpectation(new FixtureChangeExpectation<T>(handle, assertion));
            return this;
        }

        @Override
        public FixtureExpectationSpec<T> afterMatches(final FixtureStatePatch<T> patch) {
            plan.addVerification(new ScenarioVerification() {
                @Override
                public void verify(VerifyContext context) throws Exception {
                    ((FixtureVerifyContext<T>) context.fixture(handle)).matchesAfter(patch);
                }
            });
            return this;
        }

        @Override
        public FixtureExpectationSpec<T> inspect(final FixtureInspection<T> inspection) {
            plan.addVerification(new ScenarioVerification() {
                @Override
                public void verify(VerifyContext context) throws Exception {
                    inspection.inspect(context.fixture(handle));
                }
            });
            return this;
        }
    }

    // ========== Utility ==========

    @SuppressWarnings("unchecked")
    private static <T> FixtureTrait<T> builderToTrait(Consumer<FixtureBuilder<T>> builder) {
        return FixtureTrait.draft(builder);
    }
}
