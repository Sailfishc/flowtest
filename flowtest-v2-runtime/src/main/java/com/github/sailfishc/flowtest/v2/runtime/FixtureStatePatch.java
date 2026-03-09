package com.github.sailfishc.flowtest.v2.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Partial after-state declaration for whole-fixture verification.
 */
public final class FixtureStatePatch<T> {

    private final Class<T> entityType;
    private final Map<String, Object> expectedValues = new LinkedHashMap<String, Object>();
    private final Set<String> ignoredProperties = new LinkedHashSet<String>();

    private FixtureStatePatch(Class<T> entityType) {
        if (entityType == null) {
            throw new IllegalArgumentException("entityType must not be null");
        }
        this.entityType = entityType;
    }

    public static <T> FixtureStatePatch<T> of(Class<T> entityType) {
        return new FixtureStatePatch<T>(entityType);
    }

    public FixtureStatePatch<T> set(String propertyName, Object expectedValue) {
        expectedValues.put(requirePropertyName(propertyName), expectedValue);
        return this;
    }

    public <V> FixtureStatePatch<T> set(PropertyRef<T, V> propertyRef, V expectedValue) {
        return set(PropertyRefResolver.resolve(propertyRef), expectedValue);
    }

    public FixtureStatePatch<T> ignore(String... propertyNames) {
        if (propertyNames == null) {
            throw new IllegalArgumentException("propertyNames must not be null");
        }
        for (String propertyName : propertyNames) {
            ignoredProperties.add(requirePropertyName(propertyName));
        }
        return this;
    }

    @SafeVarargs
    public final FixtureStatePatch<T> ignore(PropertyRef<T, ?>... propertyRefs) {
        if (propertyRefs == null) {
            throw new IllegalArgumentException("propertyRefs must not be null");
        }
        for (PropertyRef<T, ?> propertyRef : propertyRefs) {
            ignoredProperties.add(requirePropertyName(PropertyRefResolver.resolve(propertyRef)));
        }
        return this;
    }

    public Class<T> getEntityType() {
        return entityType;
    }

    public Map<String, Object> getExpectedValues() {
        return Collections.unmodifiableMap(expectedValues);
    }

    public Set<String> getIgnoredProperties() {
        return Collections.unmodifiableSet(ignoredProperties);
    }

    private static String requirePropertyName(String propertyName) {
        if (propertyName == null || propertyName.trim().isEmpty()) {
            throw new IllegalArgumentException("propertyName must not be blank");
        }
        return propertyName.trim();
    }
}
