package com.flowtest.core.util;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Unified value comparison utility with type coercion support.
 * Handles BigDecimal precision, Number promotion, Enum/String interop.
 */
public final class ValueComparator {

    private ValueComparator() {
    }

    /**
     * Compares two values with type coercion for numbers and enums.
     *
     * <p>Comparison rules (in order):
     * <ol>
     *   <li>null == null → true; null vs non-null → false</li>
     *   <li>Either side is BigDecimal → compareTo (scale-insensitive)</li>
     *   <li>Both Numbers → doubleValue comparison</li>
     *   <li>Both Enums → identity comparison</li>
     *   <li>String vs Enum → compare with enum.name()</li>
     *   <li>Fallback → Objects.equals()</li>
     * </ol>
     */
    public static boolean valuesEqual(Object expected, Object actual) {
        if (expected == null && actual == null) {
            return true;
        }
        if (expected == null || actual == null) {
            return false;
        }

        // Handle BigDecimal comparison (scale-insensitive)
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

    /**
     * Converts a value to BigDecimal if possible.
     *
     * @param value the value to convert
     * @return BigDecimal representation, or null if conversion is not possible
     */
    public static BigDecimal toBigDecimal(Object value) {
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
