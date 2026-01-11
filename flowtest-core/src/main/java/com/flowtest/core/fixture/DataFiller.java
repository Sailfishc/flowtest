package com.flowtest.core.fixture;

/**
 * Interface for auto-filling entity fields with random data.
 * Implementations can use different libraries like EasyRandom or Instancio.
 */
public interface DataFiller {

    /**
     * Creates and fills a new entity instance with random data.
     *
     * @param entityClass the entity class
     * @param <T> the entity type
     * @return a filled entity instance
     */
    <T> T fill(Class<T> entityClass);

    /**
     * Fills null fields of an existing entity with random data.
     *
     * @param entity the entity to fill
     * @param <T> the entity type
     * @return the entity with null fields filled
     */
    <T> T fillNulls(T entity);
}
