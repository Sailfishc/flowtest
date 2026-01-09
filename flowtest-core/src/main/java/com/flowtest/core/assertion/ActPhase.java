package com.flowtest.core.assertion;

import com.flowtest.core.TestContext;
import com.flowtest.core.ThrowingRunnable;
import com.flowtest.core.ThrowingSupplier;
import com.flowtest.core.persistence.EntityPersister;
import com.flowtest.core.snapshot.SnapshotEngine;

/**
 * Represents the Act phase of testing.
 * Executes the business logic and captures results/exceptions.
 *
 * <p>Example usage:
 * <pre>{@code
 * flow.arrange()
 *     ...
 *     .persist()
 *     .act(() -> orderService.createOrder(userId, productId))
 *     .assertThat()
 *     ...
 * }</pre>
 */
public class ActPhase {

    private final TestContext context;
    private final EntityPersister persister;
    private final SnapshotEngine snapshotEngine;

    public ActPhase(TestContext context, EntityPersister persister) {
        this(context, persister, null);
    }

    public ActPhase(TestContext context, EntityPersister persister, SnapshotEngine snapshotEngine) {
        this.context = context;
        this.persister = persister;
        this.snapshotEngine = snapshotEngine;
    }

    /**
     * Executes the given action and captures the result.
     *
     * @param action the action to execute
     * @param <T> the result type
     * @return the AssertPhase for assertions
     */
    public <T> AssertPhase<T> act(ThrowingSupplier<T> action) {
        AssertPhase<T> phase = new AssertPhase<>(context, persister, action);
        if (snapshotEngine != null) {
            phase.withSnapshotEngine(snapshotEngine);
        }
        return phase;
    }

    /**
     * Executes the given action (no return value).
     *
     * @param action the action to execute
     * @return the AssertPhase for assertions
     */
    public AssertPhase<Void> act(ThrowingRunnable action) {
        return act(() -> {
            action.run();
            return null;
        });
    }

    /**
     * Gets the test context.
     */
    public TestContext getContext() {
        return context;
    }
}
