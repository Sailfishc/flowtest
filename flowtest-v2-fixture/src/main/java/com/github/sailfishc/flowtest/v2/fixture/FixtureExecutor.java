package com.github.sailfishc.flowtest.v2.fixture;

import com.github.sailfishc.flowtest.v2.spec.FixtureSpec;

import java.util.List;

/**
 * Prepares fixture data for a scenario.
 */
public interface FixtureExecutor {

    FixtureExecution prepare(List<FixtureSpec<?>> fixtures) throws Exception;
}
