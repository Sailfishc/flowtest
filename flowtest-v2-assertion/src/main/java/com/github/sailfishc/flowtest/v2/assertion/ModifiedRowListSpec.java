package com.github.sailfishc.flowtest.v2.assertion;

/**
 * Collector DSL for multi-modified-row assertions with optional sorting and indexed access.
 */
public interface ModifiedRowListSpec {

    /**
     * Sort modified rows by the given column in the <em>after</em> image before applying indexed assertions.
     * Values must be {@link Comparable}; nulls sort first.
     */
    ModifiedRowListSpec sortBy(String column);

    /**
     * Assert a specific modified row by index (after sorting if {@link #sortBy} was called).
     */
    ModifiedRowListSpec row(int index, ModifiedRowAssertion assertion);

    /**
     * Assert that every modified row satisfies the given assertion.
     */
    ModifiedRowListSpec eachRow(ModifiedRowAssertion assertion);
}
