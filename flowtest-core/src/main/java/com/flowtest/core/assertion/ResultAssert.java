package com.flowtest.core.assertion;

import com.flowtest.core.util.ColumnNameResolver;
import com.flowtest.core.util.ValueComparator;

import java.io.Serializable;
import java.util.function.Function;

/**
 * Fluent assertion for act() return values.
 * Provides a concise way to assert properties of the returned object.
 *
 * <p>Example usage:
 * <pre>{@code
 * .assertThat()
 *     .result()
 *         .has(Order::getStatus, OrderStatus.CREATED)
 *         .has(Order::getTotalAmount, BigDecimal.valueOf(180))
 *     .and()
 *     .created(Order.class);
 * }</pre>
 *
 * @param <T> the result type
 * @param <R> the original act return type (for AssertBuilder)
 */
public class ResultAssert<T, R> {

    private final AssertBuilder<R> parent;
    private final T result;

    public ResultAssert(AssertBuilder<R> parent, T result) {
        this.parent = parent;
        this.result = result;
    }

    /**
     * Asserts that a property of the result equals the expected value.
     * Uses method reference for type-safe property access.
     *
     * @param getter the method reference to extract the property (e.g., Order::getStatus)
     * @param expected the expected value
     * @param <V> the property value type
     * @return this for chaining
     */
    public <V> ResultAssert<T, R> has(SerializableFunction<T, V> getter, V expected) {
        if (result == null) {
            throw new AssertionError("Cannot assert property on null result");
        }

        V actual = getter.apply(result);
        String propertyName = ColumnNameResolver.extractPropertyName(getter);

        if (!ValueComparator.valuesEqual(expected, actual)) {
            throw new AssertionError(String.format(
                "Result property '%s': expected <%s> but was <%s>",
                propertyName, expected, actual));
        }
        return this;
    }

    /**
     * Asserts that the result is not null.
     */
    public ResultAssert<T, R> isNotNull() {
        if (result == null) {
            throw new AssertionError("Expected result to be not null, but was null");
        }
        return this;
    }

    /**
     * Asserts that the result is null.
     */
    public ResultAssert<T, R> isNull() {
        if (result != null) {
            throw new AssertionError("Expected result to be null, but was: " + result);
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
     * Gets the result value.
     */
    public T get() {
        return result;
    }

    /**
     * A serializable Function interface for extracting method references.
     */
    @FunctionalInterface
    public interface SerializableFunction<T, R> extends Function<T, R>, Serializable {
    }
}
