package com.github.sailfishc.flowtest.v2.fixture.jdbc;

import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcEntityRegistration;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Convention-based JDBC fixture adapter that derives columns from bean properties.
 */
public final class GenericJdbcFixtureEntityAdapter<T> implements FixtureEntityAdapter<T> {

    private final Class<T> entityType;
    private final String tableName;
    private final JdbcEntityRegistration registration;
    private final List<ColumnBinding> insertBindings;
    private final List<ColumnBinding> selectBindings;
    private final List<ColumnBinding> keyBindings;

    @SuppressWarnings("unchecked")
    public static <T> GenericJdbcFixtureEntityAdapter<T> of(JdbcEntityRegistration registration) {
        return new GenericJdbcFixtureEntityAdapter<T>(
            (Class<T>) registration.getEntityType(),
            registration.getIdentity().getTableName(),
            registration.getIdentity().getKeyColumns(),
            registration.getPropertyColumns(),
            registration.getIgnoredProperties(),
            registration
        );
    }

    public static <T> GenericJdbcFixtureEntityAdapter<T> of(Class<T> entityType,
                                                            String tableName,
                                                            Collection<String> keyColumns,
                                                            Map<String, String> propertyColumns,
                                                            Set<String> ignoredProperties) {
        return new GenericJdbcFixtureEntityAdapter<T>(entityType, tableName, keyColumns, propertyColumns, ignoredProperties, null);
    }

    public GenericJdbcFixtureEntityAdapter(Class<T> entityType,
                                           String tableName,
                                           Collection<String> keyColumns,
                                           Map<String, String> propertyColumns,
                                           Set<String> ignoredProperties) {
        this(entityType, tableName, keyColumns, propertyColumns, ignoredProperties, null);
    }

    private GenericJdbcFixtureEntityAdapter(Class<T> entityType,
                                            String tableName,
                                            Collection<String> keyColumns,
                                            Map<String, String> propertyColumns,
                                            Set<String> ignoredProperties,
                                            JdbcEntityRegistration registration) {
        this.entityType = entityType;
        this.tableName = tableName;
        this.registration = registration;

        Map<String, PropertyDescriptor> propertiesByName = introspect(entityType);
        Map<String, PropertyDescriptor> propertiesByColumn = new LinkedHashMap<String, PropertyDescriptor>();
        List<ColumnBinding> discovered = new ArrayList<ColumnBinding>();
        for (PropertyDescriptor descriptor : propertiesByName.values()) {
            String propertyName = descriptor.getName();
            if (ignoredProperties.contains(propertyName)) {
                continue;
            }
            String columnName = propertyColumns.containsKey(propertyName)
                ? propertyColumns.get(propertyName)
                : toSnakeCase(propertyName);
            ColumnBinding binding = new ColumnBinding(columnName, descriptor);
            discovered.add(binding);
            propertiesByColumn.put(columnName.toLowerCase(), descriptor);
        }

        this.insertBindings = discovered;
        this.selectBindings = discovered;
        this.keyBindings = resolveKeyBindings(keyColumns, propertiesByColumn);
    }

    @Override
    public Class<T> getEntityType() {
        return entityType;
    }

    @Override
    public void insert(Connection connection, T entity) throws Exception {
        PreparedStatement statement = connection.prepareStatement(buildInsertSql(entity));
        try {
            bindColumns(statement, entity, insertBindings);
            statement.executeUpdate();
        } finally {
            statement.close();
        }
    }

