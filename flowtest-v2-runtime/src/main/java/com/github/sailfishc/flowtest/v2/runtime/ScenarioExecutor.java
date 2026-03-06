package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.assertion.ExpectationSet;
import com.github.sailfishc.flowtest.v2.assertion.FixtureChangeExpectation;
import com.github.sailfishc.flowtest.v2.assertion.FixtureStateExpectation;
import com.github.sailfishc.flowtest.v2.assertion.ResourceChangeAssertionExpectation;
import com.github.sailfishc.flowtest.v2.assertion.ResourceChangeExpectation;
import com.github.sailfishc.flowtest.v2.assertion.ResultAssertion;
import com.github.sailfishc.flowtest.v2.fixture.FixtureExecution;
import com.github.sailfishc.flowtest.v2.fixture.FixtureExecutor;
import com.github.sailfishc.flowtest.v2.fixture.NoOpFixtureExecutor;
import com.github.sailfishc.flowtest.v2.spec.CleanupPolicy;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureValueResolver;
import com.github.sailfishc.flowtest.v2.spec.ObservationDiff;
import com.github.sailfishc.flowtest.v2.spec.ObservationExecutor;
import com.github.sailfishc.flowtest.v2.spec.ObservationPreparationSupport;
import com.github.sailfishc.flowtest.v2.spec.ObservationSnapshot;
import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;
import com.github.sailfishc.flowtest.v2.spec.ResourceChange;

import java.util.List;

/**
 * Executes compiled scenarios against pluggable fixture and observation backends.
 */
public final class ScenarioExecutor {

    private final FixtureExecutor fixtureExecutor;
    private final ObservationExecutor observationExecutor;

    public ScenarioExecutor(ObservationExecutor observationExecutor) {
        this(NoOpFixtureExecutor.INSTANCE, observationExecutor);
    }

    public ScenarioExecutor(FixtureExecutor fixtureExecutor, ObservationExecutor observationExecutor) {
        if (fixtureExecutor == null) {
            throw new IllegalArgumentException("fixtureExecutor must not be null");
        }
        if (observationExecutor == null) {
            throw new IllegalArgumentException("observationExecutor must not be null");
        }
        this.fixtureExecutor = fixtureExecutor;
        this.observationExecutor = observationExecutor;
    }

    public <R> ScenarioExecutionResult<R> execute(CompiledScenario<R> compiledScenario) throws Exception {
        ScenarioDefinition<R> definition = compiledScenario.getDefinition();
        FixtureExecution fixtureExecution = fixtureExecutor.prepare(definition.getFixtures());

        // Prepare effective observations: enrich fixture-backed specs with derived metadata
        List<ObservationSpec> effectiveObservations = prepareObservations(
            definition.getObservations(), fixtureExecution);

        ObservationSnapshot beforeSnapshot = ObservationSnapshot.empty();
        ObservationSnapshot afterSnapshot = ObservationSnapshot.empty();
        ObservationDiff diff = ObservationDiff.empty();
        R result = null;
        Exception failure = null;
        Throwable primaryFailure = null;

        try {
            beforeSnapshot = observationExecutor.capture(effectiveObservations);
            try {
                result = definition.getAction().get();
            } catch (Exception ex) {
                failure = ex;
            }
            afterSnapshot = observationExecutor.capture(effectiveObservations);
            diff = ObservationDiff.between(beforeSnapshot, afterSnapshot);
            verifyExpectations(definition.getExpectations(), definition.getVerifications(), fixtureExecution, result, failure, diff);
            if (failure != null
                && definition.getExpectations().getResultAssertions().isEmpty()
                && definition.getVerifications().isEmpty()) {
                primaryFailure = failure;
            }
        } catch (Throwable ex) {
            primaryFailure = ex;
        } finally {
            Throwable cleanupFailure = cleanup(definition.getCleanupPolicy(), effectiveObservations, fixtureExecution, diff);
            if (cleanupFailure != null) {
                if (primaryFailure == null) {
                    primaryFailure = cleanupFailure;
                } else {
                    primaryFailure.addSuppressed(cleanupFailure);
                }
            }
        }

        if (primaryFailure != null) {
            rethrow(primaryFailure);
        }
        return new ScenarioExecutionResult<R>(definition.getName(), result, failure, diff);
    }

    /**
     * Enriches observation specs using materialized fixture data if the observation
     * executor supports it. This allows fixture-backed dynamic-table observations
     * to auto-derive their {@link com.github.sailfishc.flowtest.v2.spec.TableRouteScope}.
     */
    private List<ObservationSpec> prepareObservations(List<ObservationSpec> observations,
                                                      final FixtureExecution fixtureExecution) {
        if (!(observationExecutor instanceof ObservationPreparationSupport)) {
            return observations;
        }
        FixtureValueResolver resolver = new FixtureValueResolver() {
            @Override
            public <T> T resolve(FixtureHandle<T> handle) {
                return fixtureExecution.resolve(handle);
            }
        };
        return ((ObservationPreparationSupport) observationExecutor)
            .prepareObservations(observations, resolver);
    }

