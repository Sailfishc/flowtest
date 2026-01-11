package com.flowtest.core.fixture;

import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.jeasy.random.FieldPredicates;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Predicate;

/**
 * Auto-fills entity fields using EasyRandom.
 * Excludes ID fields by default to allow database auto-generation.
 */
public class AutoFiller implements DataFiller {

    private final EasyRandom easyRandom;

    /**
     * Creates an AutoFiller with default settings.
     */
    public AutoFiller() {
        this(defaultParameters());
    }

    /**
     * Creates an AutoFiller with custom parameters.
     */
    public AutoFiller(EasyRandomParameters params) {
        this.easyRandom = new EasyRandom(params);
    }

    /**
     * Creates default EasyRandom parameters.
     */
    public static EasyRandomParameters defaultParameters() {
        return new EasyRandomParameters()
            .seed(System.currentTimeMillis())
            .objectPoolSize(100)
            .randomizationDepth(3)
            .stringLengthRange(5, 20)
            .collectionSizeRange(1, 3)
            .scanClasspathForConcreteTypes(true)
            .overrideDefaultInitialization(false)
            // Exclude ID fields
            .excludeField(isIdField())
            // Use reasonable date ranges
            .dateRange(
                LocalDate.now().minusYears(1),
                LocalDate.now().plusYears(1)
            );
    }

    /**
     * Predicate to identify ID fields.
     */
    private static Predicate<Field> isIdField() {
        return field -> {
            // Check field name
            if ("id".equalsIgnoreCase(field.getName())) {
                return true;
            }
            // Check for @Id annotation (javax or jakarta)
            for (java.lang.annotation.Annotation ann : field.getAnnotations()) {
                String annName = ann.annotationType().getName();
                if (annName.equals("javax.persistence.Id") ||
                    annName.equals("jakarta.persistence.Id")) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * Creates and fills a new entity instance.
     *
     * @param entityClass the entity class
     * @param <T> the entity type
     * @return a filled entity instance
     */
    @Override
    public <T> T fill(Class<T> entityClass) {
        return easyRandom.nextObject(entityClass);
    }

    /**
     * Fills null fields of an existing entity.
     *
     * @param entity the entity to fill
     * @param <T> the entity type
     * @return the entity with null fields filled
     */
    @Override
    public <T> T fillNulls(T entity) {
        if (entity == null) {
            return null;
        }

        Class<?> clazz = entity.getClass();
        T template = (T) easyRandom.nextObject(clazz);

        fillNullFieldsRecursive(entity, template, clazz);
        return entity;
    }

    private void fillNullFieldsRecursive(Object target, Object source, Class<?> clazz) {
        if (clazz == null || clazz == Object.class) {
            return;
        }

        for (Field field : clazz.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                continue;
            }

            field.setAccessible(true);

            try {
                Object targetValue = field.get(target);
                if (targetValue == null) {
                    Object sourceValue = field.get(source);
                    field.set(target, sourceValue);
                }
            } catch (IllegalAccessException e) {
                // Skip fields that can't be accessed
            }
        }

        // Process superclass
        fillNullFieldsRecursive(target, source, clazz.getSuperclass());
    }

    /**
     * Gets the underlying EasyRandom instance.
     */
    public EasyRandom getEasyRandom() {
        return easyRandom;
    }

    /**
     * Builder for customizing AutoFiller.
     */
    public static class Builder {
        private EasyRandomParameters params = defaultParameters();

        public Builder seed(long seed) {
            params.seed(seed);
            return this;
        }

        public Builder stringLengthRange(int min, int max) {
            params.stringLengthRange(min, max);
            return this;
        }

        public Builder collectionSizeRange(int min, int max) {
            params.collectionSizeRange(min, max);
            return this;
        }

        public Builder randomizationDepth(int depth) {
            params.randomizationDepth(depth);
            return this;
        }

        public Builder excludeField(Predicate<Field> predicate) {
            params.excludeField(predicate);
            return this;
        }

        public AutoFiller build() {
            return new AutoFiller(params);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
