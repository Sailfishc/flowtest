package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.assertion.ExpectationSet;
import com.github.sailfishc.flowtest.v2.spec.CleanupPolicy;
import com.github.sailfishc.flowtest.v2.spec.FixtureSpec;
import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;
import com.github.sailfishc.flowtest.v2.spec.ThrowingSupplier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable scenario definition produced by the DSL.
 */
public final class ScenarioDefinition<R> {

    private final String name;
    private final List<FixtureSpec<?>> fixtures;
    private final List<ObservationSpec> observations;
    private final CleanupPolicy cleanupPolicy;
    private final ThrowingSupplier<R> action;
    private final ExpectationSet<R> expectations;

    public ScenarioDefinition(String name,
                              List<FixtureSpec<?>> fixtures,
                              List<ObservationSpec> observations,
                              CleanupPolicy cleanupPolicy,
                              ThrowingSupplier<R> action,
                              ExpectationSet<R> expectations) {
        this.name = requireText(name, "name must not be blank");
        this.fixtures = Collections.unmodifiableList(new ArrayList<FixtureSpec<?>>(fixtures));
        this.observations = Collections.unmodifiableList(new ArrayList<ObservationSpec>(observations));
        this.cleanupPolicy = Objects.requireNonNull(cleanupPolicy, "cleanupPolicy must not be null");
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.expectations = Objects.requireNonNull(expectations, "expectations must not be null");
    }

    public String getName() {
        return name;
    }

    public List<FixtureSpec<?>> getFixtures() {
        return fixtures;
    }

    public List<ObservationSpec> getObservations() {
        return observations;
    }

    public CleanupPolicy getCleanupPolicy() {
        return cleanupPolicy;
    }

    public ThrowingSupplier<R> getAction() {
        return action;
    }

    public ExpectationSet<R> getExpectations() {
        return expectations;
    }

    private static String requireText(String text, String message) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }
}
