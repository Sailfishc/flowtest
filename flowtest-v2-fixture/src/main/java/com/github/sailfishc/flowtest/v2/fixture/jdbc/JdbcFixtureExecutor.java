package com.github.sailfishc.flowtest.v2.fixture.jdbc;

import com.github.sailfishc.flowtest.v2.fixture.DataFiller;
import com.github.sailfishc.flowtest.v2.fixture.FixtureExecution;
import com.github.sailfishc.flowtest.v2.fixture.FixtureExecutor;
import com.github.sailfishc.flowtest.v2.fixture.FixtureMaterializer;
import com.github.sailfishc.flowtest.v2.fixture.FixtureStateMetadata;
import com.github.sailfishc.flowtest.v2.observe.rdbms.FlowTestDataSourceRegistry;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcEntityRegistration;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationRegistry;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureSpec;

import javax.sql.DataSource;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JDBC-backed fixture executor with pluggable entity adapters.
 *
 * <p>Supports automatic entity registration and lazy adapter creation:
 * when a fixture entity class is not yet registered in {@link JdbcObservationRegistry},
 * it will be auto-registered via metadata introspection, and a generic JDBC adapter
 * will be created on-demand.</p>
 */
public final class JdbcFixtureExecutor implements FixtureExecutor {

    private final DataSource dataSource;
    private final FlowTestDataSourceRegistry dataSourceRegistry;
    private final FixtureAdapterRegistry adapterRegistry;
    private final FixtureMaterializer materializer;
    private final JdbcObservationRegistry observationRegistry;

    public JdbcFixtureExecutor(DataSource dataSource, JdbcObservationRegistry observationRegistry) {
        this(dataSource, null, JdbcFixtureAdapters.fromObservationRegistry(observationRegistry),
             new FixtureMaterializer(), observationRegistry);
    }

    public JdbcFixtureExecutor(FlowTestDataSourceRegistry dataSourceRegistry, JdbcObservationRegistry observationRegistry) {
        this(null, dataSourceRegistry, JdbcFixtureAdapters.fromObservationRegistry(observationRegistry),
             new FixtureMaterializer(), observationRegistry);
    }

    public JdbcFixtureExecutor(DataSource dataSource, FixtureAdapterRegistry adapterRegistry) {
        this(dataSource, adapterRegistry, new FixtureMaterializer());
    }

    public JdbcFixtureExecutor(FlowTestDataSourceRegistry dataSourceRegistry,
                               FixtureAdapterRegistry adapterRegistry,
                               JdbcObservationRegistry observationRegistry) {
        this(null, dataSourceRegistry, JdbcFixtureAdapters.merge(adapterRegistry, observationRegistry), new FixtureMaterializer(), observationRegistry);
    }

    public JdbcFixtureExecutor(DataSource dataSource,
                               FixtureAdapterRegistry adapterRegistry,
                               JdbcObservationRegistry observationRegistry) {
        this(dataSource, null, JdbcFixtureAdapters.merge(adapterRegistry, observationRegistry), new FixtureMaterializer(), observationRegistry);
    }

    public JdbcFixtureExecutor(DataSource dataSource,
                               FixtureAdapterRegistry adapterRegistry,
                               FixtureMaterializer materializer) {
        this(dataSource, null, adapterRegistry, materializer, null);
    }

    public JdbcFixtureExecutor(DataSource dataSource,
                               FixtureAdapterRegistry adapterRegistry,
                               FixtureMaterializer materializer,
                               JdbcObservationRegistry observationRegistry) {
        this(dataSource, null, adapterRegistry, materializer, observationRegistry);
    }

    public JdbcFixtureExecutor(FlowTestDataSourceRegistry dataSourceRegistry,
                               FixtureAdapterRegistry adapterRegistry,
                               FixtureMaterializer materializer,
                               JdbcObservationRegistry observationRegistry) {
        this(null, dataSourceRegistry, JdbcFixtureAdapters.merge(adapterRegistry, observationRegistry), materializer, observationRegistry);
    }

    private JdbcFixtureExecutor(DataSource dataSource,
                                FlowTestDataSourceRegistry dataSourceRegistry,
                                FixtureAdapterRegistry adapterRegistry,
                                FixtureMaterializer materializer,
                                JdbcObservationRegistry observationRegistry) {
        this.dataSource = dataSource;
        this.dataSourceRegistry = dataSourceRegistry;
        this.adapterRegistry = adapterRegistry;
        this.materializer = materializer;
        this.observationRegistry = observationRegistry;
    }

    @Override
    public FixtureExecution prepare(List<FixtureSpec<?>> fixtures) throws Exception {
        // Auto-register fixture entity types that are not yet registered
        autoRegisterFixtureEntities(fixtures);

        Map<FixtureHandle<?>, Object> resolved = materializer.materialize(fixtures);
        for (FixtureSpec<?> fixture : fixtures) {
            insertFixture(fixture, resolved.get(fixture.getHandle()));
        }
        return new JdbcFixtureExecution(dataSource, dataSourceRegistry, adapterRegistry, observationRegistry, fixtures, resolved);
    }

    /**
     * Auto-registers fixture entity types in the observation registry and creates
     * generic adapters for any entity types that don't already have a custom adapter.
     * Custom adapters are always authoritative — auto-registration only applies when
     * no adapter exists yet.
     */
    private void autoRegisterFixtureEntities(List<FixtureSpec<?>> fixtures) {
        if (observationRegistry == null) {
            return;
        }
        for (FixtureSpec<?> fixture : fixtures) {
            Class<?> entityType = fixture.getEntityType();
            if (!adapterRegistry.hasAdapter(entityType)) {
                JdbcEntityRegistration registration = observationRegistry.registerEntityIfAbsent(entityType);
                adapterRegistry.register(GenericJdbcFixtureEntityAdapter.of(registration));
            }
        }
    }

