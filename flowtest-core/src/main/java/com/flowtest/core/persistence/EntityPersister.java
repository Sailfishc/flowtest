package com.flowtest.core.persistence;

import java.util.List;

/**
 * Interface for persisting entities to database.
 */
public interface EntityPersister {

    /**
     * Persists an entity and returns the generated ID.
     *
     * @param entity the entity to persist
     * @param <T> the entity type
     * @return the generated ID (may be null if no ID is generated)
     */
    <T> Object persist(T entity);

    /**
     * Persists multiple entities.
     *
     * @param entities the entities to persist
     * @param <T> the entity type
     * @return list of generated IDs
     */
    <T> List<Object> persistAll(List<T> entities);

    /**
     * Deletes an entity by ID.
     *
     * @param entityClass the entity class
     * @param id the entity ID
     * @param <T> the entity type
     */
    <T> void delete(Class<T> entityClass, Object id);

    /**
     * Deletes multiple entities by IDs.
     *
     * @param entityClass the entity class
     * @param ids the entity IDs
     * @param <T> the entity type
     */
    <T> void deleteAll(Class<T> entityClass, List<Object> ids);

    /**
     * Deletes all entities of the given type.
     *
     * @param entityClass the entity class
     * @param <T> the entity type
     * @return number of deleted rows
     */
    <T> int deleteAllOfType(Class<T> entityClass);
}
