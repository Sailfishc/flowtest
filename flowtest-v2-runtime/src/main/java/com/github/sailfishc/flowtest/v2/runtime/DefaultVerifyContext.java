package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.fixture.FixtureExecution;
import com.github.sailfishc.flowtest.v2.fixture.FixtureStateMetadata;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.ModifiedRow;
import com.github.sailfishc.flowtest.v2.spec.ObservationDiff;
import com.github.sailfishc.flowtest.v2.spec.ResourceChange;
import com.github.sailfishc.flowtest.v2.spec.RowSnapshot;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

            @Override
            public void matchesAfter(FixtureStatePatch<T> patch) throws Exception {
                assertMatchesAfter(handle, patch, before(), after(), fixtureExecution.describe(handle));
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

    private static <T> void assertMatchesAfter(FixtureHandle<T> handle,
                                               FixtureStatePatch<T> patch,
                                               T before,
                                               T after,
                                               FixtureStateMetadata metadata) {
        if (patch == null) {
            throw new IllegalArgumentException("patch must not be null");
        }
        if (!handle.getType().equals(patch.getEntityType())) {
            throw new IllegalArgumentException("Patch type " + patch.getEntityType().getName()
                + " does not match fixture type " + handle.getType().getName());
        }
        Map<String, PropertyDescriptor> properties = beanProperties(handle.getType());
        validateReferencedProperties(handle, patch, properties, metadata.getComparableProperties());

        List<String> mismatches = new ArrayList<String>();
        Set<String> comparableProperties = metadata.getComparableProperties();
        for (String propertyName : comparableProperties) {
            if (patch.getIgnoredProperties().contains(propertyName)) {
                continue;
            }
            PropertyDescriptor descriptor = properties.get(propertyName);
            Object expected = patch.getExpectedValues().containsKey(propertyName)
                ? patch.getExpectedValues().get(propertyName)
                : readProperty(before, descriptor);
            Object actual = readProperty(after, descriptor);
            if (!Objects.deepEquals(expected, actual)) {
                mismatches.add(propertyName + " expected <" + expected + "> but was <" + actual + ">");
            }
        }
        if (!mismatches.isEmpty()) {
            throw new AssertionError("Fixture " + handle.identifier()
                + " after-state did not match expected values: " + mismatches);
        }
    }

    private static <T> void validateReferencedProperties(FixtureHandle<T> handle,
                                                         FixtureStatePatch<T> patch,
                                                         Map<String, PropertyDescriptor> properties,
                                                         Set<String> comparableProperties) {
        for (String propertyName : patch.getExpectedValues().keySet()) {
            if (!properties.containsKey(propertyName)) {
                throw new IllegalArgumentException("Unknown fixture property " + handle.getType().getName()
                    + "." + propertyName);
            }
            if (!comparableProperties.contains(propertyName)) {
                throw new IllegalArgumentException("Property " + handle.getType().getName() + "." + propertyName
                    + " is not part of the comparable persisted state");
            }
        }
        for (String propertyName : patch.getIgnoredProperties()) {
            if (!properties.containsKey(propertyName)) {
                throw new IllegalArgumentException("Unknown fixture property " + handle.getType().getName()
                    + "." + propertyName);
            }
        }
    }

    private static Map<String, PropertyDescriptor> beanProperties(Class<?> entityType) {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(entityType, Object.class);
            Map<String, PropertyDescriptor> properties = new LinkedHashMap<String, PropertyDescriptor>();
            for (PropertyDescriptor descriptor : beanInfo.getPropertyDescriptors()) {
                if (descriptor.getReadMethod() == null || descriptor.getWriteMethod() == null) {
                    continue;
                }
                properties.put(descriptor.getName(), descriptor);
            }
            return properties;
        } catch (IntrospectionException ex) {
            throw new IllegalArgumentException("Failed to inspect bean properties for " + entityType.getName(), ex);
        }
    }

    private static Object readProperty(Object target, PropertyDescriptor descriptor) {
        try {
            Method readMethod = descriptor.getReadMethod();
            readMethod.setAccessible(true);
            return readMethod.invoke(target);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read property " + descriptor.getName()
                + " from " + target.getClass().getName(), ex);
        }
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
