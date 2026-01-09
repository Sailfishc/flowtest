package com.flowtest.core.snapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.*;

/**
 * Engine for taking database snapshots and computing differences.
 * Uses MAX(ID) and row count to detect changes efficiently.
 */
public class SnapshotEngine {

    private static final Logger log = LoggerFactory.getLogger(SnapshotEngine.class);

    private final JdbcTemplate jdbcTemplate;
    private String idColumnName = "id";

    public SnapshotEngine(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public SnapshotEngine(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Sets the ID column name to use for snapshots.
     * Default is "id".
     */
    public void setIdColumnName(String idColumnName) {
        this.idColumnName = idColumnName;
    }

    /**
     * Takes a "before" snapshot of the given tables.
     *
     * @param tables the table names to snapshot
     * @return map of table name to snapshot
     */
    public Map<String, TableSnapshot> takeBeforeSnapshot(Set<String> tables) {
        Map<String, TableSnapshot> snapshots = new LinkedHashMap<>();

        for (String table : tables) {
            TableSnapshot snapshot = new TableSnapshot(table);
            snapshot.setMaxId(getMaxId(table));
            snapshot.setRowCount(getRowCount(table));
            snapshots.put(table, snapshot);
            log.debug("Before snapshot for {}: maxId={}, rowCount={}",
                table, snapshot.getMaxId(), snapshot.getRowCount());
        }

        return snapshots;
    }

    /**
     * Takes an "after" snapshot of the given tables.
     *
     * @param tables the table names to snapshot
     * @return map of table name to snapshot
     */
    public Map<String, TableSnapshot> takeAfterSnapshot(Set<String> tables) {
        Map<String, TableSnapshot> snapshots = new LinkedHashMap<>();

        for (String table : tables) {
            TableSnapshot snapshot = new TableSnapshot(table);
            snapshot.setMaxId(getMaxId(table));
            snapshot.setRowCount(getRowCount(table));
            snapshots.put(table, snapshot);
            log.debug("After snapshot for {}: maxId={}, rowCount={}",
                table, snapshot.getMaxId(), snapshot.getRowCount());
        }

        return snapshots;
    }

    /**
     * Computes the difference between before and after snapshots.
     *
     * @param before the before snapshots
     * @param after the after snapshots
     * @return the computed diff
     */
    public SnapshotDiff computeDiff(Map<String, TableSnapshot> before, Map<String, TableSnapshot> after) {
        SnapshotDiff diff = new SnapshotDiff();

        Set<String> allTables = new LinkedHashSet<>();
        allTables.addAll(before.keySet());
        allTables.addAll(after.keySet());

        for (String table : allTables) {
            TableSnapshot beforeSnap = before.get(table);
            TableSnapshot afterSnap = after.get(table);

            long beforeMaxId = beforeSnap != null && beforeSnap.getMaxId() != null ? beforeSnap.getMaxId() : 0;
            long afterMaxId = afterSnap != null && afterSnap.getMaxId() != null ? afterSnap.getMaxId() : 0;
            long beforeCount = beforeSnap != null && beforeSnap.getRowCount() != null ? beforeSnap.getRowCount() : 0;
            long afterCount = afterSnap != null && afterSnap.getRowCount() != null ? afterSnap.getRowCount() : 0;

            // Calculate new rows based on MAX(ID) difference
            long newRows = Math.max(0, afterMaxId - beforeMaxId);
            diff.setNewRowCount(table, newRows);

            // Calculate deleted rows based on count difference
            // deletedRows = beforeCount + newRows - afterCount
            long deletedRows = Math.max(0, beforeCount + newRows - afterCount);
            diff.setDeletedRowCount(table, deletedRows);

            // Fetch actual new row data if there are new rows
            if (newRows > 0) {
                List<Map<String, Object>> newRowsData = fetchNewRows(table, beforeMaxId, afterMaxId);
                diff.setNewRowsData(table, newRowsData);
            }

            log.debug("Diff for {}: newRows={}, deletedRows={}", table, newRows, deletedRows);
        }

        return diff;
    }

    /**
     * Gets the MAX(ID) for a table.
     */
    private Long getMaxId(String table) {
        try {
            String sql = "SELECT MAX(" + idColumnName + ") FROM " + table;
            return jdbcTemplate.queryForObject(sql, Long.class);
        } catch (Exception e) {
            log.warn("Failed to get max ID for table {}: {}", table, e.getMessage());
            return 0L;
        }
    }

    /**
     * Gets the row count for a table.
     */
    private Long getRowCount(String table) {
        try {
            String sql = "SELECT COUNT(*) FROM " + table;
            return jdbcTemplate.queryForObject(sql, Long.class);
        } catch (Exception e) {
            log.warn("Failed to get row count for table {}: {}", table, e.getMessage());
            return 0L;
        }
    }

    /**
     * Fetches new rows between the before and after max IDs.
     */
    private List<Map<String, Object>> fetchNewRows(String table, long beforeMaxId, long afterMaxId) {
        try {
            String sql = "SELECT * FROM " + table +
                " WHERE " + idColumnName + " > ? AND " + idColumnName + " <= ?" +
                " ORDER BY " + idColumnName;
            return jdbcTemplate.queryForList(sql, beforeMaxId, afterMaxId);
        } catch (Exception e) {
            log.warn("Failed to fetch new rows for table {}: {}", table, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Gets the underlying JdbcTemplate.
     */
    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }
}
