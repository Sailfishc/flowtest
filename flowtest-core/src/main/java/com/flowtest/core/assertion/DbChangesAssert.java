package com.flowtest.core.assertion;

import com.flowtest.core.snapshot.SnapshotDiff;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fluent assertion DSL for database changes.
 * Provides table-level and row-level assertions based on snapshot diffs.
 */
public class DbChangesAssert {

    private final SnapshotDiff diff;
    private final List<TableAssert> tableAsserts = new ArrayList<>();

    public DbChangesAssert(SnapshotDiff diff) {
        this.diff = diff;
    }

    /**
     * Starts assertions on a specific table.
     */
    public TableAssert table(String tableName) {
        TableAssert tableAssert = new TableAssert(this, tableName, diff);
        tableAsserts.add(tableAssert);
        return tableAssert;
    }

    /**
     * Verifies all table assertions.
     * Called internally after all assertions are defined.
     */
    void verify() {
        List<String> failures = new ArrayList<>();

        for (TableAssert tableAssert : tableAsserts) {
            failures.addAll(tableAssert.validate());
        }

        if (!failures.isEmpty()) {
            throw new AssertionError("Database change assertions failed:\n" +
                String.join("\n", failures));
        }
    }

    /**
     * Table-level assertion builder.
     */
    public static class TableAssert {

        private final DbChangesAssert parent;
        private final String tableName;
        private final SnapshotDiff diff;

        private Long expectedNewRows;
        private Long expectedDeletedRows;
        private boolean expectNoChanges = false;
        private final List<RowAssert> rowAsserts = new ArrayList<>();

        public TableAssert(DbChangesAssert parent, String tableName, SnapshotDiff diff) {
            this.parent = parent;
            this.tableName = tableName;
            this.diff = diff;
        }

        /**
         * Asserts that the table has exactly N new rows.
         */
        public TableAssert hasNumberOfRows(long count) {
            this.expectedNewRows = count;
            return this;
        }

        /**
         * Alias for hasNumberOfRows.
         */
        public TableAssert hasNewRows(long count) {
            return hasNumberOfRows(count);
        }

        /**
         * Asserts that the table has at least one new row.
         */
        public TableAssert hasNewRows() {
            return hasNewRows(1);
        }

        /**
         * Asserts that the table has N deleted rows.
         */
        public TableAssert hasDeletedRows(long count) {
            this.expectedDeletedRows = count;
            return this;
        }

        /**
         * Asserts that the table has no changes (no inserts, updates, or deletes).
         */
        public TableAssert hasNoChanges() {
            this.expectNoChanges = true;
            return this;
        }

        /**
         * Starts assertions on a specific row by index.
         */
        public RowAssert row(int index) {
            RowAssert rowAssert = new RowAssert(this, index);
            rowAsserts.add(rowAssert);
            return rowAssert;
        }

        /**
         * Chains to another table assertion.
         */
        public TableAssert table(String tableName) {
            return parent.table(tableName);
        }

        /**
         * Validates this table's assertions.
         */
        List<String> validate() {
            List<String> failures = new ArrayList<>();

            long actualNewRows = diff.getNewRowCount(tableName);
            long actualDeletedRows = diff.getDeletedRowCount(tableName);

            if (expectNoChanges) {
                if (actualNewRows > 0 || actualDeletedRows > 0) {
                    failures.add(String.format(
                        "Table '%s': expected no changes, but found %d new rows and %d deleted rows",
                        tableName, actualNewRows, actualDeletedRows));
                }
            }

            if (expectedNewRows != null && actualNewRows != expectedNewRows) {
                failures.add(String.format(
                    "Table '%s': expected %d new rows, but found %d",
                    tableName, expectedNewRows, actualNewRows));
            }

            if (expectedDeletedRows != null && actualDeletedRows != expectedDeletedRows) {
                failures.add(String.format(
                    "Table '%s': expected %d deleted rows, but found %d",
                    tableName, expectedDeletedRows, actualDeletedRows));
            }

            // Validate row assertions
            for (RowAssert rowAssert : rowAsserts) {
                failures.addAll(rowAssert.validate());
            }

            return failures;
        }
    }

