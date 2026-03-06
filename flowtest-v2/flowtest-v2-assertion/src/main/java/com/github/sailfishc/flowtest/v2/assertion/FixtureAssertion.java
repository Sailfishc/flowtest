package com.github.sailfishc.flowtest.v2.assertion;

/**
 * Assertion contract for a fixture-backed value.
 */
public interface FixtureAssertion<T> {

    void verify(T value);
}
