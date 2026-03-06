package com.github.sailfishc.flowtest.v2.runtime;

/**
 * Supplies a {@link ScenarioExecutor} for test framework integrations.
 */
public interface ScenarioExecutorProvider {

    ScenarioExecutor createScenarioExecutor() throws Exception;
}
