package com.github.sailfishc.flowtest.v2.fixture;

import org.instancio.Instancio;
import org.instancio.settings.Keys;
import org.instancio.settings.Settings;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Instancio-backed data filler for V2 fixtures.
 * Auto-fills entity fields with random data while excluding:
 * <ul>
 *   <li>ID fields ({@code id}, JPA {@code @Id}, MyBatis-Plus {@code @TableId})</li>
 *   <li>Fields marked with common ignore annotations (JPA/Jakarta/Spring Transient,
 *       {@code @JdbcIgnore}, MyBatis-Plus {@code @TableField(exist = false)})</li>
 *   <li>Fields annotated with {@code @JdbcDynamicTable} routing properties</li>
 * </ul>
 *
 * <p>Compatible with Java 8 (Instancio 3.7.1).</p>
 */
public final class InstancioDataFiller implements DataFiller {

    private final Settings settings;

    /**
     * Creates an InstancioDataFiller with default settings.
     */
    public InstancioDataFiller() {
        this(defaultSettings());
    }

    /**
     * Creates an InstancioDataFiller with custom settings.
     */
    public InstancioDataFiller(Settings settings) {
        this.settings = settings;
    }

    /**
     * Creates default Instancio settings suitable for fixture filling.
     */
    public static Settings defaultSettings() {
        return Settings.create()
            .set(Keys.STRING_MIN_LENGTH, 5)
            .set(Keys.STRING_MAX_LENGTH, 20)
            .set(Keys.COLLECTION_MIN_SIZE, 1)
            .set(Keys.COLLECTION_MAX_SIZE, 3)
            .set(Keys.MAX_DEPTH, 3)
            .set(Keys.JPA_ENABLED, true)
            .lock();
    }

    @Override
    public <T> T fill(Class<T> entityType) {
        T entity = Instancio.of(entityType)
            .withSettings(settings)
            .create();
        clearExcludedFields(entity);
        return entity;
    }

    /**
     * Clears fields that should not be auto-filled: ID fields, ignored fields,
     * and dynamic table routing properties.
     */
    private void clearExcludedFields(Object entity) {
        Class<?> entityClass = entity.getClass();
        String dynamicTableProperty = resolveDynamicTableProperty(entityClass);
        Class<?> clazz = entityClass;
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (shouldClear(field, dynamicTableProperty)) {
                    field.setAccessible(true);
                    try {
                        field.set(entity, null);
                    } catch (Exception ignored) {
                        // skip fields that can't be cleared (e.g., primitives)
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private boolean shouldClear(Field field, String dynamicTableProperty) {
        return isIdField(field) || isIgnoredField(field)
            || (dynamicTableProperty != null && dynamicTableProperty.equals(field.getName()));
    }

    private boolean isIdField(Field field) {
        if ("id".equalsIgnoreCase(field.getName())) {
            return true;
        }
        return hasAnnotation(field, "javax.persistence.Id")
            || hasAnnotation(field, "jakarta.persistence.Id")
            || hasAnnotation(field, "com.baomidou.mybatisplus.annotation.TableId");
    }

    private boolean isIgnoredField(Field field) {
        // @JdbcIgnore
        if (hasAnnotation(field, "com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcIgnore")) {
            return true;
        }
        // JPA/Jakarta/Spring transient markers
        if (hasAnnotation(field, "javax.persistence.Transient")
            || hasAnnotation(field, "jakarta.persistence.Transient")
            || hasAnnotation(field, "org.springframework.data.annotation.Transient")) {
            return true;
        }
        // MyBatis-Plus @TableField(exist = false)
        if (isMybatisPlusNonExistent(field)) {
            return true;
        }
        return false;
    }

    /**
     * Resolves the dynamic table property name from the class-level {@code @JdbcDynamicTable}
     * annotation. Returns null if the entity does not use dynamic tables.
     */
    private String resolveDynamicTableProperty(Class<?> entityType) {
        for (Annotation annotation : entityType.getAnnotations()) {
            if ("com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcDynamicTable".equals(
                    annotation.annotationType().getName())) {
                try {
                    java.lang.reflect.Method propertyMethod = annotation.annotationType().getMethod("property");
                    Object value = propertyMethod.invoke(annotation);
                    if (value instanceof String && !((String) value).isEmpty()) {
                        return (String) value;
                    }
                } catch (Exception ignored) {
                    // annotation not in classpath or method not available
                }
            }
        }
        return null;
    }

    private boolean hasAnnotation(Field field, String annotationClassName) {
        for (Annotation annotation : field.getAnnotations()) {
            if (annotation.annotationType().getName().equals(annotationClassName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMybatisPlusNonExistent(Field field) {
        for (Annotation annotation : field.getAnnotations()) {
            if ("com.baomidou.mybatisplus.annotation.TableField".equals(annotation.annotationType().getName())) {
                try {
                    java.lang.reflect.Method existMethod = annotation.annotationType().getMethod("exist");
                    Object value = existMethod.invoke(annotation);
                    if (Boolean.FALSE.equals(value)) {
                        return true;
                    }
                } catch (Exception ignored) {
                    // annotation not in classpath or method not available
                }
            }
        }
        return false;
    }
}
