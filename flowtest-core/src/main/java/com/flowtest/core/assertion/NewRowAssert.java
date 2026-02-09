package com.flowtest.core.assertion;

import com.flowtest.core.assertion.ResultAssert.SerializableFunction;
import com.flowtest.core.util.ColumnNameResolver;
import com.flowtest.core.util.ValueComparator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent assertion for new rows created during act() execution.
 * Data comes from SnapshotDiff's newRowsData - no extra database queries.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Simple case: only one new row
 * .newRow(Order.class)
 *     .has(Order::getStatus, OrderStatus.CREATED)
 *
 * // Multiple new rows: use matching() to locate
 * .newRow(Order.class)
 *     .matching("user_id", userId)
 *     .has(Order::getStatus, OrderStatus.CREATED)
 *
 * // Multiple matching conditions
 * .newRow(Order.class)
 *     .matching("user_id", userId)
 *     .matching("product_id", productId)
 *     .has("status", "CREATED")
 * }</pre>
 *
 * @param <E> the entity type
 * @param <R> the original act return type (for AssertBuilder)
 */
public class NewRowAssert<E, R> {

    private final AssertBuilder<R> parent;
    private final List<Map<String, Object>> allNewRows;
    private final Class<E> entityClass;

    /** Current matched row (null means not yet matched, use first row) */
    private Map<String, Object> currentRow;

    /** Matching conditions applied (for error messages) */
    private final Map<String, Object> matchConditions = new LinkedHashMap<>();

    /** Whether matching() was explicitly called */
    private boolean matchingCalled = false;

    public NewRowAssert(AssertBuilder<R> parent, List<Map<String, Object>> allNewRows, Class<E> entityClass) {
        this.parent = parent;
        this.allNewRows = allNewRows != null ? new ArrayList<>(allNewRows) : new ArrayList<>();
        this.entityClass = entityClass;
        // Default to first row if available
        this.currentRow = this.allNewRows.isEmpty() ? null : this.allNewRows.get(0);
    }

    /**
     * Locates a new row by matching a column value.
     * Can be chained multiple times for multiple conditions.
     *
     * @param columnName the column to match
     * @param value the expected value
     * @return this for chaining
     */
    public NewRowAssert<E, R> matching(String columnName, Object value) {
        matchConditions.put(columnName, value);
        matchingCalled = true;

        // Filter rows that match all conditions so far
        List<Map<String, Object>> matchedRows = new ArrayList<>();
        for (Map<String, Object> row : allNewRows) {
            if (rowMatchesAllConditions(row)) {
                matchedRows.add(row);
            }
        }

        if (matchedRows.isEmpty()) {
            throw new AssertionError(String.format(
                "No new %s row found matching %s. Total new rows: %d",
                entityClass.getSimpleName(), matchConditions, allNewRows.size()));
        }

        currentRow = matchedRows.get(0);

        return this;
    }

    /**
     * Locates a new row by matching a property using method reference.
     *
     * @param getter the method reference to extract the property
     * @param value the expected value
     * @param <V> the property type
     * @return this for chaining
     */
    public <V> NewRowAssert<E, R> matching(SerializableFunction<E, V> getter, V value) {
        String columnName = ColumnNameResolver.extractColumnName(getter);
        return matching(columnName, value);
    }

    /**
     * Asserts that a property of the new row equals the expected value.
     * Uses method reference for type-safe property access.
     *
     * @param getter the method reference to extract the property (e.g., Order::getStatus)
     * @param expected the expected value
     * @param <V> the property value type
     * @return this for chaining
     */
    public <V> NewRowAssert<E, R> has(SerializableFunction<E, V> getter, V expected) {
        ensureRowAvailable();

        String columnName = ColumnNameResolver.extractColumnName(getter);
        Object actual = ColumnNameResolver.getValueCaseInsensitive(
            currentRow, columnName, "new " + entityClass.getSimpleName());

        if (!ValueComparator.valuesEqual(expected, actual)) {
            throw new AssertionError(String.format(
                "New %s%s column '%s': expected <%s> but was <%s>",
                entityClass.getSimpleName(), getMatchDescription(), columnName, expected, actual));
        }
        return this;
    }

    /**
     * Asserts that a column value equals the expected value.
     * Uses column name directly.
     *
     * @param columnName the database column name
     * @param expected the expected value
     * @return this for chaining
     */
    public NewRowAssert<E, R> has(String columnName, Object expected) {
        ensureRowAvailable();

        Object actual = ColumnNameResolver.getValueCaseInsensitive(
            currentRow, columnName, "new " + entityClass.getSimpleName());

        if (!ValueComparator.valuesEqual(expected, actual)) {
            throw new AssertionError(String.format(
                "New %s%s column '%s': expected <%s> but was <%s>",
                entityClass.getSimpleName(), getMatchDescription(), columnName, expected, actual));
        }
        return this;
    }

    /**
     * Returns to the parent AssertBuilder for further assertions.
     */
    public AssertBuilder<R> and() {
        return parent;
    }

    /**
     * Gets the raw row data map of the current matched row.
     */
    public Map<String, Object> getRowData() {
        return currentRow;
    }

    /**
     * Gets the count of new rows.
     */
    public int count() {
        return allNewRows.size();
    }

    // ==================== Private helper methods ====================

    private void ensureRowAvailable() {
        if (currentRow == null) {
            if (allNewRows.isEmpty()) {
                throw new AssertionError(String.format(
                    "No new rows found for %s", entityClass.getSimpleName()));
            } else {
                throw new AssertionError(String.format(
                    "No new %s row matched conditions: %s",
                    entityClass.getSimpleName(), matchConditions));
            }
        }
    }

    private boolean rowMatchesAllConditions(Map<String, Object> row) {
        for (Map.Entry<String, Object> condition : matchConditions.entrySet()) {
            Object actualValue = ColumnNameResolver.getValueCaseInsensitiveNullable(row, condition.getKey());
            if (!ValueComparator.valuesEqual(condition.getValue(), actualValue)) {
                return false;
            }
        }
        return true;
    }

    private String getMatchDescription() {
        if (matchConditions.isEmpty()) {
            return "";
        }
        return " matching " + matchConditions;
    }
}
