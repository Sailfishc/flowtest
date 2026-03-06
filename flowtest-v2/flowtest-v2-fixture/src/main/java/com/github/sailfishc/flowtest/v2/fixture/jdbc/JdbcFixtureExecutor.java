package com.github.sailfishc.flowtest.v2.fixture.jdbc;

import com.github.sailfishc.flowtest.v2.fixture.FixtureExecution;
import com.github.sailfishc.flowtest.v2.fixture.FixtureExecutor;
import com.github.sailfishc.flowtest.v2.fixture.FixtureMaterializer;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureSpec;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC-backed fixture executor with pluggable entity adapters.
 */
public final class JdbcFixtureExecutor implements FixtureExecutor {

    private final DataSource dataSource;
    private final FixtureAdapterRegistry adapterRegistry;
    private final FixtureMaterializer materializer;

    public JdbcFixtureExecutor(DataSource dataSource, FixtureAdapterRegistry adapterRegistry) {
        this(dataSource, adapterRegistry, new FixtureMaterializer());
    }

    public JdbcFixtureExecutor(DataSource dataSource,
                               FixtureAdapterRegistry adapterRegistry,
                               FixtureMaterializer materializer) {
        this.dataSource = dataSource;
        this.adapterRegistry = adapterRegistry;
        this.materializer = materializer;
    }

    @Override
    public FixtureExecution prepare(List<FixtureSpec<?>> fixtures) throws Exception {
        Map<FixtureHandle<?>, Object> resolved = materializer.materialize(fixtures);
        for (FixtureSpec<?> fixture : fixtures) {
            insertFixture(fixture, resolved.get(fixture.getHandle()));
        }
        return new JdbcFixtureExecution(dataSource, adapterRegistry, fixtures, resolved);
    }

    private <T> void insertFixture(FixtureSpec<T> fixture, Object value) throws Exception {
        T entity = fixture.getEntityType().cast(value);
        FixtureEntityAdapter<T> adapter = adapterRegistry.requireAdapter(fixture.getEntityType());
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            adapter.insert(connection, entity);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private static final class JdbcFixtureExecution implements FixtureExecution {

        private final DataSource dataSource;
        private final FixtureAdapterRegistry adapterRegistry;
        private final List<FixtureSpec<?>> fixtures;
        private final Map<FixtureHandle<?>, Object> resolved;
        private boolean cleaned;

        private JdbcFixtureExecution(DataSource dataSource,
                                     FixtureAdapterRegistry adapterRegistry,
                                     List<FixtureSpec<?>> fixtures,
                                     Map<FixtureHandle<?>, Object> resolved) {
            this.dataSource = dataSource;
            this.adapterRegistry = adapterRegistry;
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
                connection = dataSource.getConnection();
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
                connection = dataSource.getConnection();
                adapter.delete(connection, entity);
            } finally {
                if (connection != null) {
                    connection.close();
                }
            }
        }
    }
}
