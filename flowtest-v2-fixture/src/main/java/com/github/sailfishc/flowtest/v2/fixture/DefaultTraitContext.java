package com.github.sailfishc.flowtest.v2.fixture;

import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.TraitContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable trait context used while materializing fixtures.
 */
public final class DefaultTraitContext implements TraitContext {

    private final Map<FixtureHandle<?>, Object> resolvedFixtures = new LinkedHashMap<FixtureHandle<?>, Object>();
    private final Map<String, Object> resolvedFixturesByAlias = new LinkedHashMap<String, Object>();

    public <T> DefaultTraitContext withResolved(FixtureHandle<T> handle, T value) {
        resolvedFixtures.put(handle, value);
        resolvedFixturesByAlias.put(handle.getName(), value);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T resolve(FixtureHandle<T> handle) {
        Object value = resolvedFixtures.get(handle);
        if (value == null) {
            throw new IllegalArgumentException("No resolved fixture found for handle " + handle.identifier());
        }
        return (T) value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T fixture(String alias, Class<T> type) {
        Object value = resolvedFixturesByAlias.get(alias);
        if (value == null) {
            throw new IllegalArgumentException("No resolved fixture found for alias " + alias);
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("Fixture alias " + alias + " does not refer to " + type.getName());
        }
        return (T) value;
    }
}
