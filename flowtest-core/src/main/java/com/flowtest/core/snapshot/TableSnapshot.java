package com.flowtest.core.snapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Snapshot of a single table's state at a point in time.
 * Captures row count and full row data indexed by primary key for change detection.
 */
public class TableSnapshot {

    private final String tableName;
    private Long rowCount;

    /** Full row data indexed by primary key value */
    private Map<Object, Map<String, Object>> rowsByPrimaryKey = new LinkedHashMap<>();

    public TableSnapshot(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public void setRowCount(Long rowCount) {
        this.rowCount = rowCount;
    }

    public Map<Object, Map<String, Object>> getRowsByPrimaryKey() {
        return Collections.unmodifiableMap(rowsByPrimaryKey);
    }

    public void setRowsByPrimaryKey(Map<Object, Map<String, Object>> rowsByPrimaryKey) {
        this.rowsByPrimaryKey = rowsByPrimaryKey != null ? new LinkedHashMap<>(rowsByPrimaryKey) : new LinkedHashMap<>();
    }

    /**
     * Checks if this snapshot has full row data.
     */
    public boolean hasRowData() {
        return !rowsByPrimaryKey.isEmpty();
    }

    @Override
    public String toString() {
        return "TableSnapshot{" +
            "tableName='" + tableName + '\'' +
            ", rowCount=" + rowCount +
            ", rowDataSize=" + rowsByPrimaryKey.size() +
            '}';
    }
}
