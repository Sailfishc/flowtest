package com.flowtest.core.assertion;

import com.flowtest.core.assertion.ResultAssert.SerializableFunction;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
     * <p>Example:
     * <pre>{@code
     * .newRow(Order.class)
     *     .matching("user_id", userId)
     *     .matching("product_id", productId)
     *     .has("status", "CREATED")
     * }</pre>
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

        if (matchedRows.size() > 1) {
            // Multiple matches - keep first one but continue (user might add more conditions)
            currentRow = matchedRows.get(0);
        } else {
            currentRow = matchedRows.get(0);
        }

        return this;
    }

    /**
     * Locates a new row by matching a property using method reference.
     *
     * <p>Example:
     * <pre>{@code
     * .newRow(Order.class)
     *     .matching(Order::getUserId, userId)
     *     .has(Order::getStatus, OrderStatus.CREATED)
     * }</pre>
     *
     * @param getter the method reference to extract the property
     * @param value the expected value
     * @param <V> the property type
     * @return this for chaining
     */
    public <V> NewRowAssert<E, R> matching(SerializableFunction<E, V> getter, V value) {
        String columnName = extractColumnName(getter);
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

        String columnName = extractColumnName(getter);
        Object actual = getValueCaseInsensitive(currentRow, columnName);

        if (!valuesEqual(expected, actual)) {
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

        Object actual = getValueCaseInsensitive(currentRow, columnName);

        if (!valuesEqual(expected, actual)) {
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
            Object actualValue = getValueCaseInsensitiveNullable(row, condition.getKey());
            if (!valuesEqual(condition.getValue(), actualValue)) {
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

    private Object getValueCaseInsensitive(Map<String, Object> row, String columnName) {
        Object value = getValueCaseInsensitiveNullable(row, columnName);
        if (value == null && !containsColumnCaseInsensitive(row, columnName)) {
            throw new AssertionError(String.format(
                "Column '%s' not found in new %s. Available columns: %s",
                columnName, entityClass.getSimpleName(), row.keySet()));
        }
        return value;
    }

    private Object getValueCaseInsensitiveNullable(Map<String, Object> row, String columnName) {
        // Try exact match first
        if (row.containsKey(columnName)) {
            return row.get(columnName);
        }

        // Try case-insensitive match
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(columnName)) {
                return entry.getValue();
            }
        }

        return null;
    }

    private boolean containsColumnCaseInsensitive(Map<String, Object> row, String columnName) {
        if (row.containsKey(columnName)) {
            return true;
        }
        for (String key : row.keySet()) {
            if (key.equalsIgnoreCase(columnName)) {
                return true;
            }
        }
        return false;
    }

    private String extractColumnName(SerializableFunction<E, ?> getter) {
        try {
            Method writeReplace = getter.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            SerializedLambda lambda = (SerializedLambda) writeReplace.invoke(getter);

            String methodName = lambda.getImplMethodName();
            String fieldName;

            if (methodName.startsWith("get") && methodName.length() > 3) {
                fieldName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
            } else if (methodName.startsWith("is") && methodName.length() > 2) {
                fieldName = Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
            } else {
                fieldName = methodName;
            }

            return camelToSnake(fieldName);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String camelToSnake(String camelCase) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private boolean valuesEqual(Object expected, Object actual) {
        if (expected == null && actual == null) {
            return true;
        }
        if (expected == null || actual == null) {
            return false;
        }

        // Handle BigDecimal comparison
        if (expected instanceof BigDecimal || actual instanceof BigDecimal) {
            BigDecimal expectedBd = toBigDecimal(expected);
            BigDecimal actualBd = toBigDecimal(actual);
            if (expectedBd != null && actualBd != null) {
                return expectedBd.compareTo(actualBd) == 0;
            }
        }

        // Handle numeric comparison
        if (expected instanceof Number && actual instanceof Number) {
            return ((Number) expected).doubleValue() == ((Number) actual).doubleValue();
        }

        // Handle enum comparison
        if (expected instanceof Enum && actual instanceof Enum) {
            return expected == actual;
        }

        // String comparison for enums
        if (expected instanceof String && actual instanceof Enum) {
            return expected.equals(((Enum<?>) actual).name());
        }
        if (expected instanceof Enum && actual instanceof String) {
            return ((Enum<?>) expected).name().equals(actual);
        }

        return Objects.equals(expected, actual);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        } else if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        } else if (value instanceof String) {
            try {
                return new BigDecimal((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
