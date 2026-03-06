package com.github.sailfishc.flowtest.v2.assertion;

import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;

import java.util.Objects;

/**
 * Fixture before/after assertion paired with the handle it targets.
 */
public final class FixtureChangeExpectation<T> {

    private final FixtureHandle<T> handle;
    private final FixtureChangeAssertion<T> assertion;

    public FixtureChangeExpectation(FixtureHandle<T> handle, FixtureChangeAssertion<T> assertion) {
        this.handle = Objects.requireNonNull(handle, "handle must not be null");
        this.assertion = Objects.requireNonNull(assertion, "assertion must not be null");
    }

    public FixtureHandle<T> getHandle() {
        return handle;
    }

    public FixtureChangeAssertion<T> getAssertion() {
        return assertion;
    }
}
