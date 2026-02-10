package com.flowtest.core.routing;

import com.flowtest.core.persistence.EntityPersister;

import java.util.List;

/**
 * An {@link EntityPersister} implementation that delegates to the correct
 * per-datasource persister based on entity class routing.
 */
public class RoutingEntityPersister implements EntityPersister {

    private final DataSourceRouter router;

    public RoutingEntityPersister(DataSourceRouter router) {
        this.router = router;
    }

    @Override
    public <T> Object persist(T entity) {
        return router.getPersister(entity.getClass()).persist(entity);
    }

    @Override
    public <T> List<Object> persistAll(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        // All entities in the list are the same type, so route by the first element
        return router.getPersister(entities.get(0).getClass()).persistAll(entities);
    }

    @Override
    public <T> void delete(Class<T> entityClass, Object id) {
        router.getPersister(entityClass).delete(entityClass, id);
    }

    @Override
    public <T> void deleteAll(Class<T> entityClass, List<Object> ids) {
        router.getPersister(entityClass).deleteAll(entityClass, ids);
    }

    @Override
    public <T> int deleteAllOfType(Class<T> entityClass) {
        return router.getPersister(entityClass).deleteAllOfType(entityClass);
    }

    /**
     * Returns the underlying router (for testing/debugging).
     */
    public DataSourceRouter getRouter() {
        return router;
    }
}
