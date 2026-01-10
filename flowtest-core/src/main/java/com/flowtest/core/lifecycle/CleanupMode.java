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
     * Snapshot-based cleanup (L3).
     * Deletes all data created during persist() and act() phases.
     * Uses MAX(ID) snapshots to detect new rows created by business logic.
     * Use when transaction rollback is not available and you need to clean
     * both test fixtures and data produced by the system under test.
     */
    SNAPSHOT_BASED,

    /**
     * No cleanup.
     * Data remains in the database after the test.
     * Useful for debugging or manual inspection.
     */
    NONE
}
