package com.flowtest.mockito;

import org.mockito.Mockito;

/**
 * Fluent API for mock verification in the assert phase.
 *
 * @param <T> the return type of the test action
 */
public class MockVerifier<T> {

    private final MockContext mockContext;
    private final Runnable onDone;

    public MockVerifier(MockContext mockContext, Runnable onDone) {
        this.mockContext = mockContext;
        this.onDone = onDone;
    }

    /**
     * Starts verification for a mock of the given type.
     */
    public <M> MockAssertion<T, M> verify(Class<M> mockClass) {
        M mock = mockContext.getMock(mockClass);
        return new MockAssertion<>(this, mock);
    }

    /**
     * Starts verification for a mock by alias.
     */
    public <M> MockAssertion<T, M> verify(String alias, Class<M> mockClass) {
        M mock = mockContext.getMock(alias, mockClass);
        return new MockAssertion<>(this, mock);
    }

    /**
     * Verifies no more interactions on all mocks.
     */
    public MockVerifier<T> noMoreInteractions() {
        for (Object mock : mockContext.getAllMocks()) {
            Mockito.verifyNoMoreInteractions(mock);
        }
        return this;
    }

    /**
     * Verifies no interactions on all mocks.
     */
    public MockVerifier<T> noInteractions() {
        for (Object mock : mockContext.getAllMocks()) {
            Mockito.verifyNoInteractions(mock);
        }
        return this;
    }

    /**
     * Completes mock verification.
     */
    public void done() {
        if (onDone != null) {
            onDone.run();
        }
    }

    /**
     * Gets the MockContext.
     */
    public MockContext getMockContext() {
        return mockContext;
    }
}