    /**
     * Row-level assertion builder.
     */
    public static class RowAssert {

        private final TableAssert parent;
        private final int rowIndex;
        private final List<ColumnAssert> columnAsserts = new ArrayList<>();

        public RowAssert(TableAssert parent, int rowIndex) {
            this.parent = parent;
            this.rowIndex = rowIndex;
        }

        /**
         * Starts assertions on a specific column.
         */
        public ColumnAssert value(String columnName) {
            ColumnAssert columnAssert = new ColumnAssert(this, columnName);
            columnAsserts.add(columnAssert);
            return columnAssert;
        }

        /**
         * Returns to the parent table assert.
         */
        public TableAssert and() {
            return parent;
        }

        /**
         * Chains to another table assertion.
         */
        public TableAssert table(String tableName) {
            return parent.table(tableName);
        }

        /**
         * Validates this row's assertions.
         */
        List<String> validate() {
            List<String> failures = new ArrayList<>();

            List<Map<String, Object>> newRows = parent.diff.getNewRowsData(parent.tableName);

            if (rowIndex >= newRows.size()) {
                failures.add(String.format(
                    "Table '%s': no row at index %d (only %d new rows)",
                    parent.tableName, rowIndex, newRows.size()));
                return failures;
            }

            Map<String, Object> row = newRows.get(rowIndex);

            for (ColumnAssert columnAssert : columnAsserts) {
                String failure = columnAssert.validate(row);
                if (failure != null) {
                    failures.add(failure);
                }
            }

            return failures;
        }
    }

    /**
     * Column-level assertion builder.
     */
    public static class ColumnAssert {

        private final RowAssert parent;
        private final String columnName;
        private Object expectedValue;
        private boolean checkNull = false;
        private boolean expectNull = false;

        public ColumnAssert(RowAssert parent, String columnName) {
            this.parent = parent;
            this.columnName = columnName;
        }

        /**
         * Asserts that the column value equals the expected value.
         */
        public RowAssert isEqualTo(Object expected) {
            this.expectedValue = expected;
            return parent;
        }

        /**
         * Asserts that the column value is null.
         */
        public RowAssert isNull() {
            this.checkNull = true;
            this.expectNull = true;
            return parent;
        }

        /**
         * Asserts that the column value is not null.
         */
        public RowAssert isNotNull() {
            this.checkNull = true;
            this.expectNull = false;
            return parent;
        }

        /**
         * Validates this column assertion.
         */
        String validate(Map<String, Object> row) {
            Object actualValue = row.get(columnName);

            // Try case-insensitive match if exact match fails
            if (actualValue == null && !row.containsKey(columnName)) {
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(columnName)) {
                        actualValue = entry.getValue();
                        break;
                    }
                }
            }

            if (checkNull) {
                if (expectNull && actualValue != null) {
                    return String.format(
                        "Table '%s' row %d column '%s': expected null, but was '%s'",
                        parent.parent.tableName, parent.rowIndex, columnName, actualValue);
                }
                if (!expectNull && actualValue == null) {
                    return String.format(
                        "Table '%s' row %d column '%s': expected not null, but was null",
                        parent.parent.tableName, parent.rowIndex, columnName);
                }
            }

            if (expectedValue != null) {
                if (!valuesEqual(expectedValue, actualValue)) {
                    return String.format(
                        "Table '%s' row %d column '%s': expected '%s', but was '%s'",
                        parent.parent.tableName, parent.rowIndex, columnName, expectedValue, actualValue);
                }
            }

            return null;
        }

        /**
         * Compares values with type coercion.
         */
        private boolean valuesEqual(Object expected, Object actual) {
            if (expected == null && actual == null) {
                return true;
            }
            if (expected == null || actual == null) {
                return false;
            }

            // Handle numeric comparisons
            if (expected instanceof Number && actual instanceof Number) {
                return ((Number) expected).doubleValue() == ((Number) actual).doubleValue();
            }

            // String comparison
            return expected.toString().equals(actual.toString());
        }
    }
}
