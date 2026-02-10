package com.flowtest.core.lifecycle;

import com.flowtest.core.TestContext;
import com.flowtest.core.persistence.EntityPersister;
import com.flowtest.core.snapshot.SnapshotEngine;
import com.flowtest.core.snapshot.TableSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Cleanup strategy that physically deletes test data after test execution.
 * This is the L2 cleanup strategy for scenarios where transaction rollback is not possible
 * (e.g., async operations, REQUIRES_NEW transactions).
 *
 * <p>By default, only cleans up persist() phase data (entities tracked by persistedIds).
 * When {@code cleanActData} is enabled and a {@link SnapshotEngine} is provided,
 * it also cleans up act() phase data using row-data-based snapshot comparison.
 */
public class CompensatingCleanup implements CleanupStrategy {

    private static final Logger log = LoggerFactory.getLogger(CompensatingCleanup.class);

    private final EntityPersister persister;
    private final SnapshotEngine snapshotEngine;
    private final boolean cleanActData;

    public CompensatingCleanup(EntityPersister persister) {
        this(persister, null, false);
    }

    public CompensatingCleanup(EntityPersister persister, SnapshotEngine snapshotEngine, boolean cleanActData) {
        this.persister = persister;
        this.snapshotEngine = snapshotEngine;
        this.cleanActData = cleanActData;
    }

    @Override
    public void beforeTest(TestContext context) {
        if (cleanActData && snapshotEngine != null) {
            // Take full row-data snapshots for act data cleanup
            Set<String> tables = snapshotEngine.listTableNames();
            Map<String, TableSnapshot> beforeSnapshots = snapshotEngine.takeBeforeSnapshot(tables);
            context.setCleanupBeforeSnapshot(beforeSnapshots);
            log.debug("Recorded cleanup before snapshot for {} tables (COMPENSATING + cleanActData)",
                beforeSnapshots.size());
        }
    }

    @Override
    public void afterTest(TestContext context) {
        // Step 1: Delete act-produced data if enabled
        if (cleanActData && snapshotEngine != null) {
            deleteActProducedData(context);
        }

        // Step 2: Delete persist-produced data
        deletePersistedEntities(context);
    }

    @Override
    public CleanupMode getMode() {
        return CleanupMode.COMPENSATING;
    }

    /**
     * Deletes rows created during act() phase by comparing before/after snapshots.
     */
    private void deleteActProducedData(TestContext context) {
        Map<String, TableSnapshot> beforeSnapshots = context.getCleanupBeforeSnapshot();
        if (beforeSnapshots == null || beforeSnapshots.isEmpty()) {
            log.debug("No cleanup before snapshot found, skipping act-produced data cleanup");
            return;
        }

        // Take after snapshots for the same tables
        Set<String> tables = beforeSnapshots.keySet();
        Map<String, TableSnapshot> afterSnapshots = snapshotEngine.takeAfterSnapshot(tables);

        // Reverse order to handle foreign key dependencies
        List<String> tableList = new ArrayList<>(tables);
        Collections.reverse(tableList);

        for (String table : tableList) {
            TableSnapshot before = beforeSnapshots.get(table);
            TableSnapshot after = afterSnapshots.get(table);

            if (after == null) {
                continue;
            }

            String idColumn = snapshotEngine.getIdColumnForTable(table);
            List<Object> newKeys = snapshotEngine.findNewPrimaryKeys(before, after);

            if (!newKeys.isEmpty()) {
                int deleted = snapshotEngine.deleteRowsByPrimaryKeys(table, idColumn, newKeys);
                log.debug("Deleted {} act-produced rows from {} (found {} new PKs)",
                    deleted, table, newKeys.size());
            }
        }
    }

    /**
     * Deletes entities recorded during persist() phase.
     */
    private void deletePersistedEntities(TestContext context) {
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
}
