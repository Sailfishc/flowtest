package com.github.sailfishc.flowtest.v2.runtime;

/**
 * Before and after access to one fixture during verification.
 */
public interface FixtureVerifyContext<T> {

    T before();

    T after() throws Exception;
}
