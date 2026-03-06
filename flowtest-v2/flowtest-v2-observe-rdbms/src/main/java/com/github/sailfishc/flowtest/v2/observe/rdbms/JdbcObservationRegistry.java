package com.github.sailfishc.flowtest.v2.observe.rdbms;

import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolves observed resources to concrete relational tables and identities.
 */
public final class JdbcObservationRegistry {

    private final Map<String, JdbcObservedResource> resourcesByName = new LinkedHashMap<String, JdbcObservedResource>();
    private final Map<Class<?>, JdbcObservedResource> resourcesByType = new LinkedHashMap<Class<?>, JdbcObservedResource>();
    private final Map<Class<?>, JdbcEntityRegistration> entityRegistrations = new LinkedHashMap<Class<?>, JdbcEntityRegistration>();

    public JdbcObservationRegistry registerTable(String tableName, String... keyColumns) {
        resourcesByName.put(tableName, new JdbcObservedResource(tableName, TableIdentity.of(tableName, keyColumns)));
        return this;
    }

    public JdbcObservationRegistry registerNamedTable(String resourceName, String tableName, String... keyColumns) {
        resourcesByName.put(resourceName, new JdbcObservedResource(resourceName, TableIdentity.of(tableName, keyColumns)));
        return this;
    }

    public JdbcObservationRegistry registerEntity(Class<?> entityType) {
        registerEntity(annotatedRegistration(entityType));
        return this;
    }

    public JdbcObservationRegistry registerEntity(Class<?> entityType, String tableName, String... keyColumns) {
        registerEntity(new JdbcEntityRegistration(
            entityType,
            resolveResourceName(entityType),
            TableIdentity.of(tableName, keyColumns),
            detectPropertyColumns(entityType),
            detectIgnoredProperties(entityType)
        ));
        return this;
    }

    public <T> EntityRegistrationBuilder<T> entity(Class<T> entityType) {
        JdbcEntityRegistration registration = annotatedRegistration(entityType);
        return new EntityRegistrationBuilder<T>(
            this,
            entityType,
            registration.getResourceName(),
            registration.getIdentity().getTableName(),
            registration.getIdentity().getKeyColumns().toArray(new String[registration.getIdentity().getKeyColumns().size()]),
            registration.getPropertyColumns(),
            registration.getIgnoredProperties()
        );
    }

    public <T> EntityRegistrationBuilder<T> entity(Class<T> entityType, String tableName, String... keyColumns) {
        return new EntityRegistrationBuilder<T>(
            this,
            entityType,
            resolveResourceName(entityType),
            tableName,
            keyColumns,
            detectPropertyColumns(entityType),
            detectIgnoredProperties(entityType)
        );
    }

    public Map<Class<?>, JdbcEntityRegistration> getEntityRegistrations() {
        return Collections.unmodifiableMap(entityRegistrations);
    }

    private void registerEntity(JdbcEntityRegistration registration) {
        JdbcObservedResource resource = new JdbcObservedResource(registration.getResourceName(), registration.getIdentity());
        resourcesByType.put(registration.getEntityType(), resource);
        resourcesByName.put(registration.getResourceName(), resource);
        entityRegistrations.put(registration.getEntityType(), registration);
    }

    private JdbcEntityRegistration annotatedRegistration(Class<?> entityType) {
        JdbcEntity jdbcEntity = entityType.getAnnotation(JdbcEntity.class);
        if (jdbcEntity == null) {
            throw new IllegalArgumentException("No @JdbcEntity mapping found on " + entityType.getName());
        }
        return new JdbcEntityRegistration(
            entityType,
            hasText(jdbcEntity.resourceName()) ? jdbcEntity.resourceName().trim() : entityType.getName(),
            TableIdentity.of(jdbcEntity.table(), jdbcEntity.keyColumns()),
            detectPropertyColumns(entityType),
            detectIgnoredProperties(entityType)
        );
    }

    private String resolveResourceName(Class<?> entityType) {
        JdbcEntity jdbcEntity = entityType.getAnnotation(JdbcEntity.class);
        if (jdbcEntity != null && hasText(jdbcEntity.resourceName())) {
            return jdbcEntity.resourceName().trim();
        }
        return entityType.getName();
    }

