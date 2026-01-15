package com.flowtest.core.assertion;

import com.flowtest.core.FlowTestException;
import com.flowtest.core.TestContext;
import com.flowtest.core.fixture.EntityMetadata;
import com.flowtest.core.snapshot.SnapshotDiff;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

    // ==================== Shortcut assertion methods ====================

    /**
     * Asserts that 1 row was created in the table corresponding to the entity class.
     *
     * <p>Example:
     * <pre>{@code
     * .assertThat()
     *     .noException()
     *     .created(Order.class)  // equivalent to: .dbChanges(db -> db.table("t_order").hasNewRows(1))
     * }</pre>
     *
     * @param entityClass the entity class (table name is auto-resolved)
     * @return this builder
     */
    public AssertBuilder<T> created(Class<?> entityClass) {
        return created(entityClass, 1);
    }

    /**
     * Asserts that N rows were created in the table corresponding to the entity class.
     *
     * @param entityClass the entity class
     * @param count expected number of new rows
     * @return this builder
     */
    public AssertBuilder<T> created(Class<?> entityClass, int count) {
        String tableName = resolveTableName(entityClass);
        return dbChanges(db -> db.table(tableName).hasNewRows(count));
    }

    /**
     * Asserts that at least 1 row was modified in the table corresponding to the entity class.
     *
     * @param entityClass the entity class
     * @return this builder
     */
    public AssertBuilder<T> modified(Class<?> entityClass) {
        return modified(entityClass, 1);
    }

    /**
     * Asserts that N rows were modified in the table corresponding to the entity class.
     *
     * @param entityClass the entity class
     * @param count expected number of modified rows
     * @return this builder
     */
    public AssertBuilder<T> modified(Class<?> entityClass, int count) {
        String tableName = resolveTableName(entityClass);
        return dbChanges(db -> db.table(tableName).hasModifiedRows(count));
    }

    /**
     * Asserts that 1 row was deleted from the table corresponding to the entity class.
     *
     * @param entityClass the entity class
     * @return this builder
     */
    public AssertBuilder<T> deleted(Class<?> entityClass) {
        return deleted(entityClass, 1);
    }

    /**
     * Asserts that N rows were deleted from the table corresponding to the entity class.
     *
     * @param entityClass the entity class
     * @param count expected number of deleted rows
     * @return this builder
     */
    public AssertBuilder<T> deleted(Class<?> entityClass, int count) {
        String tableName = resolveTableName(entityClass);
        return dbChanges(db -> db.table(tableName).hasDeletedRows(count));
    }

    /**
     * Asserts that the table corresponding to the entity class has no changes.
     *
     * @param entityClass the entity class
     * @return this builder
     */
    public AssertBuilder<T> unchanged(Class<?> entityClass) {
        String tableName = resolveTableName(entityClass);
        return dbChanges(db -> db.table(tableName).hasNoChanges());
    }

    /**
     * Asserts that only the specified entity tables have changes, all other watched tables are unchanged.
     *
     * <p>Example:
     * <pre>{@code
     * .assertThat()
     *     .noException()
     *     .onlyChanged(Order.class, User.class)  // only t_order and t_user changed, others unchanged
     * }</pre>
     *
     * @param entityClasses the entity classes that are expected to have changes
     * @return this builder
     */
    public AssertBuilder<T> onlyChanged(Class<?>... entityClasses) {
        // Use case-insensitive comparison (H2 uses uppercase, entity metadata returns lowercase)
        Set<String> changedTables = Arrays.stream(entityClasses)
            .map(this::resolveTableName)
            .map(String::toUpperCase)
            .collect(Collectors.toSet());

        phase.computeSnapshotDiff();
        SnapshotDiff diff = context.getSnapshotDiff();
        if (diff == null) {
            throw new FlowTestException("No snapshot diff available.");
        }

        return dbChanges(db -> {
            for (String table : context.getWatchedTables()) {
                if (!changedTables.contains(table.toUpperCase())) {
                    db.table(table).hasNoChanges();
                }
            }
        });
    }

    /**
     * Asserts that all watched tables have no changes.
     *
     * @return this builder
     */
    public AssertBuilder<T> noDatabaseChanges() {
        phase.computeSnapshotDiff();
        SnapshotDiff diff = context.getSnapshotDiff();
        if (diff == null) {
            throw new FlowTestException("No snapshot diff available.");
        }

        return dbChanges(db -> {
            for (String table : context.getWatchedTables()) {
                db.table(table).hasNoChanges();
            }
        });
    }

    /**
     * Resolves the table name for an entity class.
     */
    private String resolveTableName(Class<?> entityClass) {
        return new EntityMetadata(entityClass).getTableName();
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

        // ===== Shortcut assertion methods for ExceptionAssert =====

        /**
         * Asserts that 1 row was created (chained from exception assertion).
         */
        public AssertBuilder<T> created(Class<?> entityClass) {
            return parent.created(entityClass);
        }

        /**
         * Asserts that N rows were created (chained from exception assertion).
         */
        public AssertBuilder<T> created(Class<?> entityClass, int count) {
            return parent.created(entityClass, count);
        }

        /**
         * Asserts that the table has no changes (chained from exception assertion).
         */
        public AssertBuilder<T> unchanged(Class<?> entityClass) {
            return parent.unchanged(entityClass);
        }

        /**
         * Asserts that at least 1 row was modified (chained from exception assertion).
         */
        public AssertBuilder<T> modified(Class<?> entityClass) {
            return parent.modified(entityClass);
        }

        /**
         * Asserts that N rows were modified (chained from exception assertion).
         */
        public AssertBuilder<T> modified(Class<?> entityClass, int count) {
            return parent.modified(entityClass, count);
        }

        /**
         * Asserts that 1 row was deleted (chained from exception assertion).
         */
        public AssertBuilder<T> deleted(Class<?> entityClass) {
            return parent.deleted(entityClass);
        }

        /**
         * Asserts that N rows were deleted (chained from exception assertion).
         */
        public AssertBuilder<T> deleted(Class<?> entityClass, int count) {
            return parent.deleted(entityClass, count);
        }

        /**
         * Asserts that only specified tables have changes (chained from exception assertion).
         */
        public AssertBuilder<T> onlyChanged(Class<?>... entityClasses) {
            return parent.onlyChanged(entityClasses);
        }

        /**
         * Asserts that all watched tables have no changes (chained from exception assertion).
         */
        public AssertBuilder<T> noDatabaseChanges() {
            return parent.noDatabaseChanges();
        }
    }
}
