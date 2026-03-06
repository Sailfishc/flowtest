package com.github.sailfishc.flowtest.v2.assertion;

import com.github.sailfishc.flowtest.v2.spec.RowSnapshot;

/**
 * Assertion contract for a single observed row.
 */
public interface RowAssertion {

    void verify(RowSnapshot row);
}
