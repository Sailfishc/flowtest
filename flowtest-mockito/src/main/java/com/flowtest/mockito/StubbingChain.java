package com.flowtest.mockito;

import org.mockito.stubbing.Answer;
import org.mockito.stubbing.OngoingStubbing;

/**
 * Fluent chain for configuring mock stubbing behavior.
 *
 * @param <T> the type of the mock
 * @param <R> the return type of the stubbed method
 */
public class StubbingChain<T, R> {

    private final MockConfiguration<T> parent;
    private final OngoingStubbing<R> stubbing;

    public StubbingChain(MockConfiguration<T> parent, OngoingStubbing<R> stubbing) {
        this.parent = parent;
        this.stubbing = stubbing;
    }

    /**
     * Configures the mock to return the specified value.
     */
    public MockConfiguration<T> thenReturn(R value) {
        stubbing.thenReturn(value);
        return parent;
    }

    /**
     * Configures the mock to return multiple values in sequence.
     */
    @SuppressWarnings("unchecked")
    public MockConfiguration<T> thenReturn(R first, R... others) {
        stubbing.thenReturn(first, others);
        return parent;
    }

    /**
     * Configures the mock to throw the specified exception.
     */
    public MockConfiguration<T> thenThrow(Throwable throwable) {
        stubbing.thenThrow(throwable);
        return parent;
    }

    /**
     * Configures the mock to throw exceptions of the specified types.
     */
    public MockConfiguration<T> thenThrow(Class<? extends Throwable> first,
                                          Class<? extends Throwable>... others) {
        stubbing.thenThrow(first, others);
        return parent;
    }

    /**
     * Configures the mock to use a custom answer.
     */
    public MockConfiguration<T> thenAnswer(Answer<R> answer) {
        stubbing.thenAnswer(answer);
        return parent;
    }

    /**
     * Configures the mock to call the real method (for spies).
     */
    public MockConfiguration<T> thenCallRealMethod() {
        stubbing.thenCallRealMethod();
        return parent;
    }
}
