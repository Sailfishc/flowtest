package com.github.sailfishc.flowtest.v2.observe.rdbms;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
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
    private final DynamicTableRule dynamicTableRule;

    JdbcEntityRegistration(Class<?> entityType,
                           String resourceName,
                           TableIdentity identity,
                           Map<String, String> propertyColumns,
                           Set<String> ignoredProperties,
                           DynamicTableRule dynamicTableRule) {
        this.entityType = entityType;
        this.resourceName = resourceName;
        this.identity = identity;
        this.propertyColumns = Collections.unmodifiableMap(new LinkedHashMap<String, String>(propertyColumns));
        this.ignoredProperties = Collections.unmodifiableSet(new LinkedHashSet<String>(ignoredProperties));
        this.dynamicTableRule = dynamicTableRule;
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

    public boolean isDynamicTable() {
        return dynamicTableRule != null;
    }

    public String getDynamicTablePropertyName() {
        return dynamicTableRule == null ? null : dynamicTableRule.getPropertyName();
    }

    DynamicTableRule getDynamicTableRule() {
        return dynamicTableRule;
    }

    public String resolveTableName(Object entity) {
        if (dynamicTableRule == null) {
            return identity.getTableName();
        }
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null when resolving dynamic table for " + entityType.getName());
        }
        PropertyDescriptor descriptor = resolveDynamicProperty(entityType, dynamicTableRule.getPropertyName());
        if (descriptor == null) {
            throw new IllegalArgumentException("No bean property named "
                + dynamicTableRule.getPropertyName() + " for dynamic table " + entityType.getName());
        }
        try {
            Method readMethod = descriptor.getReadMethod();
            readMethod.setAccessible(true);
            return dynamicTableRule.resolveTableName(identity.getTableName(), readMethod.invoke(entity));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to resolve dynamic table name for " + entityType.getName(), ex);
        }
    }

    private PropertyDescriptor resolveDynamicProperty(Class<?> type, String propertyName) {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(type, Object.class);
            for (PropertyDescriptor descriptor : beanInfo.getPropertyDescriptors()) {
                if (descriptor.getReadMethod() == null || descriptor.getWriteMethod() == null) {
                    continue;
                }
                if (propertyName.equals(descriptor.getName())) {
                    return descriptor;
                }
            }
        } catch (IntrospectionException ex) {
            throw new IllegalStateException("Failed to inspect bean properties for " + type.getName(), ex);
        }
        return null;
    }
}
