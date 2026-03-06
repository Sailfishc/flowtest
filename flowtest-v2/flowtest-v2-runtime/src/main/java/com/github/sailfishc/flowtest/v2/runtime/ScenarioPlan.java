package com.github.sailfishc.flowtest.v2.runtime;

import java.util.function.Consumer;

/**
 * Stage that captures the action and expectations before compilation.
 */
public interface ScenarioPlan<R> {

    ScenarioPlan<R> then(Consumer<ThenSpec<R>> then);

    ScenarioDefinition<R> definition();

    CompiledScenario<R> compile();

    ScenarioExecutionResult<R> execute(ScenarioExecutor executor) throws Exception;
}
