package com.github.sailfishc.flowtest.v2.observe.rdbms;

import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureValueResolver;
import com.github.sailfishc.flowtest.v2.spec.ObservationMode;
import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;
import com.github.sailfishc.flowtest.v2.spec.TableRouteScope;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared logic for enriching fixture-backed observations with auto-derived
 * {@link TableRouteScope} from materialized fixture instances.
 *
 * <p>Used by both {@link JdbcObservationExecutor} and
 * {@link MultiDataSourceJdbcObservationExecutor}.</p>
 */
final class ObservationEnricher {

    private ObservationEnricher() {
    }

    /**
     * Enriches fixture-backed dynamic-table observations by deriving {@link TableRouteScope}
     * from the materialized fixture entity's dynamic table property.
     *
     * @param observations the original observation list
     * @param fixtures the fixture value resolver
     * @param registry the observation registry for entity metadata lookup
     * @return the enriched observation list (same instance if no changes needed)
     */
    static List<ObservationSpec> enrichFixtureObservations(List<ObservationSpec> observations,
                                                           FixtureValueResolver fixtures,
                                                           JdbcObservationRegistry registry) {
        List<ObservationSpec> result = new ArrayList<ObservationSpec>();
        boolean changed = false;
        for (ObservationSpec observation : observations) {
            ObservationSpec enriched = enrichSingle(observation, fixtures, registry);
            result.add(enriched);
            if (enriched != observation) {
                changed = true;
            }
        }
        return changed ? result : observations;
    }

    private static ObservationSpec enrichSingle(ObservationSpec observation,
                                                FixtureValueResolver fixtures,
                                                JdbcObservationRegistry registry) {
        if (observation.getObservationMode() != ObservationMode.FIXTURE_BACKED) {
            return observation;
        }
        if (!observation.getTableRouteScope().isEmpty()) {
            return observation;
        }
        FixtureHandle<?> handle = observation.getFixtureHandle();
        if (handle == null) {
            return observation;
        }
        Class<?> entityType = handle.getType();
        JdbcEntityRegistration registration = registry.getEntityRegistrations().get(entityType);
        if (registration == null || !registration.isDynamicTable()) {
            return observation;
        }
        Object fixtureValue = fixtures.resolve(handle);
        if (fixtureValue == null) {
            return observation;
        }
        TableRouteScope derived = registration.deriveTableRouteScope(fixtureValue);
        if (derived == null) {
            return observation;
        }
        return observation.withTableRouteScope(derived);
    }
}
