package com.github.sailfishc.flowtest.v2.observe.rdbms;

import com.github.sailfishc.flowtest.v2.spec.ObservationMode;
import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;
import com.github.sailfishc.flowtest.v2.spec.TableRouteScope;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves observed resources to concrete relational tables and identities.
 */
public final class JdbcObservationRegistry {

    private final Map<String, JdbcObservedResource> resourcesByName = new LinkedHashMap<String, JdbcObservedResource>();
    private final Map<Class<?>, JdbcObservedResource> resourcesByType = new LinkedHashMap<Class<?>, JdbcObservedResource>();
    private final Map<Class<?>, JdbcEntityRegistration> entityRegistrations = new LinkedHashMap<Class<?>, JdbcEntityRegistration>();
    private final List<JdbcIgnorePropertyResolver> ignorePropertyResolvers =
        new ArrayList<JdbcIgnorePropertyResolver>(JdbcIgnorePropertyResolvers.defaults());

    public JdbcObservationRegistry addIgnorePropertyResolver(JdbcIgnorePropertyResolver resolver) {
        if (resolver == null) {
            throw new IllegalArgumentException("resolver must not be null");
        }
        ignorePropertyResolvers.add(resolver);
        return this;
    }

    public JdbcObservationRegistry registerTable(String tableName, String... keyColumns) {
        return table(tableName, keyColumns).register();
    }

    public JdbcObservationRegistry registerNamedTable(String resourceName, String tableName, String... keyColumns) {
        return namedTable(resourceName, tableName, keyColumns).register();
    }

    public TableRegistrationBuilder table(String tableName, String... keyColumns) {
        return new TableRegistrationBuilder(this, tableName, tableName, keyColumns);
    }

    public TableRegistrationBuilder namedTable(String resourceName, String tableName, String... keyColumns) {
        return new TableRegistrationBuilder(this, resourceName, tableName, keyColumns);
    }

    public JdbcObservationRegistry registerEntity(Class<?> entityType) {
        registerEntity(introspectedRegistration(entityType));
        return this;
    }

    public JdbcObservationRegistry registerEntity(Class<?> entityType, String tableName, String... keyColumns) {
        registerEntity(new JdbcEntityRegistration(
            entityType,
            resolveResourceName(entityType),
            TableIdentity.of(tableName, keyColumns),
            detectPropertyColumns(entityType),
            detectIgnoredProperties(entityType),
            dynamicTableRuleOf(entityType)
        ));
        return this;
    }

    public <T> EntityRegistrationBuilder<T> entity(Class<T> entityType) {
        JdbcEntityRegistration registration = introspectedRegistration(entityType);
        return new EntityRegistrationBuilder<T>(
            this,
            entityType,
            registration.getResourceName(),
            registration.getIdentity().getTableName(),
            registration.getIdentity().getKeyColumns().toArray(new String[registration.getIdentity().getKeyColumns().size()]),
            registration.getPropertyColumns(),
            registration.getIgnoredProperties(),
            registration.getDynamicTableRule()
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
            detectIgnoredProperties(entityType),
            dynamicTableRuleOf(entityType)
        );
    }

    public Map<Class<?>, JdbcEntityRegistration> getEntityRegistrations() {
        return Collections.unmodifiableMap(entityRegistrations);
    }

    /**
     * Registers the given entity type if it has not been registered yet.
     * Uses automatic metadata introspection (annotations + convention).
     * This is idempotent and thread-safe — calling it for an already-registered type is a no-op.
     *
     * @param entityType the entity class to register
     * @return the registration (existing or newly created)
     */
    public synchronized JdbcEntityRegistration registerEntityIfAbsent(Class<?> entityType) {
        JdbcEntityRegistration existing = entityRegistrations.get(entityType);
        if (existing != null) {
            return existing;
        }
        JdbcEntityRegistration registration = introspectedRegistration(entityType);
        registerEntity(registration);
        return registration;
    }

