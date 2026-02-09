package com.flowtest.core.assertion;

import com.flowtest.core.assertion.ResultAssert.SerializableFunction;
import com.flowtest.core.util.ColumnNameResolver;
import com.flowtest.core.util.ValueComparator;

import java.util.Map;

/**
 * Fluent assertion for entity state after act() execution.
 * Data comes from SnapshotDiff's RowModification.getAfterRow() - no extra database queries.
 *
 * <p>Example usage:
 * <pre>{@code
 * .assertThat()
 *     .entity(User.class)
 *         .has(User::getBalance, BigDecimal.valueOf(800))
 *         .has("level", "VIP")
 *     .and()
 *     .created(Order.class);
 * }</pre>
 *
 * @param <E> the entity type
 * @param <R> the original act return type (for AssertBuilder)
 */
public class EntityStateAssert<E, R> {

    private final AssertBuilder<R> parent;
    private final Map<String, Object> rowData;
    private final Class<E> entityClass;

    public EntityStateAssert(AssertBuilder<R> parent, Map<String, Object> rowData, Class<E> entityClass) {
        this.parent = parent;
        this.rowData = rowData;
        this.entityClass = entityClass;
    }

    /**
     * Asserts that a property of the entity equals the expected value.
     * Uses method reference for type-safe property access.
     *
     * @param getter the method reference to extract the property (e.g., User::getBalance)
     * @param expected the expected value
     * @param <V> the property value type
     * @return this for chaining
     */
    public <V> EntityStateAssert<E, R> has(SerializableFunction<E, V> getter, V expected) {
        if (rowData == null) {
            throw new AssertionError("Cannot assert property on null row data. " +
                "Entity may not have been modified during act().");
        }

        String columnName = ColumnNameResolver.extractColumnName(getter);
        Object actual = ColumnNameResolver.getValueCaseInsensitive(
            rowData, columnName, "entity " + entityClass.getSimpleName());

        if (!ValueComparator.valuesEqual(expected, actual)) {
            throw new AssertionError(String.format(
                "Entity %s column '%s': expected <%s> but was <%s>",
                entityClass.getSimpleName(), columnName, expected, actual));
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
    public EntityStateAssert<E, R> has(String columnName, Object expected) {
        if (rowData == null) {
            throw new AssertionError("Cannot assert property on null row data. " +
                "Entity may not have been modified during act().");
        }

        Object actual = ColumnNameResolver.getValueCaseInsensitive(
            rowData, columnName, "entity " + entityClass.getSimpleName());

        if (!ValueComparator.valuesEqual(expected, actual)) {
            throw new AssertionError(String.format(
                "Entity %s column '%s': expected <%s> but was <%s>",
                entityClass.getSimpleName(), columnName, expected, actual));
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
}
