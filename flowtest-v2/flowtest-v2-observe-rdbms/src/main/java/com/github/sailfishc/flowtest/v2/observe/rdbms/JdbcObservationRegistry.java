package com.github.sailfishc.flowtest.v2.observe.rdbms;

import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves observed resources to concrete relational tables and identities.
 */
public final class JdbcObservationRegistry {

    private final Map<String, JdbcObservedResource> resourcesByName = new LinkedHashMap<String, JdbcObservedResource>();
    private final Map<Class<?>, JdbcObservedResource> resourcesByType = new LinkedHashMap<Class<?>, JdbcObservedResource>();

    public JdbcObservationRegistry registerTable(String tableName, String... keyColumns) {
        resourcesByName.put(tableName, new JdbcObservedResource(tableName, TableIdentity.of(tableName, keyColumns)));
        return this;
    }

    public JdbcObservationRegistry registerNamedTable(String resourceName, String tableName, String... keyColumns) {
        resourcesByName.put(resourceName, new JdbcObservedResource(resourceName, TableIdentity.of(tableName, keyColumns)));
        return this;
    }

    public JdbcObservationRegistry registerEntity(Class<?> entityType, String tableName, String... keyColumns) {
        JdbcObservedResource resource = new JdbcObservedResource(entityType.getName(), TableIdentity.of(tableName, keyColumns));
        resourcesByType.put(entityType, resource);
        resourcesByName.put(entityType.getName(), resource);
        return this;
    }

    JdbcObservedResource resolve(ObservationSpec observation) {
        if (observation.getResourceType() != null) {
            JdbcObservedResource byType = resourcesByType.get(observation.getResourceType());
            if (byType != null) {
                return byType;
            }
        }
        JdbcObservedResource byName = resourcesByName.get(observation.getResourceName());
        if (byName == null) {
            throw new IllegalArgumentException("No observed resource registered for " + observation.getResourceName());
        }
        return byName;
    }

    static final class JdbcObservedResource {

        private final String resourceName;
        private final TableIdentity identity;

        private JdbcObservedResource(String resourceName, TableIdentity identity) {
            this.resourceName = resourceName;
            this.identity = identity;
        }

        public String getResourceName() {
            return resourceName;
        }

        public TableIdentity getIdentity() {
            return identity;
        }
    }
}
