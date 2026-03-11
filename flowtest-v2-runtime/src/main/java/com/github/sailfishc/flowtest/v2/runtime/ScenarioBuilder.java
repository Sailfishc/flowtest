package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.spec.CleanupPolicy;
import com.github.sailfishc.flowtest.v2.spec.ThrowingSupplier;

import java.util.function.Consumer;

/**
 * Builder for assembling a scenario definition.
 *
 * <pre>{@code
 * FlowTestV2.scenario("name")
 *     .given(g -> g.fixture("user", User.class, f -> f.set(User::setId, 1L)))
 *     .observe(o -> o.table("ft_order", r -> r.route("tenant_id", 100L)))
 *     .when(() -> service.doSomething())
 *     .then(t -> t.success().table("ft_order", order -> order.inserted(1)))
 *     .run();
 * }</pre>
 */
public interface ScenarioBuilder {

    ScenarioBuilder given(Consumer<GivenSpec> given);

    /**
     * Optional observation configuration for resources that cannot be auto-inferred from {@code then(...)}.
     * Only needed when resources require route conditions, dynamic table parameters,
     * or are accessed exclusively inside {@code then.inspect(...)}.
     */
    ScenarioBuilder observe(Consumer<ObserveSpec> observe);

    ScenarioBuilder cleanup(CleanupPolicy cleanupPolicy);

    <R> ScenarioPlan<R> when(ThrowingSupplier<R> action);
}
