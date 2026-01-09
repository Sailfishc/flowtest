package com.flowtest.core.assertion;

import com.flowtest.core.FlowTestException;
import com.flowtest.core.TestContext;
import com.flowtest.core.ThrowingSupplier;
import com.flowtest.core.persistence.EntityPersister;
import com.flowtest.core.snapshot.SnapshotDiff;
import com.flowtest.core.snapshot.SnapshotEngine;
import com.flowtest.core.snapshot.TableSnapshot;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Represents the Assert phase of testing.
 * Provides fluent assertions for results, exceptions, and database changes.
 *
 * @param <T> the result type from the act phase
 */
public class AssertPhase<T> {

    private final TestContext context;
    private final EntityPersister persister;
    private final ThrowingSupplier<T> action;
    private SnapshotEngine snapshotEngine;

    private boolean executed = false;
    private T result;
    private Throwable thrownException;

    public AssertPhase(TestContext context, EntityPersister persister, ThrowingSupplier<T> action) {
        this.context = context;
        this.persister = persister;
        this.action = action;
    }

    /**
     * Sets the snapshot engine for database assertions.
     * This is typically set by the framework infrastructure.
     */
    public AssertPhase<T> withSnapshotEngine(SnapshotEngine snapshotEngine) {
        this.snapshotEngine = snapshotEngine;
        return this;
    }

    /**
     * Returns the assertion builder for making assertions.
     */
    public AssertBuilder<T> assertThat() {
        ensureExecuted();
        return new AssertBuilder<>(this, context);
    }

    /**
     * Ensures the action has been executed.
     */
    void ensureExecuted() {
        if (executed) {
            return;
        }

        // Take before snapshot
        if (snapshotEngine != null && !context.getWatchedTables().isEmpty()) {
            Map<String, TableSnapshot> beforeSnapshot = snapshotEngine.takeBeforeSnapshot(context.getWatchedTables());
            context.setBeforeSnapshot(beforeSnapshot);
        }

        // Execute the action
        try {
            result = action.get();
            context.setActResult(result);
        } catch (Throwable t) {
            thrownException = t;
            context.setThrownException(t);
        }

        executed = true;
    }

    /**
     * Takes the after snapshot and computes the diff.
     */
    void computeSnapshotDiff() {
        if (snapshotEngine == null || context.getBeforeSnapshot() == null) {
            return;
        }

        Map<String, TableSnapshot> afterSnapshot = snapshotEngine.takeAfterSnapshot(context.getWatchedTables());
        context.setAfterSnapshot(afterSnapshot);

        SnapshotDiff diff = snapshotEngine.computeDiff(context.getBeforeSnapshot(), afterSnapshot);
        context.setSnapshotDiff(diff);
    }

    // Accessors for AssertBuilder

    T getResult() {
        return result;
    }

    Throwable getThrownException() {
        return thrownException;
    }

    TestContext getContext() {
        return context;
    }

    SnapshotEngine getSnapshotEngine() {
        return snapshotEngine;
    }
}
