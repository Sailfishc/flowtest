package com.flowtest.core.assertion;

import com.flowtest.core.FlowTestException;
import com.flowtest.core.TestContext;
import com.flowtest.core.snapshot.SnapshotDiff;

import java.util.function.Consumer;

/**
 * Builder for assertions in the Assert phase.
 * Provides fluent API for asserting results, exceptions, and database changes.
 *
 * @param <T> the result type
 */
public class AssertBuilder<T> {

    private final AssertPhase<T> phase;
    private final TestContext context;
    private boolean expectNoException = false;
    private Class<? extends Throwable> expectedExceptionType;
    private String expectedExceptionMessage;
    private boolean exceptionAsserted = false;

    public AssertBuilder(AssertPhase<T> phase, TestContext context) {
        this.phase = phase;
        this.context = context;
    }

    /**
     * Asserts that no exception was thrown.
     */
    public AssertBuilder<T> noException() {
        Throwable thrown = phase.getThrownException();
        if (thrown != null) {
            throw new AssertionError("Expected no exception, but got: " + thrown.getClass().getName() +
                " - " + thrown.getMessage(), thrown);
        }
        expectNoException = true;
        return this;
    }

    /**
     * Asserts that an exception of the given type was thrown.
     */
    public ExceptionAssert<T> exception(Class<? extends Throwable> exceptionType) {
        exceptionAsserted = true;
        Throwable thrown = phase.getThrownException();

        if (thrown == null) {
            throw new AssertionError("Expected exception of type " + exceptionType.getName() +
                ", but no exception was thrown");
        }

        if (!exceptionType.isInstance(thrown)) {
            throw new AssertionError("Expected exception of type " + exceptionType.getName() +
                ", but got: " + thrown.getClass().getName(), thrown);
        }

        return new ExceptionAssert<>(this, thrown);
    }

    /**
     * Asserts the return value using the given consumer.
     */
    public AssertBuilder<T> returnValue(Consumer<T> asserter) {
        // Ensure no exception was thrown if we're checking return value
        if (!exceptionAsserted && phase.getThrownException() != null) {
            throw new AssertionError("Cannot assert return value when exception was thrown: " +
                phase.getThrownException().getClass().getName(), phase.getThrownException());
        }

        T result = phase.getResult();
        asserter.accept(result);
        return this;
    }

    /**
     * Gets the return value.
     */
    public T getResult() {
        if (phase.getThrownException() != null) {
            throw new AssertionError("Cannot get result when exception was thrown: " +
                phase.getThrownException().getClass().getName(), phase.getThrownException());
        }
        return phase.getResult();
    }

    /**
     * Asserts database changes using the given consumer.
     * This uses the built-in snapshot diff mechanism.
     */
    public AssertBuilder<T> dbChanges(Consumer<DbChangesAssert> asserter) {
        // Compute snapshot diff
        phase.computeSnapshotDiff();

        SnapshotDiff diff = context.getSnapshotDiff();
        if (diff == null) {
            throw new FlowTestException("No snapshot diff available. " +
                "Make sure SnapshotEngine is configured and tables are being watched.");
        }

        DbChangesAssert dbAssert = new DbChangesAssert(diff);
        asserter.accept(dbAssert);
        dbAssert.verify();

        return this;
    }

    /**
     * Gets the test context.
     */
    public TestContext getContext() {
        return context;
    }

    /**
     * Exception assertion helper.
     */
    public static class ExceptionAssert<T> {

        private final AssertBuilder<T> parent;
        private final Throwable exception;

        public ExceptionAssert(AssertBuilder<T> parent, Throwable exception) {
            this.parent = parent;
            this.exception = exception;
        }

        /**
         * Asserts that the exception message contains the given text.
         */
        public ExceptionAssert<T> hasMessageContaining(String text) {
            String message = exception.getMessage();
            if (message == null || !message.contains(text)) {
                throw new AssertionError("Expected exception message to contain '" + text +
                    "', but was: " + message);
            }
            return this;
        }

        /**
         * Asserts that the exception message equals the given text.
         */
        public ExceptionAssert<T> hasMessage(String text) {
            String message = exception.getMessage();
            if (message == null ? text != null : !message.equals(text)) {
                throw new AssertionError("Expected exception message '" + text +
                    "', but was: " + message);
            }
            return this;
        }

        /**
         * Asserts the exception using a custom consumer.
         */
        public ExceptionAssert<T> satisfies(Consumer<Throwable> asserter) {
            asserter.accept(exception);
            return this;
        }

        /**
         * Returns to the parent AssertBuilder for further assertions.
         */
        public AssertBuilder<T> and() {
            return parent;
        }

        /**
         * Asserts database changes (chained from exception assertion).
         */
        public AssertBuilder<T> dbChanges(Consumer<DbChangesAssert> asserter) {
            return parent.dbChanges(asserter);
        }
    }
}
