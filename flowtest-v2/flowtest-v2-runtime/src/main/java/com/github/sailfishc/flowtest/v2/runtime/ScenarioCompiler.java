package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.assertion.FixtureChangeExpectation;
import com.github.sailfishc.flowtest.v2.assertion.FixtureStateExpectation;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureSpec;
import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Performs static validation for scenarios before runtime execution is implemented.
 */
public final class ScenarioCompiler {

    public <R> CompiledScenario<R> compile(ScenarioDefinition<R> definition) {
        List<String> issues = validate(definition);
        if (!issues.isEmpty()) {
            throw new ScenarioValidationException(issues);
        }
        return new CompiledScenario<R>(definition);
    }

    private <R> List<String> validate(ScenarioDefinition<R> definition) {
        List<String> issues = new ArrayList<String>();
        if (definition.getObservations().isEmpty()) {
            issues.add("At least one observed resource must be declared");
        }

        Set<FixtureHandle<?>> handles = new LinkedHashSet<FixtureHandle<?>>();
        for (FixtureSpec<?> fixture : definition.getFixtures()) {
            if (!handles.add(fixture.getHandle())) {
                issues.add("Duplicate fixture handle declared: " + fixture.getHandle().identifier());
            }
        }

        for (ObservationSpec observation : definition.getObservations()) {
            if (observation.isRouteRequired() && observation.getRouteScope().isEmpty()) {
                issues.add("Route scope is required for observed resource " + observation.getResourceName());
            }
            if (observation.getFixtureHandle() != null && !handles.contains(observation.getFixtureHandle())) {
                issues.add("Observed fixture handle is not declared in given: " + observation.getFixtureHandle().identifier());
            }
        }

        for (FixtureStateExpectation<?> expectation : definition.getExpectations().getFixtureExpectations()) {
            if (!handles.contains(expectation.getHandle())) {
                issues.add("Fixture expectation references undeclared handle: " + expectation.getHandle().identifier());
            }
        }

        for (FixtureChangeExpectation<?> expectation : definition.getExpectations().getFixtureChangeExpectations()) {
            if (!handles.contains(expectation.getHandle())) {
                issues.add("Fixture change expectation references undeclared handle: " + expectation.getHandle().identifier());
            }
        }
        return issues;
    }
}
