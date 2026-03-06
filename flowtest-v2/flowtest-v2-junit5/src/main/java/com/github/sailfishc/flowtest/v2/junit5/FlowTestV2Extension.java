package com.github.sailfishc.flowtest.v2.junit5;

import com.github.sailfishc.flowtest.v2.fixture.FixtureExecutor;
import com.github.sailfishc.flowtest.v2.fixture.jdbc.FixtureAdapterRegistry;
import com.github.sailfishc.flowtest.v2.fixture.jdbc.FixtureEntityAdapter;
import com.github.sailfishc.flowtest.v2.fixture.jdbc.GenericJdbcFixtureEntityAdapter;
import com.github.sailfishc.flowtest.v2.fixture.jdbc.JdbcFixtureExecutor;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcEntityRegistration;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationExecutor;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationRegistry;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutor;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutorProvider;
import com.github.sailfishc.flowtest.v2.spec.ObservationExecutor;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import javax.sql.DataSource;
import java.lang.reflect.Field;

/**
 * JUnit 5 extension that resolves {@link ScenarioExecutor} for test methods.
 */
public final class FlowTestV2Extension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(FlowTestV2Extension.class);
    private static final String EXECUTOR_KEY = "scenarioExecutor";

    private final ScenarioExecutorFactory scenarioExecutorFactory;

    public FlowTestV2Extension() {
        this(null);
    }

    private FlowTestV2Extension(ScenarioExecutorFactory scenarioExecutorFactory) {
        this.scenarioExecutorFactory = scenarioExecutorFactory;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        getOrCreateExecutor(context);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        context.getStore(NAMESPACE).remove(EXECUTOR_KEY);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return ScenarioExecutor.class.equals(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        try {
            return getOrCreateExecutor(extensionContext);
        } catch (Exception ex) {
            throw new ParameterResolutionException("Failed to resolve ScenarioExecutor", ex);
        }
    }

    private ScenarioExecutor getOrCreateExecutor(ExtensionContext context) throws Exception {
        ScenarioExecutor executor = context.getStore(NAMESPACE).get(EXECUTOR_KEY, ScenarioExecutor.class);
        if (executor != null) {
            return executor;
        }
        ScenarioExecutor created = createExecutor(context);
        context.getStore(NAMESPACE).put(EXECUTOR_KEY, created);
        return created;
    }

    private ScenarioExecutor createExecutor(ExtensionContext context) throws Exception {
        if (scenarioExecutorFactory != null) {
            return requireExecutor(scenarioExecutorFactory.create(), "configured builder");
        }
        Object testInstance = context.getRequiredTestInstance();
        if (testInstance instanceof ScenarioExecutorProvider) {
            return requireExecutor(((ScenarioExecutorProvider) testInstance).createScenarioExecutor(),
                ScenarioExecutorProvider.class.getName());
        }
        ScenarioExecutor fromField = findScenarioExecutor(testInstance);
        if (fromField != null) {
            return fromField;
        }
        throw new IllegalStateException("No ScenarioExecutor available. Use @RegisterExtension with FlowTestV2Extension.builder() "
            + "or implement " + ScenarioExecutorProvider.class.getName());
    }

    private ScenarioExecutor findScenarioExecutor(Object instance) throws IllegalAccessException {
        ScenarioExecutor direct = findScenarioExecutorInInstance(instance);
        if (direct != null) {
            return direct;
        }
        Object enclosing = getEnclosingInstance(instance);
        if (enclosing != null) {
            return findScenarioExecutorInInstance(enclosing);
        }
        return null;
    }

    private ScenarioExecutor findScenarioExecutorInInstance(Object instance) throws IllegalAccessException {
        Class<?> type = instance.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!ScenarioExecutor.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(instance);
                if (value != null) {
                    return (ScenarioExecutor) value;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private Object getEnclosingInstance(Object testInstance) {
        Class<?> type = testInstance.getClass();
        if (type.getEnclosingClass() == null) {
            return null;
        }
        try {
            Field field = type.getDeclaredField("this$0");
            field.setAccessible(true);
            return field.get(testInstance);
        } catch (NoSuchFieldException ex) {
            return null;
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Failed to access enclosing instance", ex);
        }
    }

    private ScenarioExecutor requireExecutor(ScenarioExecutor executor, String source) {
        if (executor == null) {
            throw new IllegalStateException("ScenarioExecutor resolved from " + source + " must not be null");
        }
        return executor;
    }

    /**
     * Builder for explicit extension registration.
     */
    public static final class Builder {

        private ScenarioExecutorFactory scenarioExecutorFactory;
        private ScenarioExecutor scenarioExecutor;
        private ObservationExecutor observationExecutor;
        private FixtureExecutor fixtureExecutor;
        private DataSource dataSource;
        private JdbcObservationRegistry observationRegistry;
        private FixtureAdapterRegistry fixtureAdapterRegistry;

        private Builder() {
        }

        public Builder scenarioExecutor(final ScenarioExecutor scenarioExecutor) {
            this.scenarioExecutor = scenarioExecutor;
            return this;
        }

        public Builder scenarioExecutorFactory(ScenarioExecutorFactory scenarioExecutorFactory) {
            this.scenarioExecutorFactory = scenarioExecutorFactory;
            return this;
        }

        public Builder observationExecutor(ObservationExecutor observationExecutor) {
            this.observationExecutor = observationExecutor;
            return this;
        }

        public Builder fixtureExecutor(FixtureExecutor fixtureExecutor) {
            this.fixtureExecutor = fixtureExecutor;
            return this;
        }

        public Builder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public Builder observationRegistry(JdbcObservationRegistry observationRegistry) {
            this.observationRegistry = observationRegistry;
            return this;
        }

        public Builder fixtureAdapterRegistry(FixtureAdapterRegistry fixtureAdapterRegistry) {
            this.fixtureAdapterRegistry = fixtureAdapterRegistry;
            return this;
        }

        public Builder registerObservedTable(String tableName, String... keyColumns) {
            registry().registerTable(tableName, keyColumns);
            return this;
        }

        public Builder registerObservedEntity(Class<?> entityType, String tableName, String... keyColumns) {
            registry().registerEntity(entityType, tableName, keyColumns);
            return this;
        }

        public Builder registerNamedTable(String resourceName, String tableName, String... keyColumns) {
            registry().registerNamedTable(resourceName, tableName, keyColumns);
            return this;
        }

        public <T> Builder registerFixtureAdapter(FixtureEntityAdapter<T> adapter) {
            fixtureAdapters().register(adapter);
            return this;
        }

        public FlowTestV2Extension build() {
            return new FlowTestV2Extension(resolveFactory());
        }

        private ScenarioExecutorFactory resolveFactory() {
            if (scenarioExecutorFactory != null) {
                return scenarioExecutorFactory;
            }
            if (scenarioExecutor != null) {
                return new ScenarioExecutorFactory() {
                    @Override
                    public ScenarioExecutor create() {
                        return scenarioExecutor;
                    }
                };
            }
            if (observationExecutor != null) {
                return new ScenarioExecutorFactory() {
                    @Override
                    public ScenarioExecutor create() {
                        if (fixtureExecutor != null) {
                            return new ScenarioExecutor(fixtureExecutor, observationExecutor);
                        }
                        return new ScenarioExecutor(observationExecutor);
                    }
                };
            }
            if (dataSource == null) {
                throw new IllegalStateException("Builder requires a ScenarioExecutor, ObservationExecutor, or DataSource");
            }
            return new ScenarioExecutorFactory() {
                @Override
                public ScenarioExecutor create() {
                    FixtureExecutor resolvedFixtureExecutor = fixtureExecutor != null
                        ? fixtureExecutor
                        : new JdbcFixtureExecutor(dataSource, effectiveFixtureAdapters());
                    ObservationExecutor resolvedObservationExecutor = new JdbcObservationExecutor(dataSource, registry());
                    return new ScenarioExecutor(resolvedFixtureExecutor, resolvedObservationExecutor);
                }
            };
        }

        private JdbcObservationRegistry registry() {
            if (observationRegistry == null) {
                observationRegistry = new JdbcObservationRegistry();
            }
            return observationRegistry;
        }

        private FixtureAdapterRegistry fixtureAdapters() {
            if (fixtureAdapterRegistry == null) {
                fixtureAdapterRegistry = new FixtureAdapterRegistry();
            }
            return fixtureAdapterRegistry;
        }

        private FixtureAdapterRegistry effectiveFixtureAdapters() {
            FixtureAdapterRegistry effective = new FixtureAdapterRegistry().registerAll(fixtureAdapters());
            for (JdbcEntityRegistration registration : registry().getEntityRegistrations().values()) {
                if (effective.hasAdapter(registration.getEntityType())) {
                    continue;
                }
                effective.register(GenericJdbcFixtureEntityAdapter.of(
                    registration.getEntityType(),
                    registration.getIdentity().getTableName(),
                    registration.getIdentity().getKeyColumns(),
                    registration.getPropertyColumns(),
                    registration.getIgnoredProperties()
                ));
            }
            return effective;
        }
    }

    /**
     * Factory abstraction that may throw when creating executors.
     */
    public interface ScenarioExecutorFactory {

        ScenarioExecutor create() throws Exception;
    }
}
