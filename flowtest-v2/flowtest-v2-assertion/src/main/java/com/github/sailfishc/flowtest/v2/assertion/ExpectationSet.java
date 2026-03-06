package com.github.sailfishc.flowtest.v2.assertion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable set of expectations attached to a scenario.
 */
public final class ExpectationSet<R> {

    private final List<ResultAssertion<R>> resultAssertions;
    private final List<ResourceChangeExpectation> changeExpectations;
    private final List<ResourceChangeAssertionExpectation> changeAssertionExpectations;
    private final List<FixtureStateExpectation<?>> fixtureExpectations;

    public ExpectationSet(List<ResultAssertion<R>> resultAssertions,
                          List<ResourceChangeExpectation> changeExpectations,
                          List<ResourceChangeAssertionExpectation> changeAssertionExpectations,
                          List<FixtureStateExpectation<?>> fixtureExpectations) {
        this.resultAssertions = Collections.unmodifiableList(new ArrayList<ResultAssertion<R>>(resultAssertions));
        this.changeExpectations = Collections.unmodifiableList(new ArrayList<ResourceChangeExpectation>(changeExpectations));
        this.changeAssertionExpectations = Collections.unmodifiableList(
            new ArrayList<ResourceChangeAssertionExpectation>(changeAssertionExpectations));
        this.fixtureExpectations = Collections.unmodifiableList(new ArrayList<FixtureStateExpectation<?>>(fixtureExpectations));
    }

    public static <R> ExpectationSet<R> empty() {
        return new ExpectationSet<R>(
            Collections.<ResultAssertion<R>>emptyList(),
            Collections.<ResourceChangeExpectation>emptyList(),
            Collections.<ResourceChangeAssertionExpectation>emptyList(),
            Collections.<FixtureStateExpectation<?>>emptyList()
        );
    }

    public List<ResultAssertion<R>> getResultAssertions() {
        return resultAssertions;
    }

    public List<ResourceChangeExpectation> getChangeExpectations() {
        return changeExpectations;
    }

    public List<ResourceChangeAssertionExpectation> getChangeAssertionExpectations() {
        return changeAssertionExpectations;
    }

    public List<FixtureStateExpectation<?>> getFixtureExpectations() {
        return fixtureExpectations;
    }
}
