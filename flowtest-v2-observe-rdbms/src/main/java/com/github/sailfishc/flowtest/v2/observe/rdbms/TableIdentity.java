package com.github.sailfishc.flowtest.v2.observe.rdbms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Identity definition used for diffing a relational table.
 */
public final class TableIdentity {

    private final String tableName;
    private final List<String> keyColumns;

    private TableIdentity(String tableName, List<String> keyColumns) {
        this.tableName = requireText(tableName, "tableName must not be blank");
        if (keyColumns == null || keyColumns.isEmpty()) {
            throw new IllegalArgumentException("keyColumns must not be empty");
        }
        List<String> normalized = new ArrayList<String>();
        for (String keyColumn : keyColumns) {
            normalized.add(requireText(keyColumn, "keyColumn must not be blank").toLowerCase());
        }
        this.keyColumns = Collections.unmodifiableList(normalized);
    }

    public static TableIdentity of(String tableName, String... keyColumns) {
        return new TableIdentity(tableName, Arrays.asList(keyColumns));
    }

    public String getTableName() {
        return tableName;
    }

    public List<String> getKeyColumns() {
        return keyColumns;
    }

    public TableIdentity withTableName(String resolvedTableName) {
        return new TableIdentity(resolvedTableName, keyColumns);
    }

    private static String requireText(String text, String message) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }
}
