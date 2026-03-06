package com.github.sailfishc.flowtest.v2.spec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of a single observed resource keyed by identity.
 */
public final class ResourceSnapshot {

    private final String resourceName;
    private final Map<RowKey, RowSnapshot> rows;

    public ResourceSnapshot(String resourceName, List<RowSnapshot> rows) {
        this.resourceName = requireText(resourceName, "resourceName must not be blank");
        Map<RowKey, RowSnapshot> indexed = new LinkedHashMap<RowKey, RowSnapshot>();
        for (RowSnapshot row : rows) {
            indexed.put(row.getKey(), row);
        }
        this.rows = Collections.unmodifiableMap(indexed);
    }

    public static ResourceSnapshot empty(String resourceName) {
        return new ResourceSnapshot(resourceName, Collections.<RowSnapshot>emptyList());
    }

    public String getResourceName() {
        return resourceName;
    }

    public List<RowSnapshot> getRows() {
        return Collections.unmodifiableList(new ArrayList<RowSnapshot>(rows.values()));
    }

    public Map<RowKey, RowSnapshot> asMap() {
        return rows;
    }

    private static String requireText(String text, String message) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }
}
