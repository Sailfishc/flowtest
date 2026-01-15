package com.flowtest.core.assertion;

import com.flowtest.core.assertion.ResultAssert.SerializableFunction;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Fluent assertion for new rows created during act() execution.
 * Data comes from SnapshotDiff's newRowsData - no extra database queries.
 *
 * <p>Example usage:
 * <pre>{@code
 * .assertThat()
 *     .newRow(Order.class)
 *         .has(Order::getStatus, OrderStatus.CREATED)
 *         .has("total_amount", BigDecimal.valueOf(180))
 *     .and()
 *     .modified(User.class);
 * }</pre>
 *
 * @param <E> the entity type
 * @param <R> the original act return type (for AssertBuilder)
 */
public class NewRowAssert<E, R> {

    private final AssertBuilder<R> parent;
    private final Map<String, Object> rowData;
    private final Class<E> entityClass;
    private final int index;

    public NewRowAssert(AssertBuilder<R> parent, Map<String, Object> rowData, Class<E> entityClass, int index) {
        this.parent = parent;
        this.rowData = rowData;
        this.entityClass = entityClass;
        this.index = index;
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
        if (rowData == null) {
            throw new AssertionError(String.format(
                "Cannot assert property on null row data. No new row at index %d for %s.",
                index, entityClass.getSimpleName()));
        }

        String columnName = extractColumnName(getter);
        Object actual = getValueCaseInsensitive(columnName);

        if (!valuesEqual(expected, actual)) {
            throw new AssertionError(String.format(
                "New %s[%d] column '%s': expected <%s> but was <%s>",
                entityClass.getSimpleName(), index, columnName, expected, actual));
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
        if (rowData == null) {
            throw new AssertionError(String.format(
                "Cannot assert property on null row data. No new row at index %d for %s.",
                index, entityClass.getSimpleName()));
        }

        Object actual = getValueCaseInsensitive(columnName);

        if (!valuesEqual(expected, actual)) {
            throw new AssertionError(String.format(
                "New %s[%d] column '%s': expected <%s> but was <%s>",
                entityClass.getSimpleName(), index, columnName, expected, actual));
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
     * Gets the raw row data map.
     */
    public Map<String, Object> getRowData() {
        return rowData;
    }

    /**
     * Gets value from row data with case-insensitive column name lookup.
     */
    private Object getValueCaseInsensitive(String columnName) {
        // Try exact match first
        if (rowData.containsKey(columnName)) {
            return rowData.get(columnName);
        }

        // Try case-insensitive match
        for (Map.Entry<String, Object> entry : rowData.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(columnName)) {
                return entry.getValue();
            }
        }

        throw new AssertionError(String.format(
            "Column '%s' not found in new %s[%d]. Available columns: %s",
            columnName, entityClass.getSimpleName(), index, rowData.keySet()));
    }

    /**
     * Extracts the column name from a method reference.
     */
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
