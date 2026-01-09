package com.flowtest.core.lifecycle;

import com.flowtest.core.TestContext;
import com.flowtest.core.persistence.EntityPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Cleanup strategy that physically deletes test data after test execution.
 * This is the L2 cleanup strategy for scenarios where transaction rollback is not possible
 * (e.g., async operations, REQUIRES_NEW transactions).
 */
public class CompensatingCleanup implements CleanupStrategy {

    private static final Logger log = LoggerFactory.getLogger(CompensatingCleanup.class);

    private final EntityPersister persister;

    public CompensatingCleanup(EntityPersister persister) {
        this.persister = persister;
    }

    @Override
    public void beforeTest(TestContext context) {
        // No special setup needed for compensating cleanup
    }

    @Override
    public void afterTest(TestContext context) {
        Map<Class<?>, List<Object>> persistedIds = context.getPersistedIds();

        if (persistedIds.isEmpty()) {
            log.debug("No entities to clean up");
            return;
        }

        // Reverse the order to handle potential foreign key dependencies
        List<Class<?>> classes = new ArrayList<>(persistedIds.keySet());
        Collections.reverse(classes);

        for (Class<?> entityClass : classes) {
            List<Object> ids = persistedIds.get(entityClass);
            if (ids != null && !ids.isEmpty()) {
                try {
                    log.debug("Cleaning up {} entities of type {}", ids.size(), entityClass.getSimpleName());
                    persister.deleteAll(entityClass, ids);
                } catch (Exception e) {
                    log.warn("Failed to clean up entities of type {}: {}",
                        entityClass.getSimpleName(), e.getMessage());
                }
            }
        }
    }

    @Override
    public CleanupMode getMode() {
        return CleanupMode.COMPENSATING;
    }
}
