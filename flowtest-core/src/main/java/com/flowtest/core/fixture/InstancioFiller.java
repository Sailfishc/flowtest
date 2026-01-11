package com.flowtest.core.fixture;

import org.instancio.Instancio;
import org.instancio.settings.Keys;
import org.instancio.settings.Settings;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Auto-fills entity fields using Instancio.
 * Excludes ID fields by default to allow database auto-generation.
 */
public class InstancioFiller implements DataFiller {

    private final Settings settings;

    /**
     * Creates an InstancioFiller with default settings.
     */
    public InstancioFiller() {
        this(defaultSettings());
    }

    /**
     * Creates an InstancioFiller with custom settings.
     */
    public InstancioFiller(Settings settings) {
        this.settings = settings;
    }

    /**
     * Creates default Instancio settings.
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
    public <T> T fill(Class<T> entityClass) {
        return Instancio.of(entityClass)
            .withSettings(settings)
            .create();
    }

    @Override
    public <T> T fillNulls(T entity) {
        if (entity == null) {
            return null;
        }

        Class<?> clazz = entity.getClass();
        @SuppressWarnings("unchecked")
        T template = (T) Instancio.of(clazz)
            .withSettings(settings)
            .create();

        fillNullFieldsRecursive(entity, template, clazz);
        return entity;
    }

    private void fillNullFieldsRecursive(Object target, Object source, Class<?> clazz) {
        if (clazz == null || clazz == Object.class) {
            return;
        }

        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (Modifier.isFinal(field.getModifiers())) {
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
     * Gets the underlying settings.
     */
    public Settings getSettings() {
        return settings;
    }

    /**
     * Builder for customizing InstancioFiller.
     */
    public static class Builder {
        private Settings settings = Settings.create();

        public Builder seed(long seed) {
            settings.set(Keys.SEED, seed);
            return this;
        }

        public Builder stringLengthRange(int min, int max) {
            settings.set(Keys.STRING_MIN_LENGTH, min);
            settings.set(Keys.STRING_MAX_LENGTH, max);
            return this;
        }

        public Builder collectionSizeRange(int min, int max) {
            settings.set(Keys.COLLECTION_MIN_SIZE, min);
            settings.set(Keys.COLLECTION_MAX_SIZE, max);
            return this;
        }

        public Builder maxDepth(int depth) {
            settings.set(Keys.MAX_DEPTH, depth);
            return this;
        }

        public Builder jpaEnabled(boolean enabled) {
            settings.set(Keys.JPA_ENABLED, enabled);
            return this;
        }

        public InstancioFiller build() {
            return new InstancioFiller(settings.lock());
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
