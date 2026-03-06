package com.github.sailfishc.flowtest.v2.assertion;

import com.github.sailfishc.flowtest.v2.spec.RowSnapshot;

import java.util.Arrays;

/**
 * Small helper DSL for row-level assertions.
 */
public final class RowAssertions {

    private RowAssertions() {
    }

    public static RowAssertion columnEquals(final String columnName, final Object expectedValue) {
        return new RowAssertion() {
            @Override
            public void verify(RowSnapshot row) {
                Object actual = row.getColumn(columnName);
                if (expectedValue == null ? actual != null : !expectedValue.equals(actual)) {
                    throw new AssertionError("Expected column " + columnName + " to be " + expectedValue + " but was " + actual);
                }
            }
        };
    }

    public static RowAssertion columns(String firstColumn, Object firstValue, Object... remainingPairs) {
        if (remainingPairs.length % 2 != 0) {
            throw new IllegalArgumentException("remainingPairs must contain an even number of elements (key-value pairs)");
        }
        int pairCount = 1 + remainingPairs.length / 2;
        RowAssertion[] assertions = new RowAssertion[pairCount];
        assertions[0] = columnEquals(firstColumn, firstValue);
        for (int i = 0; i < remainingPairs.length; i += 2) {
            if (!(remainingPairs[i] instanceof String)) {
                throw new IllegalArgumentException("Column name at position " + (i + 2) + " must be a String");
            }
            assertions[1 + i / 2] = columnEquals((String) remainingPairs[i], remainingPairs[i + 1]);
        }
        return allOf(assertions);
    }

    public static RowAssertion allOf(final RowAssertion... assertions) {
        return new RowAssertion() {
            @Override
            public void verify(RowSnapshot row) {
                for (RowAssertion assertion : Arrays.asList(assertions)) {
                    assertion.verify(row);
                }
            }
        };
    }
}
