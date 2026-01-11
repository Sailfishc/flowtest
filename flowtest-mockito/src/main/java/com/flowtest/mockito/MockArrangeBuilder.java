package com.flowtest.mockito;

import com.flowtest.core.TestContext;
import com.flowtest.core.assertion.ActPhase;
import com.flowtest.core.fixture.ArrangeBuilder;
import com.flowtest.core.fixture.Trait;

/**
 * Wrapper around ArrangeBuilder that adds mock configuration support.
 * Provides fluent API for configuring mocks in the arrange phase.
 */
public class MockArrangeBuilder {

    private final ArrangeBuilder delegate;
    private final TestContext context;
    private MockContext mockContext;

    public MockArrangeBuilder(ArrangeBuilder delegate, TestContext context) {
        this.delegate = delegate;
        this.context = context;
    }

    /**
     * Enters mock configuration mode.
     * Call done() on the returned MockBuilder to return to this ArrangeBuilder.
     */
    public MockBuilderWithReturn withMocks() {
        if (mockContext == null) {
            mockContext = new MockContext();
            context.setMockContext(mockContext);
        }
        return new MockBuilderWithReturn(mockContext, this);
    }

    /**
     * MockBuilder that returns to MockArrangeBuilder when done.
     */
    public static class MockBuilderWithReturn extends MockBuilder {
        private final MockArrangeBuilder parent;

        public MockBuilderWithReturn(MockContext mockContext, MockArrangeBuilder parent) {
            super(mockContext, null);
            this.parent = parent;
        }

        /**
         * Completes mock configuration and returns to ArrangeBuilder.
         */
        public MockArrangeBuilder done() {
            return parent;
        }
    }

    /**
     * Adds an entity with the given traits.
     */
    @SafeVarargs
    public final <T> MockArrangeBuilder add(Class<T> entityClass, Trait<T>... traits) {
        delegate.add(entityClass, traits);
        return this;
    }

    /**
     * Adds an entity with an alias and the given traits.
     */
    @SafeVarargs
    public final <T> MockArrangeBuilder add(String alias, Class<T> entityClass, Trait<T>... traits) {
        delegate.add(alias, entityClass, traits);
        return this;
    }

    /**
     * Adds multiple entities of the same type.
     */
    @SafeVarargs
    public final <T> MockArrangeBuilder addMany(Class<T> entityClass, int count, Trait<T>... traits) {
        delegate.addMany(entityClass, count, traits);
        return this;
    }

    /**
     * Persists all entities and returns ActPhase wrapped for mock support.
     */
    public MockActPhase persist() {
        ActPhase actPhase = delegate.persist();
        return new MockActPhase(actPhase, context, mockContext);
    }

    /**
     * Builds entities without persisting.
     */
    public MockActPhase build() {
        ActPhase actPhase = delegate.build();
        return new MockActPhase(actPhase, context, mockContext);
    }

    /**
     * Gets the underlying ArrangeBuilder.
     */
    public ArrangeBuilder getDelegate() {
        return delegate;
    }
}
