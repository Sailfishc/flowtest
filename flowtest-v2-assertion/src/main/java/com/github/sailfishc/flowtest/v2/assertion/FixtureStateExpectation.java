package com.github.sailfishc.flowtest.v2.assertion;

import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;

import java.util.Objects;

/**
 * Fixture assertion paired with the handle it targets.
 */
public final class FixtureStateExpectation<T> {

    private final FixtureHandle<T> handle;
    private final FixtureAssertion<T> assertion;

    public FixtureStateExpectation(FixtureHandle<T> handle, FixtureAssertion<T> assertion) {
        this.handle = Objects.requireNonNull(handle, "handle must not be null");
        this.assertion = Objects.requireNonNull(assertion, "assertion must not be null");
    }

    public FixtureHandle<T> getHandle() {
        return handle;
    }

    public FixtureAssertion<T> getAssertion() {
        return assertion;
    }
}