    private void registerEntity(JdbcEntityRegistration registration) {
        JdbcObservedResource resource = new JdbcObservedResource(
            registration.getResourceName(),
            registration.getIdentity(),
            registration.getDynamicTableRule()
        );
        resourcesByType.put(registration.getEntityType(), resource);
        resourcesByName.put(registration.getResourceName(), resource);
        entityRegistrations.put(registration.getEntityType(), registration);
    }

    private void registerTable(JdbcObservedResource resource) {
        resourcesByName.put(resource.getResourceName(), resource);
    }

    private JdbcEntityRegistration introspectedRegistration(Class<?> entityType) {
        return new JdbcEntityRegistration(
            entityType,
            resolveResourceName(entityType),
            TableIdentity.of(resolveTableName(entityType), resolveKeyColumns(entityType)),
            detectPropertyColumns(entityType),
            detectIgnoredProperties(entityType),
            dynamicTableRuleOf(entityType)
        );
    }

    private DynamicTableRule dynamicTableRuleOf(Class<?> entityType) {
        JdbcDynamicTable dynamicTable = entityType.getAnnotation(JdbcDynamicTable.class);
        if (dynamicTable == null) {
            return null;
        }
        String propertyName = hasText(dynamicTable.property()) ? dynamicTable.property().trim()
            : hasText(dynamicTable.column()) ? dynamicTable.column().trim() : null;
        if (!hasText(propertyName)) {
            throw new IllegalArgumentException("@JdbcDynamicTable on " + entityType.getName() + " requires property()");
        }
        String tableRouteKey = hasText(dynamicTable.key()) ? dynamicTable.key().trim() : propertyName;
        return DynamicTableRule.forProperty(propertyName, tableRouteKey, DynamicTableNameResolvers.suffix(dynamicTable.separator()));
    }

    private String resolveResourceName(Class<?> entityType) {
        JdbcEntity jdbcEntity = entityType.getAnnotation(JdbcEntity.class);
        if (jdbcEntity != null && hasText(jdbcEntity.resourceName())) {
            return jdbcEntity.resourceName().trim();
        }
        return entityType.getName();
    }

    private String resolveTableName(Class<?> entityType) {
        JdbcEntity jdbcEntity = entityType.getAnnotation(JdbcEntity.class);
        if (jdbcEntity != null && hasText(jdbcEntity.table())) {
            return jdbcEntity.table().trim();
        }

        String tableName = annotationValue(entityType, "javax.persistence.Table", "name");
        if (!hasText(tableName)) {
            tableName = annotationValue(entityType, "jakarta.persistence.Table", "name");
        }
        if (!hasText(tableName)) {
            tableName = annotationValue(entityType, "com.baomidou.mybatisplus.annotation.TableName", "value");
        }
        if (hasText(tableName)) {
            return tableName.trim();
        }

        return toSnakeCase(entityType.getSimpleName());
    }

    private String[] resolveKeyColumns(Class<?> entityType) {
        JdbcEntity jdbcEntity = entityType.getAnnotation(JdbcEntity.class);
        if (jdbcEntity != null && jdbcEntity.keyColumns().length > 0) {
            return jdbcEntity.keyColumns();
        }

        List<String> keyColumns = detectAnnotatedKeyColumns(entityType);
        if (!keyColumns.isEmpty()) {
            return keyColumns.toArray(new String[keyColumns.size()]);
        }

        PropertyDescriptor idProperty = beanProperties(entityType).get("id");
        if (idProperty != null) {
            String columnName = detectColumnName(entityType, idProperty);
            return new String[] { hasText(columnName) ? columnName : "id" };
        }

        Field idField = findField(entityType, "id");
        if (idField != null) {
            String columnName = detectFieldColumnName(idField);
            return new String[] { hasText(columnName) ? columnName : "id" };
        }

        throw new IllegalArgumentException("No key column mapping found for " + entityType.getName()
            + ". Add @JdbcEntity(keyColumns=...), @TableId, or an id property.");
    }

