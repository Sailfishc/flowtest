package com.flowtest.core.lifecycle;

import com.flowtest.core.TestContext;

/**
 * Strategy interface for cleaning up test data after test execution.
 */
public interface CleanupStrategy {

    /**
     * Called before the test executes.
     * Can be used to set up transactions or prepare for cleanup.
     *
     * @param context the test context
     */
    void beforeTest(TestContext context);

    /**
     * Called after the test executes.
     * Performs the actual cleanup based on the strategy.
     *
     * @param context the test context
     */
    void afterTest(TestContext context);

    /**
     * Gets the cleanup mode this strategy implements.
     *
     * @return the cleanup mode
     */
    CleanupMode getMode();
}
