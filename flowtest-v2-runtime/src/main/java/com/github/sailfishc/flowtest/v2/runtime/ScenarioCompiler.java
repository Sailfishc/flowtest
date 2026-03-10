package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.assertion.FixtureChangeExpectation;
import com.github.sailfishc.flowtest.v2.assertion.FixtureStateExpectation;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureSpec;
import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Performs static validation for scenarios before runtime execution is implemented.
 */
public final class ScenarioCompiler {

    public <R> CompiledScenario<R> compile(ScenarioDefinition<R> definition) {
        ValidationResult<R> result = validate(definition);
        List<String> issues = result.issues;
        if (!issues.isEmpty()) {
            throw new ScenarioValidationException(issues);
        }
        return new CompiledScenario<R>(result.definition);
    }

    private <R> ValidationResult<R> validate(ScenarioDefinition<R> definition) {
        List<String> issues = new ArrayList<String>();
        if (definition.getObservations().isEmpty()) {
            issues.add("At least one observed resource must be declared");
        }

        Set<FixtureHandle<?>> handles = new LinkedHashSet<FixtureHandle<?>>();
        Map<String, FixtureHandle<?>> handlesByAlias = new LinkedHashMap<String, FixtureHandle<?>>();
        for (FixtureSpec<?> fixture : definition.getFixtures()) {
            if (!handles.add(fixture.getHandle())) {
                issues.add("Duplicate fixture handle declared: " + fixture.getHandle().identifier());
            }
            FixtureHandle<?> previous = handlesByAlias.put(fixture.getHandle().getName(), fixture.getHandle());
            if (previous != null && !previous.equals(fixture.getHandle())) {
                issues.add("Duplicate fixture alias declared: " + fixture.getHandle().getName());
            }
        }

        List<ObservationSpec> resolvedObservations = new ArrayList<ObservationSpec>();
        for (ObservationSpec observation : definition.getObservations()) {
            if (observation.isRouteRequired() && observation.getRouteScope().isEmpty()) {
                issues.add("Route scope is required for observed resource " + observation.getResourceName());
            }
            if (observation.getFixtureAlias() != null) {
                FixtureHandle<?> handle = handlesByAlias.get(observation.getFixtureAlias());
                if (handle == null) {
                    issues.add("Observed fixture alias is not declared in given: " + observation.getFixtureAlias());
                } else {
                    resolvedObservations.add(ObservationSpec.fixture(handle));
                }
                continue;
            }
            if (observation.getFixtureHandle() != null && !handles.contains(observation.getFixtureHandle())) {
                issues.add("Observed fixture handle is not declared in given: " + observation.getFixtureHandle().identifier());
            }
            resolvedObservations.add(observation);
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
        return new ValidationResult<R>(issues, new ScenarioDefinition<R>(
            definition.getName(),
            definition.getFixtures(),
            resolvedObservations,
            definition.getCleanupPolicy(),
            definition.getAction(),
            definition.getExpectations(),
            definition.getVerifications()
        ));
    }

    private static final class ValidationResult<R> {

        private final List<String> issues;
        private final ScenarioDefinition<R> definition;

        private ValidationResult(List<String> issues, ScenarioDefinition<R> definition) {
            this.issues = issues;
            this.definition = definition;
        }
    }
}
