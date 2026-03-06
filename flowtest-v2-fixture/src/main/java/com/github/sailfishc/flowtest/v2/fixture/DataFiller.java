package com.github.sailfishc.flowtest.v2.fixture;

/**
 * Auto-fills entity fields with random data before traits are applied.
 * Implementations should exclude ID fields and non-persistent fields by default.
 */
public interface DataFiller {

    /**
     * Creates and fills a new entity instance with random data.
     * ID fields and non-persistent fields should be excluded from filling.
     *
     * @param entityType the entity class
     * @param <T> the entity type
     * @return a filled entity instance
     */
    <T> T fill(Class<T> entityType);
}