    @Override
    public T reload(Connection connection, T entity) throws Exception {
        PreparedStatement statement = connection.prepareStatement(buildReloadSql(entity));
        try {
            bindColumns(statement, entity, keyBindings);
            ResultSet resultSet = statement.executeQuery();
            try {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Fixture row not found for " + entityType.getName());
                }
                T reloaded = instantiate();
                for (ColumnBinding binding : selectBindings) {
                    Object value = resultSet.getObject(binding.columnName);
                    writeValue(reloaded, binding.descriptor, value);
                }
                copyDynamicTableProperty(entity, reloaded);
                return reloaded;
            } finally {
                resultSet.close();
            }
        } finally {
            statement.close();
        }
    }

    @Override
    public void delete(Connection connection, T entity) throws Exception {
        PreparedStatement statement = connection.prepareStatement(buildDeleteSql(entity));
        try {
            bindColumns(statement, entity, keyBindings);
            statement.executeUpdate();
        } finally {
            statement.close();
        }
    }

    private void bindColumns(PreparedStatement statement, T entity, List<ColumnBinding> bindings) throws Exception {
        for (int i = 0; i < bindings.size(); i++) {
            statement.setObject(i + 1, readValue(entity, bindings.get(i).descriptor));
        }
    }

    private Object readValue(T entity, PropertyDescriptor descriptor) throws Exception {
        Method readMethod = descriptor.getReadMethod();
        readMethod.setAccessible(true);
        return readMethod.invoke(entity);
    }

    private void writeValue(T entity, PropertyDescriptor descriptor, Object value) throws Exception {
        Method writeMethod = descriptor.getWriteMethod();
        writeMethod.setAccessible(true);
        writeMethod.invoke(entity, convertValue(descriptor.getPropertyType(), value));
    }

    private T instantiate() throws Exception {
        Constructor<T> constructor = entityType.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private String buildInsertSql(T entity) {
        StringBuilder sql = new StringBuilder();
        sql.append("insert into ").append(resolveTableName(entity)).append('(');
        appendColumnNames(sql, insertBindings);
        sql.append(") values (");
        appendPlaceholders(sql, insertBindings.size());
        sql.append(')');
        return sql.toString();
    }

    private String buildReloadSql(T entity) {
        StringBuilder sql = new StringBuilder();
        sql.append("select ");
        appendColumnNames(sql, selectBindings);
        sql.append(" from ").append(resolveTableName(entity)).append(" where ");
        appendPredicates(sql, keyBindings);
        return sql.toString();
    }

    private String buildDeleteSql(T entity) {
        StringBuilder sql = new StringBuilder();
        sql.append("delete from ").append(resolveTableName(entity)).append(" where ");
        appendPredicates(sql, keyBindings);
        return sql.toString();
    }

    private String resolveTableName(T entity) {
        if (registration == null) {
            return tableName;
        }
        return registration.resolveTableName(entity);
    }

    private void copyDynamicTableProperty(T source, T target) throws Exception {
        if (registration == null || !registration.isDynamicTable()) {
            return;
        }
        String propertyName = registration.getDynamicTablePropertyName();
        if (propertyName == null) {
            return;
        }
        PropertyDescriptor descriptor = introspect(entityType).get(propertyName);
        if (descriptor == null) {
            return;
        }
        writeValue(target, descriptor, readValue(source, descriptor));
    }

    private void appendColumnNames(StringBuilder builder, List<ColumnBinding> bindings) {
        for (int i = 0; i < bindings.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(bindings.get(i).columnName);
        }
    }

    private void appendPlaceholders(StringBuilder builder, int count) {
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append('?');
        }
    }

    private void appendPredicates(StringBuilder builder, List<ColumnBinding> bindings) {
        for (int i = 0; i < bindings.size(); i++) {
            if (i > 0) {
                builder.append(" and ");
            }
            builder.append(bindings.get(i).columnName).append(" = ?");
        }
    }

    private List<ColumnBinding> resolveKeyBindings(Collection<String> keyColumns,
                                                   Map<String, PropertyDescriptor> propertiesByColumn) {
        List<ColumnBinding> bindings = new ArrayList<ColumnBinding>();
        for (String keyColumn : keyColumns) {
            PropertyDescriptor descriptor = propertiesByColumn.get(keyColumn.toLowerCase());
            if (descriptor == null) {
                throw new IllegalArgumentException("No bean property mapped to key column " + keyColumn
                    + " for " + entityType.getName());
            }
            bindings.add(new ColumnBinding(keyColumn, descriptor));
        }
        return bindings;
    }

    private Map<String, PropertyDescriptor> introspect(Class<T> type) {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(type, Object.class);
            Map<String, PropertyDescriptor> descriptors = new LinkedHashMap<String, PropertyDescriptor>();
            for (PropertyDescriptor descriptor : beanInfo.getPropertyDescriptors()) {
                if (descriptor.getReadMethod() == null || descriptor.getWriteMethod() == null) {
                    continue;
                }
                descriptors.put(descriptor.getName(), descriptor);
            }
            return descriptors;
        } catch (IntrospectionException ex) {
            throw new IllegalArgumentException("Failed to inspect bean properties for " + type.getName(), ex);
        }
    }

    private static Object convertValue(Class<?> targetType, Object value) {
        if (value == null) {
            if (!targetType.isPrimitive()) {
                return null;
            }
            if (Boolean.TYPE.equals(targetType)) {
                return Boolean.FALSE;
            }
            if (Character.TYPE.equals(targetType)) {
                return Character.valueOf('\0');
            }
            if (Long.TYPE.equals(targetType)) {
                return Long.valueOf(0L);
            }
            if (Double.TYPE.equals(targetType)) {
                return Double.valueOf(0D);
            }
            if (Float.TYPE.equals(targetType)) {
                return Float.valueOf(0F);
            }
            if (Short.TYPE.equals(targetType)) {
                return Short.valueOf((short) 0);
            }
            if (Byte.TYPE.equals(targetType)) {
                return Byte.valueOf((byte) 0);
            }
            return Integer.valueOf(0);
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (Long.class.equals(targetType) || Long.TYPE.equals(targetType)) {
            return Long.valueOf(((Number) value).longValue());
        }
        if (Integer.class.equals(targetType) || Integer.TYPE.equals(targetType)) {
            return Integer.valueOf(((Number) value).intValue());
        }
        if (Short.class.equals(targetType) || Short.TYPE.equals(targetType)) {
            return Short.valueOf(((Number) value).shortValue());
        }
        if (Byte.class.equals(targetType) || Byte.TYPE.equals(targetType)) {
            return Byte.valueOf(((Number) value).byteValue());
        }
        if (Double.class.equals(targetType) || Double.TYPE.equals(targetType)) {
            return Double.valueOf(((Number) value).doubleValue());
        }
        if (Float.class.equals(targetType) || Float.TYPE.equals(targetType)) {
            return Float.valueOf(((Number) value).floatValue());
        }
        if (Boolean.class.equals(targetType) || Boolean.TYPE.equals(targetType)) {
            return value instanceof Boolean ? value : Boolean.valueOf(String.valueOf(value));
        }
        if (String.class.equals(targetType)) {
            return String.valueOf(value);
        }
        if (BigDecimal.class.equals(targetType)) {
            return value instanceof BigDecimal ? value : new BigDecimal(String.valueOf(value));
        }
        if (BigInteger.class.equals(targetType)) {
            return value instanceof BigInteger ? value : new BigInteger(String.valueOf(value));
        }
        if (targetType.isEnum()) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Enum<?> enumValue = Enum.valueOf((Class<? extends Enum>) targetType, String.valueOf(value));
            return enumValue;
        }
        return value;
    }

    private static String toSnakeCase(String propertyName) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < propertyName.length(); i++) {
            char current = propertyName.charAt(i);
            if (Character.isUpperCase(current) && i > 0) {
                builder.append('_');
            }
            builder.append(Character.toLowerCase(current));
        }
        return builder.toString();
    }

    private static final class ColumnBinding {

        private final String columnName;
        private final PropertyDescriptor descriptor;

        private ColumnBinding(String columnName, PropertyDescriptor descriptor) {
            this.columnName = columnName;
            this.descriptor = descriptor;
        }
    }
}
