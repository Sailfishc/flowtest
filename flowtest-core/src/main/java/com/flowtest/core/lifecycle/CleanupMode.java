package com.flowtest.core.lifecycle;

/**
 * Defines the cleanup mode for test data after test execution.
 */
public enum CleanupMode {

    /**
     * Transaction-based cleanup (L1).
     * Test runs in a transaction that is rolled back after completion.
     * This is the fastest and most reliable cleanup method.
     */
    TRANSACTION,

    /**
     * Compensating cleanup (L2).
     * Physically deletes all data created during the test.
     * Use when you need to verify committed transactions or
     * when dealing with async/parallel operations.
     */
    COMPENSATING,

    /**
     * No cleanup.
     * Data remains in the database after the test.
     * Useful for debugging or manual inspection.
     */
    NONE
}
