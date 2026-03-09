package com.github.sailfishc.flowtest.v2.runtime;

/**
 * Supplies a {@link ScenarioExecutor} for test framework integrations.
 *
 * @deprecated since 2.0. The framework now automatically resolves ScenarioExecutor
 *             from Spring ApplicationContext in Spring Boot tests, or from fields
 *             in non-Spring tests. Implementing this interface is no longer required.
 *             Simply declare a {@code ScenarioExecutor} field (e.g., {@code @Autowired
 *             private ScenarioExecutor executor;}) if you need direct access, otherwise
 *             just call {@code .run()} and the framework handles everything.
 *
 * @see ScenarioExecutor
 */
@Deprecated
public interface ScenarioExecutorProvider {

    /**
     * Creates or provides a ScenarioExecutor instance.
     *
     * @return the ScenarioExecutor to use for test execution
     * @throws Exception if creation fails
     */
    ScenarioExecutor createScenarioExecutor() throws Exception;
}
