package com.flowtest.core.fixture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Parses and caches entity metadata including table name, column mappings, and ID field.
 * Supports JPA annotations (@Table, @Entity, @Column, @Id) but works without them.
 */
public class EntityMetadata {

    private static final Logger log = LoggerFactory.getLogger(EntityMetadata.class);

    private final Class<?> entityClass;
    private final String tableName;
    private final Field idField;
    private final String idColumnName;
    private final Map<String, Field> columnToField;
    private final Map<Field, String> fieldToColumn;

    public EntityMetadata(Class<?> entityClass) {
        this.entityClass = entityClass;
        this.tableName = resolveTableName(entityClass);
        this.idField = resolveIdField(entityClass);
        this.idColumnName = idField != null ? resolveColumnName(idField) : "id";

        Map<String, Field> colToField = new LinkedHashMap<>();
        Map<Field, String> fieldToCol = new LinkedHashMap<>();

        for (Field field : getAllFields(entityClass)) {
            if (isTransient(field)) {
                continue;
            }
            String columnName = resolveColumnName(field);
            field.setAccessible(true);
            colToField.put(columnName, field);
            fieldToCol.put(field, columnName);
        }

        this.columnToField = Collections.unmodifiableMap(colToField);
        this.fieldToColumn = Collections.unmodifiableMap(fieldToCol);
    }

    /**
     * Resolves the table name for the entity.
     * Priority: @Table(name) > @TableName(value) > @Entity(name) > camelCase to snake_case
     */
    private String resolveTableName(Class<?> clazz) {
        // Try JPA @Table annotation
        String tableName = getAnnotationValue(clazz, "javax.persistence.Table", "name");
        if (tableName == null) {
            tableName = getAnnotationValue(clazz, "jakarta.persistence.Table", "name");
        }
        if (tableName != null && !tableName.isEmpty()) {
            return tableName;
        }

        // Try MyBatis-Plus @TableName annotation
        tableName = getAnnotationValue(clazz, "com.baomidou.mybatisplus.annotation.TableName", "value");
        if (tableName != null && !tableName.isEmpty()) {
            return tableName;
        }

        // Try JPA @Entity annotation
        String entityName = getAnnotationValue(clazz, "javax.persistence.Entity", "name");
        if (entityName == null) {
            entityName = getAnnotationValue(clazz, "jakarta.persistence.Entity", "name");
        }
        if (entityName != null && !entityName.isEmpty()) {
            return entityName;
        }

        // Default: convert class name from CamelCase to snake_case
        return camelToSnake(clazz.getSimpleName());
    }

    /**
     * Resolves the ID field for the entity.
     * Looks for @Id or @TableId annotation or field named "id".
     */
    private Field resolveIdField(Class<?> clazz) {
        // Try JPA @Id annotation
        for (Field field : getAllFields(clazz)) {
            if (hasAnnotation(field, "javax.persistence.Id") ||
                hasAnnotation(field, "jakarta.persistence.Id")) {
                field.setAccessible(true);
                return field;
            }
        }

        // Try MyBatis-Plus @TableId annotation
        for (Field field : getAllFields(clazz)) {
            if (hasAnnotation(field, "com.baomidou.mybatisplus.annotation.TableId")) {
                field.setAccessible(true);
                return field;
            }
        }

        // Fallback: look for field named "id"
        for (Field field : getAllFields(clazz)) {
            if ("id".equalsIgnoreCase(field.getName())) {
                field.setAccessible(true);
                return field;
            }
        }

        log.warn("No ID field found for entity class {}", clazz.getName());
        return null;
    }

    /**
     * Resolves the column name for a field.
     * Priority: @Column(name) > @TableField(value) > camelCase to snake_case
     */
    private String resolveColumnName(Field field) {
        // Try JPA @Column annotation
        String columnName = getAnnotationValue(field, "javax.persistence.Column", "name");
        if (columnName == null) {
            columnName = getAnnotationValue(field, "jakarta.persistence.Column", "name");
        }
        if (columnName != null && !columnName.isEmpty()) {
            return columnName;
        }

        // Try MyBatis-Plus @TableField annotation
        columnName = getAnnotationValue(field, "com.baomidou.mybatisplus.annotation.TableField", "value");
        if (columnName != null && !columnName.isEmpty()) {
            return columnName;
        }

        return camelToSnake(field.getName());
    }

