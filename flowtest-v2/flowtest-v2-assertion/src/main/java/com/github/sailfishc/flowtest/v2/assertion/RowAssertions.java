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
