package com.flowtest.core.assertion;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Objects;
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
        String propertyName = extractPropertyName(getter);

        if (!valuesEqual(expected, actual)) {
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
     * Extracts the property name from a method reference.
     */
    private String extractPropertyName(SerializableFunction<T, ?> getter) {
        try {
            Method writeReplace = getter.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            SerializedLambda lambda = (SerializedLambda) writeReplace.invoke(getter);

            String methodName = lambda.getImplMethodName();
            // Convert getXxx to xxx
            if (methodName.startsWith("get") && methodName.length() > 3) {
                return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
            } else if (methodName.startsWith("is") && methodName.length() > 2) {
                return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
            }
            return methodName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Compares values with type coercion for numbers.
     */
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

    /**
     * A serializable Function interface for extracting method references.
     */
    @FunctionalInterface
    public interface SerializableFunction<T, R> extends Function<T, R>, Serializable {
    }
}