    private <R> void verifyExpectations(ExpectationSet<R> expectations,
                                        List<ScenarioVerification<R>> verifications,
                                        FixtureExecution fixtureExecution,
                                        R result,
                                        Exception failure,
                                        ObservationDiff diff) throws Exception {
        for (ResultAssertion<R> assertion : expectations.getResultAssertions()) {
            assertion.verify(result, failure);
        }
        DefaultVerifyContext<R> verifyContext = verifyScenario(verifications, fixtureExecution, result, failure, diff);
        verifyChanges(expectations.getChangeExpectations(), diff);
        verifyChangeAssertions(expectations.getChangeAssertionExpectations(), diff);
        verifyFixtures(expectations.getFixtureExpectations(), fixtureExecution);
        verifyFixtureChanges(expectations.getFixtureChangeExpectations(), fixtureExecution);
        if (failure != null
            && expectations.getResultAssertions().isEmpty()
            && !verifications.isEmpty()
            && !verifyContext.isOutcomeVerified()) {
            throw failure;
        }
    }

    private <R> DefaultVerifyContext<R> verifyScenario(List<ScenarioVerification<R>> verifications,
                                                       FixtureExecution fixtureExecution,
                                                       R result,
                                                       Exception failure,
                                                       ObservationDiff diff) throws Exception {
        DefaultVerifyContext<R> context = new DefaultVerifyContext<R>(result, failure, diff, fixtureExecution);
        for (ScenarioVerification<R> verification : verifications) {
            verification.verify(context);
        }
        return context;
    }

    private void verifyChanges(List<ResourceChangeExpectation> expectations, ObservationDiff diff) {
        for (ResourceChangeExpectation expectation : expectations) {
            ResourceChange actual = diff.getChange(expectation.getResourceName());
            if (actual == null) {
                throw new AssertionError("No observed resource named " + expectation.getResourceName());
            }
            if (expectation.getExpectedInserted() != null && actual.getInsertedCount() != expectation.getExpectedInserted()) {
                throw new AssertionError("Expected inserted count " + expectation.getExpectedInserted()
                    + " for resource " + expectation.getResourceName() + " but was " + actual.getInsertedCount());
            }
            if (expectation.getExpectedDeleted() != null && actual.getDeletedCount() != expectation.getExpectedDeleted()) {
                throw new AssertionError("Expected deleted count " + expectation.getExpectedDeleted()
                    + " for resource " + expectation.getResourceName() + " but was " + actual.getDeletedCount());
            }
            if (expectation.getExpectedModified() != null && actual.getModifiedCount() != expectation.getExpectedModified()) {
                throw new AssertionError("Expected modified count " + expectation.getExpectedModified()
                    + " for resource " + expectation.getResourceName() + " but was " + actual.getModifiedCount());
            }
        }
    }

    private void verifyChangeAssertions(List<ResourceChangeAssertionExpectation> expectations, ObservationDiff diff) {
        for (ResourceChangeAssertionExpectation expectation : expectations) {
            ResourceChange actual = diff.getChange(expectation.getResourceName());
            if (actual == null) {
                throw new AssertionError("No observed resource named " + expectation.getResourceName());
            }
            expectation.getAssertion().verify(actual);
        }
    }

    private void verifyFixtures(List<FixtureStateExpectation<?>> expectations, FixtureExecution fixtureExecution) throws Exception {
        for (FixtureStateExpectation<?> expectation : expectations) {
            verifyFixture(expectation, fixtureExecution);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void verifyFixture(FixtureStateExpectation<?> expectation, FixtureExecution fixtureExecution) throws Exception {
        Object value = fixtureExecution.reload(expectation.getHandle());
        ((com.github.sailfishc.flowtest.v2.assertion.FixtureAssertion) expectation.getAssertion()).verify(value);
    }

    private void verifyFixtureChanges(List<FixtureChangeExpectation<?>> expectations, FixtureExecution fixtureExecution) throws Exception {
        for (FixtureChangeExpectation<?> expectation : expectations) {
            verifyFixtureChange(expectation, fixtureExecution);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void verifyFixtureChange(FixtureChangeExpectation<?> expectation, FixtureExecution fixtureExecution) throws Exception {
        Object before = fixtureExecution.resolve(expectation.getHandle());
        Object after = fixtureExecution.reload(expectation.getHandle());
        ((com.github.sailfishc.flowtest.v2.assertion.FixtureChangeAssertion) expectation.getAssertion()).verify(before, after);
    }

    private Throwable cleanup(CleanupPolicy cleanupPolicy,
                              List<ObservationSpec> effectiveObservations,
                              FixtureExecution fixtureExecution,
                              ObservationDiff diff) {
        try {
            if (cleanupPolicy == CleanupPolicy.ROLLBACK) {
                throw new UnsupportedOperationException(
                    "CleanupPolicy.ROLLBACK is not yet implemented. "
                    + "Use DELETE_INSERTED, RESTORE_BEFORE_IMAGE, or manage transactions externally.");
            }
            observationExecutor.cleanup(effectiveObservations, diff, cleanupPolicy);
            fixtureExecution.cleanup();
            return null;
        } catch (Throwable ex) {
            return ex;
        }
    }

    private void rethrow(Throwable throwable) throws Exception {
        if (throwable instanceof Exception) {
            throw (Exception) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
        throw new RuntimeException(throwable);
    }
}
