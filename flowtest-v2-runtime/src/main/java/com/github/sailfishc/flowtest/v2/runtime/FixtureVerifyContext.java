package com.github.sailfishc.flowtest.v2.runtime;

/**
 * Before and after access to one fixture during verification.
 */
public interface FixtureVerifyContext<T> {

    T before();

    T after() throws Exception;

    /**
     * Builds the expected after-state from the fixture's before-state plus the given property overrides,
     * then compares all managed properties except explicitly ignored ones.
     */
    void matchesAfter(FixtureStatePatch<T> patch) throws Exception;
}
