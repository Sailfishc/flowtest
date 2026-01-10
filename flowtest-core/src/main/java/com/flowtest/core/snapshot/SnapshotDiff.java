package com.flowtest.core.snapshot;

import java.util.*;

/**
 * Represents the difference between before and after snapshots.
 * Stores new row counts, deleted row counts, and actual new row data for each table.
 */
public class SnapshotDiff {

    /** Number of new rows per table */
    private final Map<String, Long> newRowCounts = new LinkedHashMap<>();

    /** Number of deleted rows per table */
    private final Map<String, Long> deletedRowCounts = new LinkedHashMap<>();

    /** Actual data of new rows per table */
    private final Map<String, List<Map<String, Object>>> newRowsData = new LinkedHashMap<>();

    private String normalize(String tableName) {
        return tableName == null ? null : tableName.toLowerCase();
    }

    /**
     * Gets the number of new rows for a table.
     */
    public long getNewRowCount(String tableName) {
        return newRowCounts.getOrDefault(normalize(tableName), 0L);
    }

    /**
     * Sets the number of new rows for a table.
     */
    public void setNewRowCount(String tableName, long count) {
        newRowCounts.put(normalize(tableName), count);
    }

    /**
     * Gets the number of deleted rows for a table.
     */
    public long getDeletedRowCount(String tableName) {
        return deletedRowCounts.getOrDefault(normalize(tableName), 0L);
    }

    /**
     * Sets the number of deleted rows for a table.
     */
    public void setDeletedRowCount(String tableName, long count) {
        deletedRowCounts.put(normalize(tableName), count);
    }

    /**
     * Checks if a table has any changes.
     */
    public boolean hasChanges(String tableName) {
        return getNewRowCount(tableName) > 0 || getDeletedRowCount(tableName) > 0;
    }

    /**
     * Gets the actual data of new rows for a table.
     */
    public List<Map<String, Object>> getNewRowsData(String tableName) {
        return newRowsData.getOrDefault(normalize(tableName), Collections.emptyList());
    }

    /**
     * Sets the actual data of new rows for a table.
     */
    public void setNewRowsData(String tableName, List<Map<String, Object>> data) {
        newRowsData.put(normalize(tableName), data);
    }

    /**
     * Gets all table names that have changes.
     */
    public Set<String> getChangedTables() {
        Set<String> changed = new LinkedHashSet<>();
        for (String table : newRowCounts.keySet()) {
            if (hasChanges(table)) {
                changed.add(table);
            }
        }
        for (String table : deletedRowCounts.keySet()) {
            if (hasChanges(table)) {
                changed.add(table);
            }
        }
        return changed;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SnapshotDiff{\n");
        for (String table : getChangedTables()) {
            sb.append("  ").append(table)
              .append(": +").append(getNewRowCount(table))
              .append(" -").append(getDeletedRowCount(table))
              .append("\n");
        }
        sb.append("}");
        return sb.toString();
    }
}
