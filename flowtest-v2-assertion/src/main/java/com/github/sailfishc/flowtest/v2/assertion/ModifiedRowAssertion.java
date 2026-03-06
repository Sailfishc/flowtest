package com.github.sailfishc.flowtest.v2.assertion;

import com.github.sailfishc.flowtest.v2.spec.ModifiedRow;

/**
 * Assertion contract for a modified row with before and after images.
 */
public interface ModifiedRowAssertion {

    void verify(ModifiedRow row);
}
