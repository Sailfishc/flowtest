package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.assertion.FixtureChangeExpectation;
import com.github.sailfishc.flowtest.v2.assertion.FixtureStateExpectation;
import com.github.sailfishc.flowtest.v2.spec.CleanupPolicy;
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
        // Observations can be empty if the scenario only tests result/fixture
        // (auto-inferred observations are already merged into the definition by the builder)

        // --- Validate cleanup policy ---
        CleanupPolicy cleanupPolicy = definition.getCleanupPolicy();
        if (cleanupPolicy == CleanupPolicy.ROLLBACK) {
            issues.add("CleanupPolicy.ROLLBACK is not supported by the current FlowTest V2 runtime. "
                + "Supported policies: DELETE_INSERTED, DELETE_FIXTURE, RESTORE_BEFORE_IMAGE.");
        }
        if (cleanupPolicy == CleanupPolicy.CUSTOM_COMPENSATOR) {
            issues.add("CleanupPolicy.CUSTOM_COMPENSATOR is not supported because no cleanup compensator SPI "
                + "exists in the current FlowTest V2 runtime.");
        }

        // --- Validate fixture handles ---
        Set<FixtureHandle<?>> handles = new LinkedHashSet<FixtureHandle<?>>();
        Map<String, FixtureHandle<?>> handlesByAlias = new LinkedHashMap<String, FixtureHandle<?>>();
        Map<Class<?>, List<FixtureHandle<?>>> handlesByType = new LinkedHashMap<Class<?>, List<FixtureHandle<?>>>();
        for (FixtureSpec<?> fixture : definition.getFixtures()) {
            if (!handles.add(fixture.getHandle())) {
                issues.add("Duplicate fixture handle declared: " + fixture.getHandle().identifier());
            }
            FixtureHandle<?> previous = handlesByAlias.put(fixture.getHandle().getName(), fixture.getHandle());
            if (previous != null && !previous.equals(fixture.getHandle())) {
                issues.add("Duplicate fixture alias declared: " + fixture.getHandle().getName());
            }
            List<FixtureHandle<?>> typeHandles = handlesByType.get(fixture.getHandle().getType());
            if (typeHandles == null) {
                typeHandles = new ArrayList<FixtureHandle<?>>();
                handlesByType.put(fixture.getHandle().getType(), typeHandles);
            }
            typeHandles.add(fixture.getHandle());
        }

        // --- Resolve and validate observations ---
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

        // --- Validate duplicate observation resource names ---
        Map<String, ObservationSpec> seenResourceNames = new LinkedHashMap<String, ObservationSpec>();
        for (ObservationSpec observation : resolvedObservations) {
            ObservationSpec existing = seenResourceNames.put(observation.getResourceName(), observation);
            if (existing != null) {
                issues.add("Duplicate observed resource '" + observation.getResourceName()
                    + "'. FlowTest V2 currently requires each observed resourceName to be unique within a scenario. "
                    + "First: " + describeObservation(existing) + ". Duplicate: " + describeObservation(observation) + ".");
            }
        }

        // --- Validate fixture expectations ---
        for (FixtureStateExpectation<?> expectation : definition.getExpectations().getFixtureExpectations()) {
            validateFixtureExpectationHandle(expectation.getHandle(), handles, handlesByType, issues,
                "Fixture expectation");
        }

        for (FixtureChangeExpectation<?> expectation : definition.getExpectations().getFixtureChangeExpectations()) {
            validateFixtureExpectationHandle(expectation.getHandle(), handles, handlesByType, issues,
                "Fixture change expectation");
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

    private void validateFixtureExpectationHandle(FixtureHandle<?> handle,
                                                  Set<FixtureHandle<?>> handles,
                                                  Map<Class<?>, List<FixtureHandle<?>>> handlesByType,
                                                  List<String> issues,
                                                  String expectationLabel) {
        if (handles.contains(handle)) {
            return;
        }
        // Check if the handle uses the default alias (simpleName) and there are fixtures of the same type
        List<FixtureHandle<?>> sameTypeHandles = handlesByType.get(handle.getType());
        if (sameTypeHandles == null || sameTypeHandles.isEmpty()) {
            issues.add(expectationLabel + " references undeclared handle: " + handle.identifier());
            return;
        }
        String defaultAlias = handle.getType().getSimpleName();
        if (defaultAlias.equals(handle.getName())) {
            if (sameTypeHandles.size() > 1) {
                StringBuilder aliases = new StringBuilder("[");
                for (int i = 0; i < sameTypeHandles.size(); i++) {
                    if (i > 0) aliases.append(", ");
                    aliases.append(sameTypeHandles.get(i).getName());
                }
                aliases.append("]");
                issues.add(expectationLabel + " for type " + handle.getType().getName()
                    + " is ambiguous because multiple fixtures of that type are declared " + aliases
                    + ". Use then().fixture(\"alias\", " + defaultAlias + ".class, ...) or the FixtureHandle returned from given().");
            } else {
                FixtureHandle<?> actual = sameTypeHandles.get(0);
                if (!defaultAlias.equals(actual.getName())) {
                    issues.add(expectationLabel + " for type " + handle.getType().getName()
                        + " uses default alias '" + defaultAlias + "', but the declared fixture alias is '"
                        + actual.getName() + "'. Use then().fixture(\"" + actual.getName() + "\", "
                        + defaultAlias + ".class, ...) or the FixtureHandle returned from given().");
                } else {
                    issues.add(expectationLabel + " references undeclared handle: " + handle.identifier());
                }
            }
        } else {
            issues.add(expectationLabel + " references undeclared handle: " + handle.identifier());
        }
    }

    private String describeObservation(ObservationSpec obs) {
        StringBuilder sb = new StringBuilder();
        sb.append(obs.getResourceKind()).append("/").append(obs.getObservationMode());
        if (obs.getFixtureHandle() != null) {
            sb.append(" handle=").append(obs.getFixtureHandle().identifier());
        }
        if (!obs.getRouteScope().isEmpty()) {
            sb.append(" route=").append(obs.getRouteScope().getConditions());
        }
        return sb.toString();
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
