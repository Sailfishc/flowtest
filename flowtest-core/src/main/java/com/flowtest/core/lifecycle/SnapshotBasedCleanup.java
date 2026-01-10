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
    private String defaultIdColumn = "id";
    private Map<String, String> tableIdColumns = new HashMap<>();

    public SnapshotBasedCleanup(SnapshotEngine snapshotEngine, EntityPersister persister) {
        this.snapshotEngine = snapshotEngine;
        this.persister = persister;
    }

    public void setDefaultIdColumn(String columnName) {
        this.defaultIdColumn = columnName;
    }

    public void setTableIdColumn(String tableName, String columnName) {
        this.tableIdColumns.put(tableName.toLowerCase(), columnName);
    }

    private String getIdColumn(String tableName) {
        return tableIdColumns.getOrDefault(tableName.toLowerCase(), defaultIdColumn);
    }

    @Override
    public void beforeTest(TestContext context) {
        // Record MAX(ID) for all tables as cleanup baseline
        Set<String> tables = snapshotEngine.listTableNames();
        Map<String, Object> snapshot = new LinkedHashMap<>();

        JdbcTemplate jdbc = snapshotEngine.getJdbcTemplate();

        for (String table : tables) {
            String idColumn = getIdColumn(table);
            try {
                String sql = "SELECT MAX(" + idColumn + ") FROM " + table;
                Object maxId = jdbc.queryForObject(sql, Object.class);
                snapshot.put(table, maxId);
                log.debug("Cleanup baseline for {}: maxId={}", table, maxId);
            } catch (Exception e) {
                log.debug("Skipping table {} for cleanup: {}", table, e.getMessage());
                snapshot.put(table, null);
            }
        }

        context.setCleanupSnapshot(snapshot);
        log.debug("Recorded cleanup snapshot for {} tables", snapshot.size());
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
        Map<String, Object> baseline = context.getCleanupSnapshot();
        if (baseline.isEmpty()) {
            log.debug("No cleanup baseline found, skipping act-produced data cleanup");
            return;
        }

        JdbcTemplate jdbc = snapshotEngine.getJdbcTemplate();

        // Reverse order to handle foreign key dependencies
        List<String> tables = new ArrayList<>(baseline.keySet());
        Collections.reverse(tables);

        for (String table : tables) {
            Object baselineMaxId = baseline.get(table);
            String idColumn = getIdColumn(table);

            try {
                int deleted;
                if (baselineMaxId == null) {
                    // Table was empty before test - delete ALL rows
                    String sql = "DELETE FROM " + table;
                    deleted = jdbc.update(sql);
                    if (deleted > 0) {
                        log.debug("Deleted {} act-produced rows from {} (table was empty before test)",
                            deleted, table);
                    }
                } else {
                    // Delete rows with ID > baseline
                    String sql = "DELETE FROM " + table + " WHERE " + idColumn + " > ?";
                    deleted = jdbc.update(sql, baselineMaxId);
                    if (deleted > 0) {
                        log.debug("Deleted {} act-produced rows from {} ({} > {})",
                            deleted, table, idColumn, baselineMaxId);
                    }
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