    /**
     * Checks if a field should be excluded from persistence.
     */
    private boolean isTransient(Field field) {
        if (Modifier.isStatic(field.getModifiers())) {
            return true;
        }
        if (Modifier.isTransient(field.getModifiers())) {
            return true;
        }
        if (hasAnnotation(field, "javax.persistence.Transient") ||
            hasAnnotation(field, "jakarta.persistence.Transient")) {
            return true;
        }
        // Check MyBatis-Plus @TableField(exist = false)
        String existValue = getAnnotationValue(field, "com.baomidou.mybatisplus.annotation.TableField", "exist");
        if ("false".equals(existValue)) {
            return true;
        }
        return false;
    }

    /**
     * Gets all fields from the class hierarchy.
     */
    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }

    /**
     * Converts camelCase to snake_case.
     */
    private String camelToSnake(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Gets an annotation value using reflection (to avoid compile-time dependency on JPA).
     */
    private String getAnnotationValue(Object target, String annotationClassName, String attributeName) {
        try {
            Class<?> annotationClass = Class.forName(annotationClassName);
            Annotation annotation;
            if (target instanceof Class) {
                annotation = ((Class<?>) target).getAnnotation((Class<? extends Annotation>) annotationClass);
            } else if (target instanceof Field) {
                annotation = ((Field) target).getAnnotation((Class<? extends Annotation>) annotationClass);
            } else {
                return null;
            }

            if (annotation == null) {
                return null;
            }

            Method method = annotationClass.getMethod(attributeName);
            Object value = method.invoke(annotation);
            return value != null ? value.toString() : null;
        } catch (ClassNotFoundException e) {
            // JPA not on classpath, that's fine
            return null;
        } catch (Exception e) {
            log.debug("Failed to get annotation value: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Checks if a field has the given annotation.
     */
    private boolean hasAnnotation(Field field, String annotationClassName) {
        try {
            Class<?> annotationClass = Class.forName(annotationClassName);
            return field.isAnnotationPresent((Class<? extends Annotation>) annotationClass);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // Getters

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public String getTableName() {
        return tableName;
    }

    public Field getIdField() {
        return idField;
    }

    public String getIdColumnName() {
        return idColumnName;
    }

    public Map<String, Field> getColumnToField() {
        return columnToField;
    }

    public Map<Field, String> getFieldToColumn() {
        return fieldToColumn;
    }

    /**
     * Gets the ID value from an entity instance.
     */
    public Object getId(Object entity) {
        if (idField == null) {
            return null;
        }
        try {
            return idField.get(entity);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to get ID from entity", e);
        }
    }

    /**
     * Sets the ID value on an entity instance.
     */
    public void setId(Object entity, Object id) {
        if (idField == null) {
            return;
        }
        try {
            idField.set(entity, convertId(id, idField.getType()));
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set ID on entity", e);
        }
    }

    /**
     * Converts ID value to the appropriate type.
     */
    private Object convertId(Object id, Class<?> targetType) {
        if (id == null) {
            return null;
        }
        if (targetType.isInstance(id)) {
            return id;
        }
        if (id instanceof Number) {
            Number num = (Number) id;
            if (targetType == Long.class || targetType == long.class) {
                return num.longValue();
            } else if (targetType == Integer.class || targetType == int.class) {
                return num.intValue();
            } else if (targetType == Short.class || targetType == short.class) {
                return num.shortValue();
            }
        }
        return id;
    }

    /**
     * Gets a field value from an entity.
     */
    public Object getFieldValue(Object entity, Field field) {
        try {
            return field.get(entity);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to get field value: " + field.getName(), e);
        }
    }

    /**
     * Gets columns that should be inserted (excludes null ID for auto-generation).
     */
    public List<String> getInsertColumns(Object entity) {
        List<String> columns = new ArrayList<>();
        for (Map.Entry<Field, String> entry : fieldToColumn.entrySet()) {
            Field field = entry.getKey();
            String column = entry.getValue();

            // Skip null ID (let database generate it)
            if (field.equals(idField)) {
                Object idValue = getFieldValue(entity, field);
                if (idValue == null) {
                    continue;
                }
            }

            columns.add(column);
        }
        return columns;
    }

    /**
     * Gets values for insert columns.
     */
    public List<Object> getInsertValues(Object entity, List<String> columns) {
        List<Object> values = new ArrayList<>();
        for (String column : columns) {
            Field field = columnToField.get(column);
            values.add(getFieldValue(entity, field));
        }
        return values;
    }
}
