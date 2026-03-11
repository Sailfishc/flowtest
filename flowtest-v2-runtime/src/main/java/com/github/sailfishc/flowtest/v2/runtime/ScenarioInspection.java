package com.github.sailfishc.flowtest.v2.runtime;

/**
 * Global escape hatch for cross-resource / cross-fixture imperative assertions.
 * Use for correlations that span multiple resources or combine result with resource data.
 */
@FunctionalInterface
public interface ScenarioInspection<R> {

    void inspect(VerifyContext<R> context) throws Exception;
}
