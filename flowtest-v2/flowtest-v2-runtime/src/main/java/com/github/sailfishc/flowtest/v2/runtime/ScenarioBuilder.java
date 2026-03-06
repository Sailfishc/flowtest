package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.spec.CleanupPolicy;
import com.github.sailfishc.flowtest.v2.spec.ThrowingSupplier;

import java.util.function.Consumer;

/**
 * Builder for assembling a scenario definition.
 */
public interface ScenarioBuilder {

    ScenarioBuilder given(Consumer<GivenSpec> given);

    ScenarioBuilder observe(Consumer<ObserveSpec> observe);

    ScenarioBuilder cleanup(CleanupPolicy cleanupPolicy);

    <R> ScenarioPlan<R> when(ThrowingSupplier<R> action);
}
