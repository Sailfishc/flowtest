package com.github.sailfishc.flowtest.v2.runtime;

import java.util.function.Consumer;

/**
 * Stage that captures the action and expectations before compilation.
 */
public interface ScenarioPlan<R> {

    /**
     * Add declarative and/or imperative expectations.
     * Can be called multiple times; expectations are appended in declaration order.
     */
    ScenarioPlan<R> then(Consumer<ThenSpec<R>> then);

    ScenarioDefinition<R> definition();

    CompiledScenario<R> compile();

    /**
     * Runs the scenario with the executor already bound to the current thread by a test integration.
     */
    ScenarioExecutionResult<R> run() throws Exception;

    ScenarioExecutionResult<R> execute(ScenarioExecutor executor) throws Exception;
}
