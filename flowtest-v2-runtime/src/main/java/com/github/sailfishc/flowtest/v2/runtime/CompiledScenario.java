package com.github.sailfishc.flowtest.v2.runtime;

/**
 * Output of scenario compilation.
 */
public final class CompiledScenario<R> {

    private final ScenarioDefinition<R> definition;

    public CompiledScenario(ScenarioDefinition<R> definition) {
        this.definition = definition;
    }

    public ScenarioDefinition<R> getDefinition() {
        return definition;
    }

    public ScenarioExecutionResult<R> run() throws Exception {
        return execute(ScenarioExecutors.current());
    }

    public ScenarioExecutionResult<R> execute(ScenarioExecutor executor) throws Exception {
        return executor.execute(this);
    }
}
