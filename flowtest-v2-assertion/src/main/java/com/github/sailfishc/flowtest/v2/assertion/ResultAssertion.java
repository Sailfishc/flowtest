package com.github.sailfishc.flowtest.v2.assertion;

/**
 * Assertion contract for the action outcome.
 */
public interface ResultAssertion<R> {

    void verify(R result, Throwable failure);
}
