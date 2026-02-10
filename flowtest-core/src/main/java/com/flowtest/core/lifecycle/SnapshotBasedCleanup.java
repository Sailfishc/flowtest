package com.flowtest.core.lifecycle;

import com.flowtest.core.TestContext;
import com.flowtest.core.persistence.EntityPersister;
import com.flowtest.core.snapshot.SnapshotEngine;
import com.flowtest.core.snapshot.TableSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Cleanup strategy that uses database snapshots to detect and delete all test data.
 * This includes both data created during persist() phase and data produced by
 * business logic during act() phase.
 *
 * <p>Algorithm (row-data-based):
 * <ol>
 *   <li>beforeTest: Take full row-data snapshots for all tables</li>
 *   <li>afterTest: Take after snapshots, compare primary key sets to find new rows</li>
 *   <li>afterTest: Delete new rows by primary key (supports any PK type)</li>
 *   <li>afterTest: Delete rows recorded in persistedIds (persist-produced data)</li>
 * </ol>
 *
 * <p>This approach supports any primary key type (numeric, UUID, string) and does not
 * require monotonically increasing IDs.
 */
public class SnapshotBasedCleanup implements CleanupStrategy {

    private static final Logger log = LoggerFactory.getLogger(SnapshotBasedCleanup.class);

    private final SnapshotEngine snapshotEngine;
    private final EntityPersister persister;

    public SnapshotBasedCleanup(SnapshotEngine snapshotEngine, EntityPersister persister) {
        this.snapshotEngine = snapshotEngine;
        this.persister = persister;
    }

    @Override
    public void beforeTest(TestContext context) {
        // Take full row-data snapshots for all tables as cleanup baseline
        Set<String> tables = snapshotEngine.listTableNames();
        Map<String, TableSnapshot> beforeSnapshots = snapshotEngine.takeBeforeSnapshot(tables);
        context.setCleanupBeforeSnapshot(beforeSnapshots);
        log.debug("Recorded cleanup before snapshot for {} tables", beforeSnapshots.size());
    }

    @Override
    public void afterTest(TestContext context) {
        // Step 1: Delete act-produced data (new rows detected by PK comparison)
        deleteActProducedData(context);

        // Step 2: Delete persist-produced data
        deletePersistedEntities(context);
    }

    @Override
    public CleanupMode getMode() {
        return CleanupMode.SNAPSHOT_BASED;
    }

    /**
     * Deletes rows created during act() phase by comparing before/after snapshots.
     * Uses primary key set comparison — works with any PK type.
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
            log.debug("No persisted entities to clean up");
            return;
        }

        // Reverse order to handle foreign key dependencies
        List<Class<?>> classes = new ArrayList<>(persistedIds.keySet());
        Collections.reverse(classes);

        for (Class<?> entityClass : classes) {
            List<Object> ids = persistedIds.get(entityClass);
            if (ids != null && !ids.isEmpty()) {
                try {
                    log.debug("Cleaning up {} persisted entities of type {}",
                        ids.size(), entityClass.getSimpleName());
                    persister.deleteAll(entityClass, ids);
                } catch (Exception e) {
                    log.warn("Failed to clean up persisted entities of type {}: {}",
                        entityClass.getSimpleName(), e.getMessage());
                }
            }
        }
    }
}