    private Map<String, String> detectPropertyColumns(Class<?> entityType) {
        Map<String, String> propertyColumns = new LinkedHashMap<String, String>();
        for (PropertyDescriptor property : beanProperties(entityType).values()) {
            String columnName = detectColumnName(entityType, property);
            if (columnName != null) {
                propertyColumns.put(property.getName(), columnName);
            }
        }
        return propertyColumns;
    }

    private Set<String> detectIgnoredProperties(Class<?> entityType) {
        Set<String> ignoredProperties = new LinkedHashSet<String>();
        for (PropertyDescriptor property : beanProperties(entityType).values()) {
            if (isIgnored(entityType, property)) {
                ignoredProperties.add(property.getName());
            }
        }
        return ignoredProperties;
    }

    private Map<String, PropertyDescriptor> beanProperties(Class<?> entityType) {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(entityType, Object.class);
            Map<String, PropertyDescriptor> descriptors = new LinkedHashMap<String, PropertyDescriptor>();
            for (PropertyDescriptor descriptor : beanInfo.getPropertyDescriptors()) {
                if (descriptor.getReadMethod() == null || descriptor.getWriteMethod() == null) {
                    continue;
                }
                descriptors.put(descriptor.getName(), descriptor);
            }
            return descriptors;
        } catch (IntrospectionException ex) {
            throw new IllegalArgumentException("Failed to inspect bean properties for " + entityType.getName(), ex);
        }
    }

    private String detectColumnName(Class<?> entityType, PropertyDescriptor property) {
        Field field = findField(entityType, property.getName());
        JdbcColumn column = field == null ? null : field.getAnnotation(JdbcColumn.class);
        if (column == null) {
            column = findAnnotation(property.getReadMethod(), JdbcColumn.class);
        }
        if (column == null) {
            column = findAnnotation(property.getWriteMethod(), JdbcColumn.class);
        }
        return column == null ? null : requireText(column.value(), "column value must not be blank");
    }

    private boolean isIgnored(Class<?> entityType, PropertyDescriptor property) {
        Field field = findField(entityType, property.getName());
        if (field != null && field.isAnnotationPresent(JdbcIgnore.class)) {
            return true;
        }
        return isAnnotationPresent(property.getReadMethod(), JdbcIgnore.class)
            || isAnnotationPresent(property.getWriteMethod(), JdbcIgnore.class);
    }

    private Field findField(Class<?> type, String propertyName) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(propertyName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ex) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private <A extends java.lang.annotation.Annotation> A findAnnotation(Method method, Class<A> annotationType) {
        if (method == null) {
            return null;
        }
        return method.getAnnotation(annotationType);
    }

    private boolean isAnnotationPresent(Method method, Class<? extends java.lang.annotation.Annotation> annotationType) {
        return method != null && method.isAnnotationPresent(annotationType);
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

    public static final class EntityRegistrationBuilder<T> {

        private final JdbcObservationRegistry registry;
        private final Class<T> entityType;
        private final String resourceName;
        private final String tableName;
        private final String[] keyColumns;
        private final Map<String, String> propertyColumns = new LinkedHashMap<String, String>();
        private final Set<String> ignoredProperties = new LinkedHashSet<String>();

        private EntityRegistrationBuilder(JdbcObservationRegistry registry,
                                          Class<T> entityType,
                                          String resourceName,
                                          String tableName,
                                          String[] keyColumns,
                                          Map<String, String> propertyColumns,
                                          Set<String> ignoredProperties) {
            this.registry = registry;
            this.entityType = entityType;
            this.resourceName = resourceName;
            this.tableName = tableName;
            this.keyColumns = keyColumns;
            this.propertyColumns.putAll(propertyColumns);
            this.ignoredProperties.addAll(ignoredProperties);
        }

        public EntityRegistrationBuilder<T> column(String propertyName, String columnName) {
            propertyColumns.put(requireText(propertyName, "propertyName must not be blank"), requireText(columnName, "columnName must not be blank"));
            return this;
        }

        public EntityRegistrationBuilder<T> ignore(String propertyName) {
            ignoredProperties.add(requireText(propertyName, "propertyName must not be blank"));
            return this;
        }

        public JdbcObservationRegistry register() {
            registry.registerEntity(new JdbcEntityRegistration(
                entityType,
                resourceName,
                TableIdentity.of(tableName, keyColumns),
                propertyColumns,
                ignoredProperties
            ));
            return registry;
        }
    }

    private static String requireText(String text, String message) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }

    private static boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }
}
