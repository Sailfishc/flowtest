package com.flowtest.core.lifecycle;

import com.flowtest.core.TestContext;
import com.flowtest.core.persistence.EntityPersister;
import com.flowtest.core.snapshot.SnapshotEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/**
 * Cleanup strategy that uses database snapshots to detect and delete all test data.
 * This includes both data created during persist() phase and data produced by
 * business logic during act() phase.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>beforeTest: Record MAX(ID) for each table as baseline</li>
 *   <li>afterTest: Delete rows where ID > baseline (act-produced data)</li>
 *   <li>afterTest: Delete rows recorded in persistedIds (persist-produced data)</li>
 * </ol>
 */
public class SnapshotBasedCleanup implements CleanupStrategy {

    private static final Logger log = LoggerFactory.getLogger(SnapshotBasedCleanup.class);

    private final SnapshotEngine snapshotEngine;
    private final EntityPersister persister;
    private String idColumnName = "id";

    public SnapshotBasedCleanup(SnapshotEngine snapshotEngine, EntityPersister persister) {
        this.snapshotEngine = snapshotEngine;
        this.persister = persister;
    }

    public void setIdColumnName(String idColumnName) {
        this.idColumnName = idColumnName;
    }

    @Override
    public void beforeTest(TestContext context) {
        // Record MAX(ID) for all tables as cleanup baseline
        Set<String> tables = snapshotEngine.listTableNames();
        Map<String, Long> snapshot = new LinkedHashMap<>();

        JdbcTemplate jdbc = snapshotEngine.getJdbcTemplate();

        for (String table : tables) {
            try {
                String sql = "SELECT MAX(" + idColumnName + ") FROM " + table;
                Long maxId = jdbc.queryForObject(sql, Long.class);
                snapshot.put(table, maxId != null ? maxId : 0L);
                log.debug("Cleanup baseline for {}: maxId={}", table, maxId);
            } catch (Exception e) {
                log.debug("Skipping table {} for cleanup: {}", table, e.getMessage());
                snapshot.put(table, 0L);
            }
        }

        context.setCleanupSnapshot(snapshot);
        log.debug("Recorded cleanup snapshot for  tables", snapshot.size());
    }

    @Override
    public void afterTest(TestContext context) {
        // Step 1: Delete act-produced data (ID > baseline)
        deleteActProducedData(context);

        // Step 2: Delete persist-produced data
        deletePersistedEntities(context);
    }

    @Override
    public CleanupMode getMode() {
        return CleanupMode.SNAPSHOT_BASED;
    }

    /**
     * Deletes rows created during act() phase by comparing against baseline MAX(ID).
     */
    private void deleteActProducedData(TestContext context) {
        Map<String, Long> baseline = context.getCleanupSnapshot();
        if (baseline.isEmpty()) {
            log.debug("No cleanup baseline found, skipping act-produced data cleanup");
            return;
        }

        JdbcTemplate jdbc = snapshotEngine.getJdbcTemplate();

        for (Map.Entry<String, Long> entry : baseline.entrySet()) {
            String table = entry.getKey();
            Long baselineMaxId = entry.getValue();

            try {
                String sql = "DELETE FROM " + table + " WHERE " + idColumnName + " > ?";
                int deleted = jdbc.update(sql, baselineMaxId);
                if (deleted > 0) {
                    log.debug("Deleted {} act-produced rows from {} (id > {})",
                        deleted, table, baselineMaxId);
                }
            } catch (Exception e) {
                log.warn("Failed to cleanup act-produced data from {}: {}", table, e.getMessage());
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
