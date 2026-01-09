package com.flowtest.core.lifecycle;

import com.flowtest.core.TestContext;

/**
 * Transaction-based cleanup strategy (L1).
 * This is a marker implementation - actual transaction management is handled
 * by Spring's TransactionalTestExecutionListener which wraps each test in a
 * transaction and rolls it back after completion.
 *
 * <p>This is the default and most efficient cleanup mode as it:
 * <ul>
 *   <li>Requires no additional cleanup logic</li>
 *   <li>Rolls back all changes atomically</li>
 *   <li>Handles all tables automatically</li>
 * </ul>
 *
 * <p>Note: This mode will NOT work for:
 * <ul>
 *   <li>Async operations that run in separate threads</li>
 *   <li>Operations using REQUIRES_NEW propagation</li>
 *   <li>External system calls</li>
 * </ul>
 * For those cases, use {@link CleanupMode#COMPENSATING}.
 */
public class TransactionalCleanup implements CleanupStrategy {

    @Override
    public void beforeTest(TestContext context) {
        // Transaction is managed by Spring's TransactionalTestExecutionListener
    }

    @Override
    public void afterTest(TestContext context) {
        // Rollback is handled by Spring's TransactionalTestExecutionListener
    }

    @Override
    public CleanupMode getMode() {
        return CleanupMode.TRANSACTION;
    }
}
