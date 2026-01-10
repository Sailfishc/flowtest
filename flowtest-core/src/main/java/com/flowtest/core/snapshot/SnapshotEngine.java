package com.flowtest.core.snapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;

/**
 * Engine for taking database snapshots and computing differences.
 * Uses MAX(ID) and row count to detect changes efficiently.
 */
public class SnapshotEngine {

    private static final Logger log = LoggerFactory.getLogger(SnapshotEngine.class);

    private final JdbcTemplate jdbcTemplate;
    private String idColumnName = "id";

    /** Whether to capture full row data for modification detection */
    private boolean captureFullRows = true;

    /** Maximum rows to capture per table (safety limit) */
    private int maxRowsToCapture = 10000;

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
     * Sets whether to capture full row data for modification detection.
     * Default is true.
     */
    public void setCaptureFullRows(boolean captureFullRows) {
        this.captureFullRows = captureFullRows;
    }

    /**
     * Sets the maximum rows to capture per table.
     * Default is 10000.
     */
    public void setMaxRowsToCapture(int maxRowsToCapture) {
        this.maxRowsToCapture = maxRowsToCapture;
    }

    /**
     * Lists all user table names in the current database.
     */
    public Set<String> listTableNames() {
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) {
            return Collections.emptySet();
        }

        Set<String> tables = new LinkedHashSet<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(connection.getCatalog(), null, "%", new String[] {"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (name != null && !name.isEmpty()) {
                        tables.add(name);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list tables: {}", e.getMessage());
        }

        return tables;
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

            // Capture full row data if enabled
            if (captureFullRows) {
                Map<Object, Map<String, Object>> rowData = fetchAllRowsIndexedByPK(table);
                snapshot.setRowsByPrimaryKey(rowData);
            }

            snapshots.put(table, snapshot);
            log.debug("Before snapshot for {}: maxId={}, rowCount={}, rowDataSize={}",
                table, snapshot.getMaxId(), snapshot.getRowCount(),
                snapshot.getRowsByPrimaryKey().size());
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

            // Capture full row data if enabled
            if (captureFullRows) {
                Map<Object, Map<String, Object>> rowData = fetchAllRowsIndexedByPK(table);
                snapshot.setRowsByPrimaryKey(rowData);
            }

            snapshots.put(table, snapshot);
            log.debug("After snapshot for {}: maxId={}, rowCount={}, rowDataSize={}",
                table, snapshot.getMaxId(), snapshot.getRowCount(),
                snapshot.getRowsByPrimaryKey().size());
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

            // Compute modifications if full row data is available
            if (beforeSnap != null && afterSnap != null
                && beforeSnap.hasRowData() && afterSnap.hasRowData()) {
                computeModifications(diff, table, beforeSnap, afterSnap);
            }

            log.debug("Diff for {}: newRows={}, deletedRows={}, modifiedRows={}",
                table, newRows, deletedRows, diff.getModifiedRowCount(table));
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
            log.warn("Failed to fetch new rows for table {}: ", table, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches all rows indexed by primary key.
     */
    private Map<Object, Map<String, Object>> fetchAllRowsIndexedByPK(String table) {
        Map<Object, Map<String, Object>> result = new LinkedHashMap<>();

        try {
            String sql = "SELECT * FROM " + table + " ORDER BY " + idColumnName;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            int limit = Math.min(rows.size(), maxRowsToCapture);
            for (int i = 0; i < limit; i++) {
                Map<String, Object> row = rows.get(i);
                Object pkValue = getValueCaseInsensitive(row, idColumnName);
                if (pkValue != null) {
                    result.put(pkValue, row);
                }
            }

            if (rows.size() > maxRowsToCapture) {
                log.warn("Table {} has {} rows, only capturing first {} for modification detection",
                    table, rows.size(), maxRowsToCapture);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch rows for table {}: {}", table, e.getMessage());
        }

        return result;
    }

    /**
     * Gets the underlying JdbcTemplate.
     */
    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    /**
     * Computes row modifications by comparing before/after row data.
     */
    private void computeModifications(SnapshotDiff diff, String table,
                                      TableSnapshot beforeSnap,
                                      TableSnapshot afterSnap) {
        Map<Object, Map<String, Object>> beforeRows = beforeSnap.getRowsByPrimaryKey();
        Map<Object, Map<String, Object>> afterRows = afterSnap.getRowsByPrimaryKey();

        List<RowModification> modifications = new ArrayList<>();

        for (Map.Entry<Object, Map<String, Object>> beforeEntry : beforeRows.entrySet()) {
            Object pk = beforeEntry.getKey();
            Map<String, Object> beforeRow = beforeEntry.getValue();
            Map<String, Object> afterRow = afterRows.get(pk);

            // Row exists in both - check if modified
            if (afterRow != null && !rowsEqual(beforeRow, afterRow)) {
                modifications.add(new RowModification(pk, beforeRow, afterRow));
            }
        }

        diff.setModifiedRowCount(table, modifications.size());
        diff.setModifiedRowsData(table, modifications);
    }

    /**
     * Compares two rows for equality.
     */
    private boolean rowsEqual(Map<String, Object> row1, Map<String, Object> row2) {
        if (row1.size() != row2.size()) {
            return false;
        }
        for (Map.Entry<String, Object> entry : row1.entrySet()) {
            String key = entry.getKey();
            Object val1 = entry.getValue();
            Object val2 = getValueCaseInsensitive(row2, key);
            if (!valuesEqual(val1, val2)) {
                return false;
            }
        }
        return true;
    }

    private boolean valuesEqual(Object v1, Object v2) {
        if (v1 == null && v2 == null) return true;
        if (v1 == null || v2 == null) return false;
        if (v1 instanceof Number && v2 instanceof Number) {
            return ((Number) v1).doubleValue() == ((Number) v2).doubleValue();
        }
        return v1.equals(v2);
    }

    private Object getValueCaseInsensitive(Map<String, Object> row, String columnName) {
        if (row.containsKey(columnName)) {
            return row.get(columnName);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(columnName)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
