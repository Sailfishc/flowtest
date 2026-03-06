package com.github.sailfishc.flowtest.v2.spec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Logical routing values used only for physical table name resolution.
 */
public final class TableRouteScope {

    private static final TableRouteScope EMPTY = new TableRouteScope(Collections.<TableRouteValue>emptyList());

    private final List<TableRouteValue> values;

    private TableRouteScope(List<TableRouteValue> values) {
        this.values = Collections.unmodifiableList(new ArrayList<TableRouteValue>(values));
    }

    public static TableRouteScope empty() {
        return EMPTY;
    }

    public static TableRouteScope of(String key, Object value) {
        return new TableRouteScope(Collections.singletonList(TableRouteValue.of(key, value)));
    }

    public static TableRouteScope of(TableRouteValue... values) {
        return new TableRouteScope(Arrays.asList(values));
    }

    public TableRouteScope append(String key, Object value) {
        return append(TableRouteValue.of(key, value));
    }

    public TableRouteScope append(TableRouteValue value) {
        List<TableRouteValue> updated = new ArrayList<TableRouteValue>(values);
        updated.add(value);
        return new TableRouteScope(updated);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public List<TableRouteValue> getValues() {
        return values;
    }
}
