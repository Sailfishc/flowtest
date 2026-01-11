package com.flowtest.mockito;

import com.flowtest.core.TestContext;
import com.flowtest.core.assertion.AssertPhase;

/**
 * Wrapper around AssertPhase that adds mock verification support.
 */
public class MockAssertPhase<T> {

    private final AssertPhase<T> delegate;
    private final TestContext context;
    private final MockContext mockContext;

    public MockAssertPhase(AssertPhase<T> delegate, TestContext context, MockContext mockContext) {
        this.delegate = delegate;
        this.context = context;
        this.mockContext = mockContext;
    }

    /**
     * Returns the assert builder with mock support.
     */
    public MockAssertBuilder<T> assertThat() {
        return new MockAssertBuilder<>(delegate.assertThat(), context, mockContext);
    }

    /**
     * Gets the underlying AssertPhase.
     */
    public AssertPhase<T> getDelegate() {
        return delegate;
    }
}