    private List<String> detectAnnotatedKeyColumns(Class<?> entityType) {
        List<String> keyColumns = new ArrayList<String>();
        for (PropertyDescriptor property : beanProperties(entityType).values()) {
            Field field = findField(entityType, property.getName());
            if (!hasAnnotation(field, property.getReadMethod(), property.getWriteMethod(), "com.baomidou.mybatisplus.annotation.TableId")
                && !hasAnnotation(field, property.getReadMethod(), property.getWriteMethod(), "javax.persistence.Id")
                && !hasAnnotation(field, property.getReadMethod(), property.getWriteMethod(), "jakarta.persistence.Id")) {
                continue;
            }
            String columnName = detectColumnName(entityType, property);
            keyColumns.add(hasText(columnName) ? columnName : toSnakeCase(property.getName()));
        }
        return keyColumns;
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
            JdbcPropertyAccess access = new JdbcPropertyAccess(entityType, property, findField(entityType, property.getName()));
            if (isIgnored(access)) {
                ignoredProperties.add(property.getName());
            }
        }
        return ignoredProperties;
    }

    private boolean isIgnored(JdbcPropertyAccess propertyAccess) {
        for (JdbcIgnorePropertyResolver resolver : ignorePropertyResolvers) {
            if (resolver.isIgnored(propertyAccess)) {
                return true;
            }
        }
        return false;
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
        String columnName = detectFieldColumnName(field);
        if (!hasText(columnName)) {
            JdbcColumn column = findAnnotation(property.getReadMethod(), JdbcColumn.class);
            if (column == null) {
                column = findAnnotation(property.getWriteMethod(), JdbcColumn.class);
            }
            if (column != null) {
                columnName = requireText(column.value(), "column value must not be blank");
            }
        }
        if (!hasText(columnName)) {
            columnName = annotationValue(property.getReadMethod(), "com.baomidou.mybatisplus.annotation.TableField", "value");
        }
        if (!hasText(columnName)) {
            columnName = annotationValue(property.getWriteMethod(), "com.baomidou.mybatisplus.annotation.TableField", "value");
        }
        if (!hasText(columnName)) {
            columnName = annotationValue(property.getReadMethod(), "com.baomidou.mybatisplus.annotation.TableId", "value");
        }
        if (!hasText(columnName)) {
            columnName = annotationValue(property.getWriteMethod(), "com.baomidou.mybatisplus.annotation.TableId", "value");
        }
        return hasText(columnName) ? requireText(columnName, "column value must not be blank") : null;
    }

    private String detectFieldColumnName(Field field) {
        if (field == null) {
            return null;
        }
        JdbcColumn jdbcColumn = field.getAnnotation(JdbcColumn.class);
        if (jdbcColumn != null) {
            return requireText(jdbcColumn.value(), "column value must not be blank");
        }

        String columnName = annotationValue(field, "com.baomidou.mybatisplus.annotation.TableField", "value");
        if (!hasText(columnName)) {
            columnName = annotationValue(field, "com.baomidou.mybatisplus.annotation.TableId", "value");
        }
        return hasText(columnName) ? requireText(columnName, "column value must not be blank") : null;
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

    private boolean hasAnnotation(Field field, Method readMethod, Method writeMethod, String annotationClassName) {
        return hasAnnotation(field, annotationClassName)
            || hasAnnotation(readMethod, annotationClassName)
            || hasAnnotation(writeMethod, annotationClassName);
    }

    private boolean hasAnnotation(Field field, String annotationClassName) {
        if (field == null) {
            return false;
        }
        return annotationValue(field, annotationClassName, null) != null;
    }

    private boolean hasAnnotation(Method method, String annotationClassName) {
        if (method == null) {
            return false;
        }
        return annotationValue(method, annotationClassName, null) != null;
    }

    private String annotationValue(Class<?> target, String annotationClassName, String attributeName) {
        try {
            Class<?> annotationClass = Class.forName(annotationClassName);
            java.lang.annotation.Annotation annotation =
                target.getAnnotation((Class<? extends java.lang.annotation.Annotation>) annotationClass);
            return extractAnnotationValue(annotation, attributeName);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    private String annotationValue(Field target, String annotationClassName, String attributeName) {
        try {
            Class<?> annotationClass = Class.forName(annotationClassName);
            java.lang.annotation.Annotation annotation =
                target.getAnnotation((Class<? extends java.lang.annotation.Annotation>) annotationClass);
            return extractAnnotationValue(annotation, attributeName);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    private String annotationValue(Method target, String annotationClassName, String attributeName) {
        if (target == null) {
            return null;
        }
        try {
            Class<?> annotationClass = Class.forName(annotationClassName);
            java.lang.annotation.Annotation annotation =
                target.getAnnotation((Class<? extends java.lang.annotation.Annotation>) annotationClass);
            return extractAnnotationValue(annotation, attributeName);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    private String extractAnnotationValue(java.lang.annotation.Annotation annotation, String attributeName) {
        if (annotation == null) {
            return null;
        }
        if (!hasText(attributeName)) {
            return "";
        }
        try {
            Object value = annotation.annotationType().getMethod(attributeName).invoke(annotation);
            return value == null ? null : value.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read annotation attribute " + attributeName
                + " from " + annotation.annotationType().getName(), ex);
        }
    }

    private String toSnakeCase(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    builder.append('_');
                }
                builder.append(Character.toLowerCase(c));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    JdbcObservedResource resolve(ObservationSpec observation) {
        JdbcObservedResource resource = lookup(observation);
        if (!resource.isDynamicTable()) {
            return resource;
        }

        String physicalTableName;
        TableRouteScope tableRouteScope = observation.getTableRouteScope();
        if (tableRouteScope != null && !tableRouteScope.isEmpty()) {
            physicalTableName = resource.getDynamicTableRule().resolveTableName(resource.getIdentity().getTableName(), tableRouteScope);
        } else if (observation.getObservationMode() == ObservationMode.FIXTURE_BACKED) {
            throw new IllegalArgumentException("Dynamic table observation for fixture-backed resource "
                + observation.getResourceName()
                + " requires a table route scope. Either use .dynamicTableBy(...) in the DSL,"
                + " or ensure the fixture entity's dynamic table property is set via traits"
                + " so the framework can auto-derive it.");
        } else {
            physicalTableName = resource.getDynamicTableRule().resolveTableName(resource.getIdentity().getTableName(), observation.getRouteScope());
        }
        return resource.withIdentity(resource.getIdentity().withTableName(physicalTableName));
    }

    private JdbcObservedResource lookup(ObservationSpec observation) {
        if (observation.getResourceType() != null) {
            JdbcObservedResource byType = resourcesByType.get(observation.getResourceType());
            if (byType != null) {
                return byType;
            }
            JdbcEntityRegistration registration = registerEntityIfAbsent(observation.getResourceType());
            JdbcObservedResource autoRegistered = resourcesByType.get(observation.getResourceType());
            if (autoRegistered != null) {
                return autoRegistered;
            }
            throw new IllegalStateException("Failed to register observed entity " + registration.getEntityType().getName());
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
        private final DynamicTableRule dynamicTableRule;

        private JdbcObservedResource(String resourceName, TableIdentity identity, DynamicTableRule dynamicTableRule) {
            this.resourceName = resourceName;
            this.identity = identity;
            this.dynamicTableRule = dynamicTableRule;
        }

        public String getResourceName() {
            return resourceName;
        }

        public TableIdentity getIdentity() {
            return identity;
        }

        public boolean isDynamicTable() {
            return dynamicTableRule != null;
        }

        public DynamicTableRule getDynamicTableRule() {
            return dynamicTableRule;
        }

        public JdbcObservedResource withIdentity(TableIdentity resolvedIdentity) {
            return new JdbcObservedResource(resourceName, resolvedIdentity, dynamicTableRule);
        }
    }

    public static final class TableRegistrationBuilder {

        private final JdbcObservationRegistry registry;
        private final String resourceName;
        private final String tableName;
        private final String[] keyColumns;
        private DynamicTableRule dynamicTableRule;

        private TableRegistrationBuilder(JdbcObservationRegistry registry,
                                         String resourceName,
                                         String tableName,
                                         String[] keyColumns) {
            this.registry = registry;
            this.resourceName = requireText(resourceName, "resourceName must not be blank");
            this.tableName = requireText(tableName, "tableName must not be blank");
            this.keyColumns = keyColumns;
        }

        public TableRegistrationBuilder dynamicByKey(String tableRouteKey) {
            return dynamicByKey(tableRouteKey, DynamicTableNameResolvers.suffix());
        }

        public TableRegistrationBuilder dynamicByKey(String tableRouteKey, DynamicTableNameResolver resolver) {
            this.dynamicTableRule = DynamicTableRule.forRouteKey(tableRouteKey, resolver);
            return this;
        }

        public TableRegistrationBuilder dynamicByColumn(String columnName) {
            return dynamicByKey(columnName);
        }

        public TableRegistrationBuilder dynamicByColumn(String columnName, DynamicTableNameResolver resolver) {
            return dynamicByKey(columnName, resolver);
        }

        public JdbcObservationRegistry register() {
            registry.registerTable(new JdbcObservedResource(
                resourceName,
                TableIdentity.of(tableName, keyColumns),
                dynamicTableRule
            ));
            return registry;
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
        private DynamicTableRule dynamicTableRule;

        private EntityRegistrationBuilder(JdbcObservationRegistry registry,
                                          Class<T> entityType,
                                          String resourceName,
                                          String tableName,
                                          String[] keyColumns,
                                          Map<String, String> propertyColumns,
                                          Set<String> ignoredProperties,
                                          DynamicTableRule dynamicTableRule) {
            this.registry = registry;
            this.entityType = entityType;
            this.resourceName = resourceName;
            this.tableName = tableName;
            this.keyColumns = keyColumns;
            this.propertyColumns.putAll(propertyColumns);
            this.ignoredProperties.addAll(ignoredProperties);
            this.dynamicTableRule = dynamicTableRule;
        }

        public EntityRegistrationBuilder<T> column(String propertyName, String columnName) {
            propertyColumns.put(requireText(propertyName, "propertyName must not be blank"), requireText(columnName, "columnName must not be blank"));
            return this;
        }

        public EntityRegistrationBuilder<T> ignore(String propertyName) {
            ignoredProperties.add(requireText(propertyName, "propertyName must not be blank"));
            return this;
        }

        public EntityRegistrationBuilder<T> dynamicByProperty(String propertyName) {
            return dynamicByProperty(propertyName, propertyName, DynamicTableNameResolvers.suffix());
        }

        public EntityRegistrationBuilder<T> dynamicByProperty(String propertyName, DynamicTableNameResolver resolver) {
            return dynamicByProperty(propertyName, propertyName, resolver);
        }

        public EntityRegistrationBuilder<T> dynamicByProperty(String propertyName,
                                                              String tableRouteKey,
                                                              DynamicTableNameResolver resolver) {
            this.dynamicTableRule = DynamicTableRule.forProperty(propertyName, tableRouteKey, resolver);
            return this;
        }

        public EntityRegistrationBuilder<T> dynamicByColumn(String columnName) {
            return dynamicByProperty(columnName);
        }

        public EntityRegistrationBuilder<T> dynamicByColumn(String columnName, DynamicTableNameResolver resolver) {
            return dynamicByProperty(columnName, resolver);
        }

        public JdbcObservationRegistry register() {
            registry.registerEntity(new JdbcEntityRegistration(
                entityType,
                resourceName,
                TableIdentity.of(tableName, keyColumns),
                propertyColumns,
                ignoredProperties,
                dynamicTableRule
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
