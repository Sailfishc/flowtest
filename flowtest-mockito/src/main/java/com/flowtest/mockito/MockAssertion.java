package com.flowtest.mockito;

import org.mockito.Mockito;
import org.mockito.verification.VerificationMode;

import java.util.function.Consumer;

/**
 * Fluent API for verifying interactions on a single mock.
 *
 * @param <T> the return type of the test action
 * @param <M> the type of the mock being verified
 */
public class MockAssertion<T, M> {

    private final MockVerifier<T> parent;
    private final M mock;
    private VerificationMode mode = Mockito.times(1);

    public MockAssertion(MockVerifier<T> parent, M mock) {
        this.parent = parent;
        this.mock = mock;
    }

    /**
     * Sets verification to expect exactly n calls.
     */
    public MockAssertion<T, M> times(int n) {
        this.mode = Mockito.times(n);
        return this;
    }

    /**
     * Sets verification to expect no calls.
     */
    public MockAssertion<T, M> never() {
        this.mode = Mockito.never();
        return this;
    }

    /**
     * Sets verification to expect at least n calls.
     */
    public MockAssertion<T, M> atLeast(int n) {
        this.mode = Mockito.atLeast(n);
        return this;
    }

    /**
     * Sets verification to expect at least one call.
     */
    public MockAssertion<T, M> atLeastOnce() {
        this.mode = Mockito.atLeastOnce();
        return this;
    }

    /**
     * Sets verification to expect at most n calls.
     */
    public MockAssertion<T, M> atMost(int n) {
        this.mode = Mockito.atMost(n);
        return this;
    }

    /**
     * Sets verification to expect only one call (and no more).
     */
    public MockAssertion<T, M> only() {
        this.mode = Mockito.only();
        return this;
    }

    /**
     * Verifies the method was called with the configured mode.
     */
    public MockAssertion<T, M> called(Consumer<M> methodCall) {
        M verifier = Mockito.verify(mock, mode);
        methodCall.accept(verifier);
        // Reset mode for next verification
        this.mode = Mockito.times(1);
        return this;
    }

    /**
     * Returns to MockVerifier to verify another mock.
     */
    public MockVerifier<T> and() {
        return parent;
    }

    /**
     * Completes verification and returns.
     */
    public void done() {
        parent.done();
    }
}
