package com.flowtest.mockito;

import com.flowtest.core.assertion.AssertBuilder;
import com.flowtest.core.assertion.DbChangesAssert;

import java.util.function.Consumer;

/**
 * Exception assertion helper with mock support.
 */
public class MockExceptionAssert<T> {

    private final MockAssertBuilder<T> parent;
    private final AssertBuilder.ExceptionAssert<T> delegate;

    public MockExceptionAssert(MockAssertBuilder<T> parent,
                               AssertBuilder.ExceptionAssert<T> delegate) {
        this.parent = parent;
        this.delegate = delegate;
    }

    public MockExceptionAssert<T> hasMessageContaining(String text) {
        delegate.hasMessageContaining(text);
        return this;
    }

    public MockExceptionAssert<T> hasMessage(String text) {
        delegate.hasMessage(text);
        return this;
    }

    public MockExceptionAssert<T> satisfies(Consumer<Throwable> asserter) {
        delegate.satisfies(asserter);
        return this;
    }

    public MockAssertBuilder<T> and() {
        return parent;
    }

    public MockAssertBuilder<T> dbChanges(Consumer<DbChangesAssert> asserter) {
        delegate.dbChanges(asserter);
        return parent;
    }
}
