package com.flowtest.core.lifecycle;

import com.flowtest.core.TestContext;

/**
 * No-op cleanup strategy that leaves test data in place.
 * Useful for debugging or manual inspection of test results.
 */
public class NoOpCleanup implements CleanupStrategy {

    @Override
    public void beforeTest(TestContext context) {
        // No-op
    }

    @Override
    public void afterTest(TestContext context) {
        // No-op - data remains in database
    }

    @Override
    public CleanupMode getMode() {
        return CleanupMode.NONE;
    }
}
