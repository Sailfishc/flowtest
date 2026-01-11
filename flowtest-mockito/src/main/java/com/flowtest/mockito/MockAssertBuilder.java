package com.flowtest.mockito;

import com.flowtest.core.TestContext;
import com.flowtest.core.assertion.AssertBuilder;
import com.flowtest.core.assertion.DbChangesAssert;

import java.util.function.Consumer;

/**
 * Wrapper around AssertBuilder that adds mock verification support.
 *
 * @param <T> the result type
 */
public class MockAssertBuilder<T> {

    private final AssertBuilder<T> delegate;
    private final TestContext context;
    private final MockContext mockContext;

    public MockAssertBuilder(AssertBuilder<T> delegate, TestContext context, MockContext mockContext) {
        this.delegate = delegate;
        this.context = context;
        this.mockContext = mockContext;
    }

    /**
     * Asserts that no exception was thrown.
     */
    public MockAssertBuilder<T> noException() {
        delegate.noException();
        return this;
    }

    /**
     * Asserts that an exception of the given type was thrown.
     */
    public MockExceptionAssert<T> exception(Class<? extends Throwable> exceptionType) {
        AssertBuilder.ExceptionAssert<T> exAssert = delegate.exception(exceptionType);
        return new MockExceptionAssert<>(this, exAssert);
    }

    /**
     * Asserts the return value using the given consumer.
     */
    public MockAssertBuilder<T> returnValue(Consumer<T> asserter) {
        delegate.returnValue(asserter);
        return this;
    }

    /**
     * Gets the return value.
     */
    public T getResult() {
        return delegate.getResult();
    }

    /**
     * Asserts database changes.
     */
    public MockAssertBuilder<T> dbChanges(Consumer<DbChangesAssert> asserter) {
        delegate.dbChanges(asserter);
        return this;
    }

    /**
     * Enters mock verification mode.
     */
    public MockVerifier<T> mocks() {
        if (mockContext == null) {
            throw new IllegalStateException(
                "No mocks configured. Use withMocks() in arrange phase first.");
        }
        return new MockVerifier<>(mockContext, () -> {});
    }

    /**
     * Gets the underlying AssertBuilder.
     */
    public AssertBuilder<T> getDelegate() {
        return delegate;
    }
}
