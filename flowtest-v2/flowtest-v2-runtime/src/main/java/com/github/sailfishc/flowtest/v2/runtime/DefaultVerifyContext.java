package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.fixture.FixtureExecution;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.ModifiedRow;
import com.github.sailfishc.flowtest.v2.spec.ObservationDiff;
import com.github.sailfishc.flowtest.v2.spec.ResourceChange;
import com.github.sailfishc.flowtest.v2.spec.RowSnapshot;

import java.util.List;

final class DefaultVerifyContext<R> implements VerifyContext<R> {

    private final R result;
    private final Exception failure;
    private final ObservationDiff diff;
    private final FixtureExecution fixtureExecution;
    private boolean outcomeVerified;

    DefaultVerifyContext(R result, Exception failure, ObservationDiff diff, FixtureExecution fixtureExecution) {
        this.result = result;
        this.failure = failure;
        this.diff = diff;
        this.fixtureExecution = fixtureExecution;
    }

    @Override
    public R result() {
        return result;
    }

    @Override
    public Exception failure() {
        return failure;
    }

    @Override
    public void success() {
        outcomeVerified = true;
        if (failure != null) {
            throw new AssertionError("Expected no exception but got " + failure.getClass().getName(), failure);
        }
    }

    @Override
    public void failure(Class<? extends Throwable> exceptionType) {
        outcomeVerified = true;
        if (failure == null) {
            throw new AssertionError("Expected exception of type " + exceptionType.getName() + " but nothing was thrown");
        }
        if (!exceptionType.isInstance(failure)) {
            throw new AssertionError("Expected exception of type " + exceptionType.getName()
                + " but got " + failure.getClass().getName(), failure);
        }
    }

    @Override
    public ObservationDiff diff() {
        return diff;
    }

    @Override
    public <T> FixtureVerifyContext<T> fixture(final FixtureHandle<T> handle) {
        return new FixtureVerifyContext<T>() {
            @Override
            public T before() {
                return fixtureExecution.resolve(handle);
            }

            @Override
            public T after() throws Exception {
                return fixtureExecution.reload(handle);
            }
        };
    }

    @Override
    public ResourceVerifyContext resource(String resourceName) {
        ResourceChange change = diff.getChange(resourceName);
        if (change == null) {
            throw new AssertionError("No observed resource named " + resourceName);
        }
        return new DefaultResourceVerifyContext(change);
    }

    @Override
    public ResourceVerifyContext table(String tableName) {
        return resource(tableName);
    }

    @Override
    public ResourceVerifyContext entity(Class<?> entityType) {
        return resource(entityType.getName());
    }

    boolean isOutcomeVerified() {
        return outcomeVerified;
    }

    private static final class DefaultResourceVerifyContext implements ResourceVerifyContext {

        private final ResourceChange change;

        private DefaultResourceVerifyContext(ResourceChange change) {
            this.change = change;
        }

        @Override
        public String name() {
            return change.getResourceName();
        }

        @Override
        public long insertedCount() {
            return change.getInsertedCount();
        }

        @Override
        public long deletedCount() {
            return change.getDeletedCount();
        }

        @Override
        public long modifiedCount() {
            return change.getModifiedCount();
        }

        @Override
        public List<RowSnapshot> insertedRows() {
            return change.getInsertedRows();
        }

        @Override
        public List<RowSnapshot> deletedRows() {
            return change.getDeletedRows();
        }

        @Override
        public List<ModifiedRow> modifiedRows() {
            return change.getModifiedRows();
        }

        @Override
        public RowSnapshot insertedOne() {
            return requireSingle("inserted", change.getInsertedRows());
        }

        @Override
        public RowSnapshot deletedOne() {
            return requireSingle("deleted", change.getDeletedRows());
        }

        @Override
        public ModifiedRow modifiedOne() {
            return requireSingle("modified", change.getModifiedRows());
        }

        @Override
        public ResourceChange raw() {
            return change;
        }

        private static <T> T requireSingle(String rowType, List<T> rows) {
            if (rows.size() != 1) {
                throw new AssertionError("Expected exactly one " + rowType + " row but got " + rows.size());
            }
            return rows.get(0);
        }
    }
}
