package com.flowtest.core.assertion;

import com.flowtest.core.snapshot.RowModification;
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
        private Long expectedModifiedRows;
        private boolean expectNoChanges = false;
        private final List<RowAssert> rowAsserts = new ArrayList<>();
        private final List<ModifiedRowAssert> modifiedRowAsserts = new ArrayList<>();

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
         * Asserts that the table has exactly N modified rows.
         */
        public TableAssert hasModifiedRows(long count) {
            this.expectedModifiedRows = count;
            return this;
        }

        /**
         * Asserts that the table has at least one modified row.
         */
        public TableAssert hasModifiedRows() {
            return hasModifiedRows(1);
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
         * Starts assertions on a modified row by index.
         */
        public ModifiedRowAssert modifiedRow(int index) {
            ModifiedRowAssert rowAssert = new ModifiedRowAssert(this, index);
            modifiedRowAsserts.add(rowAssert);
            return rowAssert;
        }

        /**
         * Starts assertions on a modified row by primary key value.
         */
        public ModifiedRowAssert modifiedRowWithId(Object primaryKeyValue) {
            ModifiedRowAssert rowAssert = new ModifiedRowAssert(this, primaryKeyValue);
            modifiedRowAsserts.add(rowAssert);
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
            long actualModifiedRows = diff.getModifiedRowCount(tableName);

            if (expectNoChanges) {
                if (actualNewRows > 0 || actualDeletedRows > 0 || actualModifiedRows > 0) {
                    failures.add(String.format(
                        "Table '%s': expected no changes, but found %d new, %d deleted, %d modified rows",
                        tableName, actualNewRows, actualDeletedRows, actualModifiedRows));
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

            if (expectedModifiedRows != null && actualModifiedRows != expectedModifiedRows) {
                failures.add(String.format(
                    "Table '%s': expected %d modified rows, but found %d",
                    tableName, expectedModifiedRows, actualModifiedRows));
            }

            // Validate row assertions
            for (RowAssert rowAssert : rowAsserts) {
                failures.addAll(rowAssert.validate());
            }

            // Validate modified row assertions
            for (ModifiedRowAssert rowAssert : modifiedRowAsserts) {
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

    /**
     * Modified row assertion builder.
     */
    public static class ModifiedRowAssert {

        private final TableAssert parent;
        private final Integer rowIndex;
        private final Object primaryKeyValue;
        private final List<ColumnChangeAssert> columnAsserts = new ArrayList<>();

        public ModifiedRowAssert(TableAssert parent, int rowIndex) {
            this.parent = parent;
            this.rowIndex = rowIndex;
            this.primaryKeyValue = null;
        }

        public ModifiedRowAssert(TableAssert parent, Object primaryKeyValue) {
            this.parent = parent;
            this.rowIndex = null;
            this.primaryKeyValue = primaryKeyValue;
        }

        /**
         * Starts a column change assertion.
         */
        public ColumnChangeAssert column(String columnName) {
            ColumnChangeAssert columnAssert = new ColumnChangeAssert(this, columnName);
            columnAsserts.add(columnAssert);
            return columnAssert;
        }

        /**
         * Asserts that a specific column was modified.
         */
        public ModifiedRowAssert hasChangedColumn(String columnName) {
            ColumnChangeAssert columnAssert = new ColumnChangeAssert(this, columnName);
            columnAssert.wasModified();
            columnAsserts.add(columnAssert);
            return this;
        }

        /**
         * Asserts that a specific column was NOT modified.
         */
        public ModifiedRowAssert hasUnchangedColumn(String columnName) {
            ColumnChangeAssert columnAssert = new ColumnChangeAssert(this, columnName);
            columnAssert.wasNotModified();
            columnAsserts.add(columnAssert);
            return this;
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

        List<String> validate() {
            List<String> failures = new ArrayList<>();
            RowModification modification = findModification();

            if (modification == null) {
                if (rowIndex != null) {
                    failures.add(String.format(
                        "Table '%s': no modified row at index %d",
                        parent.tableName, rowIndex));
                } else {
                    failures.add(String.format(
                        "Table '%s': no modified row with primary key %s",
                        parent.tableName, primaryKeyValue));
                }
                return failures;
            }

            for (ColumnChangeAssert columnAssert : columnAsserts) {
                String failure = columnAssert.validate(modification, parent.tableName);
                if (failure != null) {
                    failures.add(failure);
                }
            }
            return failures;
        }

        private RowModification findModification() {
            List<RowModification> modifications = parent.diff.getModifiedRowsData(parent.tableName);
            if (rowIndex != null) {
                if (rowIndex >= modifications.size()) {
                    return null;
                }
                return modifications.get(rowIndex);
            } else {
                for (RowModification mod : modifications) {
                    if (primaryKeyValue.equals(mod.getPrimaryKeyValue())) {
                        return mod;
                    }
                }
                return null;
            }
        }
    }

    /**
     * Column change assertion builder for modified rows.
     */
    public static class ColumnChangeAssert {

        private final ModifiedRowAssert parent;
        private final String columnName;
        private Object expectedBeforeValue;
        private Object expectedAfterValue;
        private boolean checkWasModified = false;
        private boolean expectModified = true;

        public ColumnChangeAssert(ModifiedRowAssert parent, String columnName) {
            this.parent = parent;
            this.columnName = columnName;
        }

        /**
         * Asserts the before value of the column.
         */
        public ColumnChangeAssert changedFrom(Object expectedBefore) {
            this.expectedBeforeValue = expectedBefore;
            return this;
        }

        /**
         * Asserts the after value of the column.
         */
        public ModifiedRowAssert to(Object expectedAfter) {
            this.expectedAfterValue = expectedAfter;
            return parent;
        }

        /**
         * Asserts only the after value.
         */
        public ModifiedRowAssert changedTo(Object expectedAfter) {
            this.expectedAfterValue = expectedAfter;
            return parent;
        }

        /**
         * Asserts that the column was modified.
         */
        public ModifiedRowAssert wasModified() {
            this.checkWasModified = true;
            this.expectModified = true;
            return parent;
        }

        /**
         * Asserts that the column was NOT modified.
         */
        public ModifiedRowAssert wasNotModified() {
            this.checkWasModified = true;
            this.expectModified = false;
            return parent;
        }

        String validate(RowModification modification, String tableName) {
            Object actualBefore = modification.getBeforeValue(columnName);
            Object actualAfter = modification.getAfterValue(columnName);
            boolean wasActuallyModified = modification.isColumnModified(columnName);

            if (checkWasModified) {
                if (expectModified && !wasActuallyModified) {
                    return String.format(
                        "Table '%s' column '%s': expected modified, but unchanged (value: %s)",
                        tableName, columnName, actualBefore);
                }
                if (!expectModified && wasActuallyModified) {
                    return String.format(
                        "Table '%s' column '%s': expected unchanged, but modified from '%s' to '%s'",
                        tableName, columnName, actualBefore, actualAfter);
                }
            }

            if (expectedBeforeValue != null && !valuesEqual(expectedBeforeValue, actualBefore)) {
                return String.format(
                    "Table '%s' column '%s': expected before '%s', but was '%s'",
                    tableName, columnName, expectedBeforeValue, actualBefore);
            }

            if (expectedAfterValue != null && !valuesEqual(expectedAfterValue, actualAfter)) {
                return String.format(
                    "Table '%s' column '%s': expected after '%s', but was '%s'",
                    tableName, columnName, expectedAfterValue, actualAfter);
            }

            return null;
        }

        private boolean valuesEqual(Object expected, Object actual) {
            if (expected == null && actual == null) return true;
            if (expected == null || actual == null) return false;
            if (expected instanceof Number && actual instanceof Number) {
                return ((Number) expected).doubleValue() == ((Number) actual).doubleValue();
            }
            return expected.toString().equals(actual.toString());
        }
    }
}
