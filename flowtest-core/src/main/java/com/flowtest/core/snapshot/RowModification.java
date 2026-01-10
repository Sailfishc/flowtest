package com.flowtest.core.snapshot;

import java.util.*;

/**
 * Represents a modification to a single row.
 * Stores the before and after state along with the primary key.
 */
public class RowModification {

    private final Object primaryKeyValue;
    private final Map<String, Object> beforeRow;
    private final Map<String, Object> afterRow;

    public RowModification(Object primaryKeyValue,
                          Map<String, Object> beforeRow,
                          Map<String, Object> afterRow) {
        this.primaryKeyValue = primaryKeyValue;
        this.beforeRow = beforeRow != null ? new LinkedHashMap<>(beforeRow) : Collections.emptyMap();
        this.afterRow = afterRow != null ? new LinkedHashMap<>(afterRow) : Collections.emptyMap();
    }

    public Object getPrimaryKeyValue() {
        return primaryKeyValue;
    }

    public Map<String, Object> getBeforeRow() {
        return Collections.unmodifiableMap(beforeRow);
    }

    public Map<String, Object> getAfterRow() {
        return Collections.unmodifiableMap(afterRow);
    }

    /**
     * Gets the before value for a specific column.
     */
    public Object getBeforeValue(String columnName) {
        return getValueCaseInsensitive(beforeRow, columnName);
    }

    /**
     * Gets the after value for a specific column.
     */
    public Object getAfterValue(String columnName) {
        return getValueCaseInsensitive(afterRow, columnName);
    }

    /**
     * Checks if a specific column was modified.
     */
    public boolean isColumnModified(String columnName) {
        Object before = getBeforeValue(columnName);
        Object after = getAfterValue(columnName);
        return !valuesEqual(before, after);
    }

    /**
     * Gets all column names that were modified.
     */
    public Set<String> getModifiedColumns() {
        Set<String> modified = new LinkedHashSet<>();
        Set<String> allColumns = new LinkedHashSet<>();
        allColumns.addAll(beforeRow.keySet());
        allColumns.addAll(afterRow.keySet());

        for (String column : allColumns) {
            if (isColumnModified(column)) {
                modified.add(column);
            }
        }
        return modified;
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

    private boolean valuesEqual(Object v1, Object v2) {
        if (v1 == null && v2 == null) {
            return true;
        }
        if (v1 == null || v2 == null) {
            return false;
        }
        if (v1 instanceof Number && v2 instanceof Number) {
            return ((Number) v1).doubleValue() == ((Number) v2).doubleValue();
        }
        return v1.equals(v2);
    }

    @Override
    public String toString() {
        return "RowModification{" +
            "pk=" + primaryKeyValue +
            ", modifiedColumns=" + getModifiedColumns() +
            '}';
    }
}
