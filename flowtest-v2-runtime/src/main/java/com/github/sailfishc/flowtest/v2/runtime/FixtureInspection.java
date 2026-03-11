package com.github.sailfishc.flowtest.v2.runtime;

/**
 * Scoped imperative assertion callback for a single fixture.
 */
@FunctionalInterface
public interface FixtureInspection<T> {

    void inspect(FixtureVerifyContext<T> context) throws Exception;
}
