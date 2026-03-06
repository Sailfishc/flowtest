package com.github.sailfishc.flowtest.v2.runtime;

import java.util.function.Consumer;

/**
 * Stage that captures the action and expectations before compilation.
 */
public interface ScenarioPlan<R> {

    ScenarioPlan<R> then(Consumer<ThenSpec<R>> then);

    ScenarioPlan<R> verify(ScenarioVerification<R> verification);

    ScenarioDefinition<R> definition();

    CompiledScenario<R> compile();

    /**
     * Runs the scenario with the executor already bound to the current thread by a test integration.
     */
    ScenarioExecutionResult<R> run() throws Exception;

    ScenarioExecutionResult<R> execute(ScenarioExecutor executor) throws Exception;
}
