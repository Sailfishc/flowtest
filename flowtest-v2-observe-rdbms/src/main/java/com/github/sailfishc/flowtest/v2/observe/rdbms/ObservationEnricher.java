package com.github.sailfishc.flowtest.v2.observe.rdbms;

import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureValueResolver;
import com.github.sailfishc.flowtest.v2.spec.ObservationMode;
import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;
import com.github.sailfishc.flowtest.v2.spec.RouteScope;
import com.github.sailfishc.flowtest.v2.spec.TableRouteScope;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared logic for enriching fixture-backed observations with auto-derived
 * {@link TableRouteScope} and {@link RouteScope} from materialized fixture instances.
 *
 * <p>Used by both {@link JdbcObservationExecutor} and
 * {@link MultiDataSourceJdbcObservationExecutor}.</p>
 */
final class ObservationEnricher {

    private ObservationEnricher() {
    }

    /**
     * Enriches fixture-backed observations by deriving:
     * <ul>
     *   <li>{@link TableRouteScope} from dynamic table property (for physical table name resolution)</li>
     *   <li>{@link RouteScope} from the entity identity columns (for WHERE conditions)</li>
     * </ul>
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
        FixtureHandle<?> handle = observation.getFixtureHandle();
        if (handle == null) {
            return observation;
        }
        Class<?> entityType = handle.getType();
        JdbcEntityRegistration registration = registry.getEntityRegistrations().get(entityType);
        if (registration == null) {
            return observation;
        }
        Object fixtureValue = fixtures.resolve(handle);
        if (fixtureValue == null) {
            return observation;
        }

        // Derive TableRouteScope for dynamic table name resolution
        TableRouteScope derivedTableRoute = observation.getTableRouteScope();
        boolean tableRouteDerived = false;
        if (derivedTableRoute.isEmpty() && registration.isDynamicTable()) {
            TableRouteScope derived = registration.deriveTableRouteScope(fixtureValue);
            if (derived != null) {
                derivedTableRoute = derived;
                tableRouteDerived = true;
            }
        }

        // Derive RouteScope from the entity identity for exact-match WHERE conditions
        RouteScope derivedRouteScope = observation.getRouteScope();
        boolean routeScopeDerived = false;
        if (derivedRouteScope.isEmpty()) {
            RouteScope identityRouteScope = registration.deriveIdentityRouteScope(fixtureValue);
            if (identityRouteScope != null) {
                derivedRouteScope = identityRouteScope;
                routeScopeDerived = true;
            }
        }

        if (tableRouteDerived || routeScopeDerived) {
            return observation.withTableRouteScopeAndRouteScope(derivedTableRoute, derivedRouteScope);
        }
        return observation;
    }
}
