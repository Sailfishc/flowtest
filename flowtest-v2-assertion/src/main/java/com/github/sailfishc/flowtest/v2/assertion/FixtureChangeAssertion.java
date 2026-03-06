package com.github.sailfishc.flowtest.v2.assertion;

/**
 * Assertion contract for a fixture with access to both before and after states.
 */
public interface FixtureChangeAssertion<T> {

    void verify(T before, T after);
}
