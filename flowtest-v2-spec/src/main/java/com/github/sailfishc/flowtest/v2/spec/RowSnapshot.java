package com.github.sailfishc.flowtest.v2.spec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable row image captured during observation.
 */
public final class RowSnapshot {

    private final RowKey key;
    private final Map<String, Object> columns;

    public RowSnapshot(RowKey key, Map<String, Object> columns) {
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.columns = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(columns));
    }

    public RowKey getKey() {
        return key;
    }

    public Map<String, Object> getColumns() {
        return columns;
    }

    public Object getColumn(String columnName) {
        return columns.get(columnName);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RowSnapshot)) {
            return false;
        }
        RowSnapshot that = (RowSnapshot) other;
        return key.equals(that.key) && columns.equals(that.columns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, columns);
    }
}
