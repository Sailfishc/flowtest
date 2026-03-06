package com.github.sailfishc.flowtest.v2.spec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Row-level change set for one observed resource.
 */
public final class ResourceChange {

    private final String resourceName;
    private final List<RowSnapshot> insertedRows;
    private final List<RowSnapshot> deletedRows;
    private final List<ModifiedRow> modifiedRows;

    public ResourceChange(String resourceName,
                          List<RowSnapshot> insertedRows,
                          List<RowSnapshot> deletedRows,
                          List<ModifiedRow> modifiedRows) {
        this.resourceName = requireText(resourceName, "resourceName must not be blank");
        this.insertedRows = Collections.unmodifiableList(new ArrayList<RowSnapshot>(insertedRows));
        this.deletedRows = Collections.unmodifiableList(new ArrayList<RowSnapshot>(deletedRows));
        this.modifiedRows = Collections.unmodifiableList(new ArrayList<ModifiedRow>(modifiedRows));
    }

    public static ResourceChange between(ResourceSnapshot before, ResourceSnapshot after) {
        Set<RowKey> keys = new LinkedHashSet<RowKey>();
        keys.addAll(before.asMap().keySet());
        keys.addAll(after.asMap().keySet());

        List<RowSnapshot> inserted = new ArrayList<RowSnapshot>();
        List<RowSnapshot> deleted = new ArrayList<RowSnapshot>();
        List<ModifiedRow> modified = new ArrayList<ModifiedRow>();
        for (RowKey key : keys) {
            RowSnapshot beforeRow = before.asMap().get(key);
            RowSnapshot afterRow = after.asMap().get(key);
            if (beforeRow == null && afterRow != null) {
                inserted.add(afterRow);
            } else if (beforeRow != null && afterRow == null) {
                deleted.add(beforeRow);
            } else if (beforeRow != null && !beforeRow.equals(afterRow)) {
                modified.add(new ModifiedRow(beforeRow, afterRow));
            }
        }
        return new ResourceChange(after.getResourceName(), inserted, deleted, modified);
    }

    public String getResourceName() {
        return resourceName;
    }

    public List<RowSnapshot> getInsertedRows() {
        return insertedRows;
    }

    public List<RowSnapshot> getDeletedRows() {
        return deletedRows;
    }

    public List<ModifiedRow> getModifiedRows() {
        return modifiedRows;
    }

    public long getInsertedCount() {
        return insertedRows.size();
    }

    public long getDeletedCount() {
        return deletedRows.size();
    }

    public long getModifiedCount() {
        return modifiedRows.size();
    }

    private static String requireText(String text, String message) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }
}
