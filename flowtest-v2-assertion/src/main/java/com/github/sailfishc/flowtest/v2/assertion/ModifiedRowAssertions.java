package com.github.sailfishc.flowtest.v2.assertion;

import com.github.sailfishc.flowtest.v2.spec.ModifiedRow;

import java.util.Arrays;

/**
 * Small helper DSL for modified-row assertions.
 */
public final class ModifiedRowAssertions {

    private ModifiedRowAssertions() {
    }

    public static ModifiedRowAssertion before(final RowAssertion assertion) {
        return new ModifiedRowAssertion() {
            @Override
            public void verify(ModifiedRow row) {
                assertion.verify(row.getBefore());
            }
        };
    }

    public static ModifiedRowAssertion after(final RowAssertion assertion) {
        return new ModifiedRowAssertion() {
            @Override
            public void verify(ModifiedRow row) {
                assertion.verify(row.getAfter());
            }
        };
    }

    public static ModifiedRowAssertion changed(final String columnName, final Object beforeValue, final Object afterValue) {
        return allOf(
            before(RowAssertions.columnEquals(columnName, beforeValue)),
            after(RowAssertions.columnEquals(columnName, afterValue))
        );
    }

    public static ModifiedRowAssertion allOf(final ModifiedRowAssertion... assertions) {
        return new ModifiedRowAssertion() {
            @Override
            public void verify(ModifiedRow row) {
                for (ModifiedRowAssertion assertion : Arrays.asList(assertions)) {
                    assertion.verify(row);
                }
            }
        };
    }
}
