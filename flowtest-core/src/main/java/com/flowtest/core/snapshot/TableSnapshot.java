package com.flowtest.core.snapshot;

/**
 * Snapshot of a single table's state at a point in time.
 * Captures MAX(ID) and row count for change detection.
 */
public class TableSnapshot {

    private final String tableName;
    private Long maxId;
    private Long rowCount;

    public TableSnapshot(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }

    public Long getMaxId() {
        return maxId;
    }

    public void setMaxId(Long maxId) {
        this.maxId = maxId;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public void setRowCount(Long rowCount) {
        this.rowCount = rowCount;
    }

    @Override
    public String toString() {
        return "TableSnapshot{" +
            "tableName='" + tableName + '\'' +
            ", maxId=" + maxId +
            ", rowCount=" + rowCount +
            '}';
    }
}
