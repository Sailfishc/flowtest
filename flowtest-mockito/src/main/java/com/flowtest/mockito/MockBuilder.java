package com.flowtest.mockito;

import org.mockito.Mockito;

/**
 * Builder for configuring mocks in the arrange phase.
 * Provides fluent API for creating and configuring mock objects.
 */
public class MockBuilder {

    private final MockContext mockContext;
    private final Runnable onDone;

    public MockBuilder(MockContext mockContext, Runnable onDone) {
        this.mockContext = mockContext;
        this.onDone = onDone;
    }

    /**
     * Creates and registers a mock of the given type.
     * Returns MockConfiguration for chained stubbing.
     */
    public <T> MockConfiguration<T> mock(Class<T> mockClass) {
        T mock = Mockito.mock(mockClass);
        mockContext.registerMock(mockClass, mock);
        return new MockConfiguration<>(this, mock, mockClass);
    }

    /**
     * Creates and registers a mock with an alias.
     * Returns MockConfiguration for chained stubbing.
     */
    public <T> MockConfiguration<T> mock(String alias, Class<T> mockClass) {
        T mock = Mockito.mock(mockClass);
        mockContext.registerMock(alias, mockClass, mock);
        return new MockConfiguration<>(this, mock, mockClass);
    }

    /**
     * Creates a mock and applies a MockTrait to configure it.
     * Returns MockBuilder for chaining more mocks.
     */
    public <T> MockBuilder mock(Class<T> mockClass, MockTrait<T> trait) {
        T mock = Mockito.mock(mockClass);
        mockContext.registerMock(mockClass, mock);
        MockConfiguration<T> config = new MockConfiguration<>(this, mock, mockClass);
        trait.apply(config);
        return this;
    }

    /**
     * Creates a mock with alias and applies a MockTrait.
     * Returns MockBuilder for chaining more mocks.
     */
    public <T> MockBuilder mock(String alias, Class<T> mockClass, MockTrait<T> trait) {
        T mock = Mockito.mock(mockClass);
        mockContext.registerMock(alias, mockClass, mock);
        MockConfiguration<T> config = new MockConfiguration<>(this, mock, mockClass);
        trait.apply(config);
        return this;
    }

    /**
     * Registers a pre-configured mock object.
     */
    @SuppressWarnings("unchecked")
    public <T> MockBuilder register(T preConfiguredMock) {
        Class<T> mockClass = (Class<T>) preConfiguredMock.getClass().getInterfaces()[0];
        mockContext.registerMock(mockClass, preConfiguredMock);
        return this;
    }

    /**
     * Registers a pre-configured mock with an alias.
     */
    @SuppressWarnings("unchecked")
    public <T> MockBuilder register(String alias, T preConfiguredMock) {
        Class<T> mockClass = (Class<T>) preConfiguredMock.getClass().getInterfaces()[0];
        mockContext.registerMock(alias, mockClass, preConfiguredMock);
        return this;
    }

    /**
     * Registers a pre-configured mock with explicit type.
     */
    public <T> MockBuilder register(Class<T> mockClass, T preConfiguredMock) {
        mockContext.registerMock(mockClass, preConfiguredMock);
        return this;
    }

    /**
     * Registers a pre-configured mock with alias and explicit type.
     */
    public <T> MockBuilder register(String alias, Class<T> mockClass, T preConfiguredMock) {
        mockContext.registerMock(alias, mockClass, preConfiguredMock);
        return this;
    }

    /**
     * Creates a spy on an existing object.
     * Returns MockConfiguration for chained stubbing.
     */
    @SuppressWarnings("unchecked")
    public <T> MockConfiguration<T> spy(T realObject) {
        T spy = Mockito.spy(realObject);
        Class<T> type = (Class<T>) realObject.getClass();
        mockContext.registerMock(type, spy);
        return new MockConfiguration<>(this, spy, type);
    }

    /**
     * Creates a spy with an alias.
     */
    @SuppressWarnings("unchecked")
    public <T> MockConfiguration<T> spy(String alias, T realObject) {
        T spy = Mockito.spy(realObject);
        Class<T> type = (Class<T>) realObject.getClass();
        mockContext.registerMock(alias, type, spy);
        return new MockConfiguration<>(this, spy, type);
    }

    /**
     * Creates a spy and applies a MockTrait.
     */
    @SuppressWarnings("unchecked")
    public <T> MockBuilder spy(T realObject, MockTrait<T> trait) {
        T spy = Mockito.spy(realObject);
        Class<T> type = (Class<T>) realObject.getClass();
        mockContext.registerMock(type, spy);
        MockConfiguration<T> config = new MockConfiguration<>(this, spy, type);
        trait.apply(config);
        return this;
    }

    /**
     * Gets the MockContext.
     */
    public MockContext getMockContext() {
        return mockContext;
    }

    /**
     * Completes mock configuration.
     * Subclasses may override to return a specific type.
     */
    public Object done() {
        if (onDone != null) {
            onDone.run();
        }
        return null;
    }
}
