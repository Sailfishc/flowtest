package com.github.sailfishc.flowtest.v2;

import com.github.sailfishc.flowtest.v2.runtime.DefaultScenarioBuilder;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioBuilder;

/**
 * Entry point for the new observation-first scenario DSL.
 */
public final class FlowTestV2 {

    private FlowTestV2() {
    }

    public static ScenarioBuilder scenario(String name) {
        return new DefaultScenarioBuilder(name);
    }
}