    private <T> void insertFixture(FixtureSpec<T> fixture, Object value) throws Exception {
        T entity = fixture.getEntityType().cast(value);
        FixtureEntityAdapter<T> adapter = adapterRegistry.requireAdapter(fixture.getEntityType());
        Connection connection = null;
        try {
            connection = resolveDataSource(fixture.getEntityType(), entity).getConnection();
            adapter.insert(connection, entity);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private static final class JdbcFixtureExecution implements FixtureExecution {

        private final DataSource dataSource;
        private final FlowTestDataSourceRegistry dataSourceRegistry;
        private final FixtureAdapterRegistry adapterRegistry;
        private final JdbcObservationRegistry observationRegistry;
        private final List<FixtureSpec<?>> fixtures;
        private final Map<FixtureHandle<?>, Object> resolved;
        private boolean cleaned;

        private JdbcFixtureExecution(DataSource dataSource,
                                     FlowTestDataSourceRegistry dataSourceRegistry,
                                     FixtureAdapterRegistry adapterRegistry,
                                     JdbcObservationRegistry observationRegistry,
                                     List<FixtureSpec<?>> fixtures,
                                     Map<FixtureHandle<?>, Object> resolved) {
            this.dataSource = dataSource;
            this.dataSourceRegistry = dataSourceRegistry;
            this.adapterRegistry = adapterRegistry;
            this.observationRegistry = observationRegistry;
            this.fixtures = new ArrayList<FixtureSpec<?>>(fixtures);
            this.resolved = new LinkedHashMap<FixtureHandle<?>, Object>(resolved);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T resolve(FixtureHandle<T> handle) {
            Object value = resolved.get(handle);
            if (value == null) {
                throw new IllegalArgumentException("Unknown fixture handle " + handle.identifier());
            }
            return (T) value;
        }

        @Override
        public <T> T reload(FixtureHandle<T> handle) throws Exception {
            FixtureEntityAdapter<T> adapter = adapterRegistry.requireAdapter(handle.getType());
            T current = resolve(handle);
            Connection connection = null;
            try {
                connection = resolveDataSource(handle.getType(), current).getConnection();
                T reloaded = adapter.reload(connection, current);
                resolved.put(handle, reloaded);
                return reloaded;
            } finally {
                if (connection != null) {
                    connection.close();
                }
            }
        }

        @Override
        public <T> FixtureStateMetadata describe(FixtureHandle<T> handle) {
            if (observationRegistry == null) {
                return FixtureExecution.super.describe(handle);
            }
            JdbcEntityRegistration registration = observationRegistry.getEntityRegistrations().get(handle.getType());
            if (registration == null) {
                return FixtureExecution.super.describe(handle);
            }
            return FixtureStateMetadata.of(handle.getType(),
                comparableProperties(handle.getType(), registration.getIgnoredProperties()));
        }

        @Override
        public void cleanup() throws Exception {
            if (cleaned) {
                return;
            }
            for (int i = fixtures.size() - 1; i >= 0; i--) {
                FixtureSpec<?> fixture = fixtures.get(i);
                deleteFixture(fixture);
            }
            cleaned = true;
        }

        private <T> void deleteFixture(FixtureSpec<T> fixture) throws Exception {
            FixtureEntityAdapter<T> adapter = adapterRegistry.requireAdapter(fixture.getEntityType());
            T entity = fixture.getEntityType().cast(resolved.get(fixture.getHandle()));
            Connection connection = null;
            try {
                connection = resolveDataSource(fixture.getEntityType(), entity).getConnection();
                adapter.delete(connection, entity);
            } finally {
                if (connection != null) {
                    connection.close();
                }
            }
        }

        private Set<String> comparableProperties(Class<?> entityType, Set<String> ignoredProperties) {
            try {
                BeanInfo beanInfo = Introspector.getBeanInfo(entityType, Object.class);
                Set<String> properties = new LinkedHashSet<String>();
                for (PropertyDescriptor descriptor : beanInfo.getPropertyDescriptors()) {
                    if (descriptor.getReadMethod() == null || descriptor.getWriteMethod() == null) {
                        continue;
                    }
                    if (ignoredProperties.contains(descriptor.getName())) {
                        continue;
                    }
                    properties.add(descriptor.getName());
                }
                return properties;
            } catch (IntrospectionException ex) {
                throw new IllegalArgumentException("Failed to inspect bean properties for " + entityType.getName(), ex);
            }
        }

        private DataSource resolveDataSource(Class<?> entityType, Object entity) {
            if (dataSourceRegistry == null) {
                return dataSource;
            }
            JdbcEntityRegistration registration = observationRegistry.getEntityRegistrations().get(entityType);
            if (registration == null) {
                throw new IllegalArgumentException("No JDBC entity registration found for " + entityType.getName());
            }
            return dataSourceRegistry.requireDataSource(registration.resolveTableName(entity));
        }
    }

    private DataSource resolveDataSource(Class<?> entityType, Object entity) {
        if (dataSourceRegistry == null) {
            return dataSource;
        }
        if (observationRegistry == null) {
            throw new IllegalStateException("Observation registry is required for multi-data-source fixture execution");
        }
        JdbcEntityRegistration registration = observationRegistry.getEntityRegistrations().get(entityType);
        if (registration == null) {
            throw new IllegalArgumentException("No JDBC entity registration found for " + entityType.getName());
        }
        return dataSourceRegistry.requireDataSource(registration.resolveTableName(entity));
    }
}
