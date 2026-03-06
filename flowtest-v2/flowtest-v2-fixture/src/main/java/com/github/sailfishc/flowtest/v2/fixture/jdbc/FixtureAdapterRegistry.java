package com.github.sailfishc.flowtest.v2.fixture.jdbc;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of JDBC fixture adapters used by the runtime.
 */
public final class FixtureAdapterRegistry {

    private final Map<Class<?>, FixtureEntityAdapter<?>> adapters = new LinkedHashMap<Class<?>, FixtureEntityAdapter<?>>();

    public <T> FixtureAdapterRegistry register(FixtureEntityAdapter<T> adapter) {
        adapters.put(adapter.getEntityType(), adapter);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> FixtureEntityAdapter<T> requireAdapter(Class<T> entityType) {
        FixtureEntityAdapter<?> adapter = adapters.get(entityType);
        if (adapter == null) {
            throw new IllegalArgumentException("No fixture adapter registered for " + entityType.getName());
        }
        return (FixtureEntityAdapter<T>) adapter;
    }

    public boolean hasAdapter(Class<?> entityType) {
        return adapters.containsKey(entityType);
    }

    public FixtureAdapterRegistry registerAll(FixtureAdapterRegistry source) {
        adapters.putAll(source.adapters);
        return this;
    }
}
