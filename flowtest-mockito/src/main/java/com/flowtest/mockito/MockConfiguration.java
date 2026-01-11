package com.flowtest.mockito;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.OngoingStubbing;

/**
 * Configuration for a single mock object.
 * Provides fluent API for stubbing mock behavior.
 *
 * @param <T> the type of the mock
 */
public class MockConfiguration<T> {

    private final MockBuilder parent;
    private final T mock;
    private final Class<T> mockClass;

    public MockConfiguration(MockBuilder parent, T mock, Class<T> mockClass) {
        this.parent = parent;
        this.mock = mock;
        this.mockClass = mockClass;
    }

    /**
     * Gets the underlying mock object.
     */
    public T getMock() {
        return mock;
    }

    /**
     * Gets the mock class.
     */
    public Class<T> getMockClass() {
        return mockClass;
    }

    /**
     * Starts stubbing a method call on this mock.
     *
     * @param methodCall a function that invokes the method to stub
     * @param <R>        the return type of the method
     * @return a StubbingChain for configuring the return value
     */
    public <R> StubbingChain<T, R> when(ThrowingFunction<T, R> methodCall) {
        try {
            R result = methodCall.apply(mock);
            @SuppressWarnings("unchecked")
            OngoingStubbing<R> stubbing = (OngoingStubbing<R>) Mockito.when(result);
            return new StubbingChain<>(this, stubbing);
        } catch (Throwable e) {
            throw new RuntimeException("Error setting up mock stubbing", e);
        }
    }

    /**
     * Starts stubbing a void method call on this mock.
     *
     * @param methodCall a consumer that invokes the void method to stub
     * @return a VoidStubbingChain for configuring the behavior
     */
    public VoidStubbingChain<T> whenVoid(ThrowingConsumer<T> methodCall) {
        return new VoidStubbingChain<>(this, methodCall);
    }

    /**
     * Returns to MockBuilder to configure another mock.
     */
    public MockBuilder and() {
        return parent;
    }

    /**
     * Functional interface for method calls that may throw exceptions.
     */
    @FunctionalInterface
    public interface ThrowingFunction<T, R> {
        R apply(T t) throws Throwable;
    }

    /**
     * Functional interface for void method calls that may throw exceptions.
     */
    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T t) throws Throwable;
    }
}
