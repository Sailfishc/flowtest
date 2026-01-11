package com.flowtest.mockito;

import com.flowtest.core.TestContext;
import com.flowtest.core.ThrowingRunnable;
import com.flowtest.core.ThrowingSupplier;
import com.flowtest.core.assertion.ActPhase;
import com.flowtest.core.assertion.AssertPhase;

/**
 * Wrapper around ActPhase that adds mock support.
 */
public class MockActPhase {

    private final ActPhase delegate;
    private final TestContext context;
    private final MockContext mockContext;

    public MockActPhase(ActPhase delegate, TestContext context, MockContext mockContext) {
        this.delegate = delegate;
        this.context = context;
        this.mockContext = mockContext;
    }

    /**
     * Executes an action that returns a value.
     */
    public <T> MockAssertPhase<T> act(ThrowingSupplier<T> action) {
        AssertPhase<T> assertPhase = delegate.act(action);
        return new MockAssertPhase<>(assertPhase, context, mockContext);
    }

    /**
     * Executes an action that returns void.
     */
    public MockAssertPhase<Void> act(ThrowingRunnable action) {
        AssertPhase<Void> assertPhase = delegate.act(action);
        return new MockAssertPhase<>(assertPhase, context, mockContext);
    }
}
