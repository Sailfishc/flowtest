package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.spec.ObservationDiff;

/**
 * Result of executing a compiled scenario.
 */
public final class ScenarioExecutionResult<R> {

    private final String scenarioName;
    private final R result;
    private final Exception failure;
    private final ObservationDiff diff;

    public ScenarioExecutionResult(String scenarioName, R result, Exception failure, ObservationDiff diff) {
        this.scenarioName = scenarioName;
        this.result = result;
        this.failure = failure;
        this.diff = diff;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public R getResult() {
        return result;
    }

    public Exception getFailure() {
        return failure;
    }

    public ObservationDiff getDiff() {
        return diff;
    }
}
