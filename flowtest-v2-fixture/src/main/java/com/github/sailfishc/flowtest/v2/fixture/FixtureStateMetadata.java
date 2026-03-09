package com.github.sailfishc.flowtest.v2.fixture;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Comparable fixture property metadata used by high-level verification helpers.
 */
public final class FixtureStateMetadata {

    private final Class<?> entityType;
    private final Set<String> comparableProperties;

    private FixtureStateMetadata(Class<?> entityType, Collection<String> comparableProperties) {
        this.entityType = Objects.requireNonNull(entityType, "entityType must not be null");
        this.comparableProperties = Collections.unmodifiableSet(new LinkedHashSet<String>(comparableProperties));
    }

    public static FixtureStateMetadata of(Class<?> entityType, Collection<String> comparableProperties) {
        return new FixtureStateMetadata(entityType, comparableProperties);
    }

    public static FixtureStateMetadata introspect(Class<?> entityType) {
        Set<String> comparableProperties = new LinkedHashSet<String>();
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(entityType, Object.class);
            for (PropertyDescriptor descriptor : beanInfo.getPropertyDescriptors()) {
                if (descriptor.getReadMethod() == null || descriptor.getWriteMethod() == null) {
                    continue;
                }
                comparableProperties.add(descriptor.getName());
            }
        } catch (IntrospectionException ex) {
            throw new IllegalArgumentException("Failed to inspect bean properties for " + entityType.getName(), ex);
        }
        return new FixtureStateMetadata(entityType, comparableProperties);
    }

    public Class<?> getEntityType() {
        return entityType;
    }

    public Set<String> getComparableProperties() {
        return comparableProperties;
    }
}
