package com.github.sailfishc.flowtest.v2.observe.rdbms;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Entity-level relational metadata shared by observation and fixture support.
 */
public final class JdbcEntityRegistration {

    private final Class<?> entityType;
    private final String resourceName;
    private final TableIdentity identity;
    private final Map<String, String> propertyColumns;
    private final Set<String> ignoredProperties;

    JdbcEntityRegistration(Class<?> entityType,
                           String resourceName,
                           TableIdentity identity,
                           Map<String, String> propertyColumns,
                           Set<String> ignoredProperties) {
        this.entityType = entityType;
        this.resourceName = resourceName;
        this.identity = identity;
        this.propertyColumns = Collections.unmodifiableMap(new LinkedHashMap<String, String>(propertyColumns));
        this.ignoredProperties = Collections.unmodifiableSet(new LinkedHashSet<String>(ignoredProperties));
    }

    public Class<?> getEntityType() {
        return entityType;
    }

    public String getResourceName() {
        return resourceName;
    }

    public TableIdentity getIdentity() {
        return identity;
    }

    public Map<String, String> getPropertyColumns() {
        return propertyColumns;
    }

    public Set<String> getIgnoredProperties() {
        return ignoredProperties;
    }
}
