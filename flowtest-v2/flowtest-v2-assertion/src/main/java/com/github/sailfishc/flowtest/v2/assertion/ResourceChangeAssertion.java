package com.github.sailfishc.flowtest.v2.assertion;

import com.github.sailfishc.flowtest.v2.spec.ResourceChange;

/**
 * Assertion contract for a full resource-level diff.
 */
public interface ResourceChangeAssertion {

    void verify(ResourceChange change);
}
