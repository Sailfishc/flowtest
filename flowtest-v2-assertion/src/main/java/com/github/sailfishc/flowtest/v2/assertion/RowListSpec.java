package com.github.sailfishc.flowtest.v2.assertion;

/**
 * Collector DSL for multi-row assertions with optional sorting and indexed access.
 */
public interface RowListSpec {

    /**
     * Sort the rows by the given column before applying indexed assertions.
     * Values must be {@link Comparable}; nulls sort first.
     */
    RowListSpec sortBy(String column);

    /**
     * Assert a specific row by index (after sorting if {@link #sortBy} was called).
     */
    RowListSpec row(int index, RowAssertion assertion);

    /**
     * Assert that every row satisfies the given assertion.
     */
    RowListSpec eachRow(RowAssertion assertion);

    /**
     * Assert that at least one row satisfies the given assertion.
     */
    RowListSpec anyRow(RowAssertion assertion);
}
