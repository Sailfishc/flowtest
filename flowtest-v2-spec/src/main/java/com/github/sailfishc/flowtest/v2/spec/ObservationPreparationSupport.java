package com.github.sailfishc.flowtest.v2.spec;

import java.util.List;

/**
 * Optional SPI that an {@link ObservationExecutor} may implement to support
 * runtime enrichment of observation specs after fixtures are materialized.
 *
 * <p>The primary use case is deriving {@link TableRouteScope} from fixture instances
 * for dynamic-table entities, so users don't need to write explicit
 * {@code .dynamicTableBy(...)} when observing fixtures.</p>
 */
public interface ObservationPreparationSupport {

    /**
     * Enriches observation specs using materialized fixture data.
     * Called once after fixture preparation, before the first observation capture.
     *
     * @param observations the original observation specs
     * @param fixtures the fixture value resolver
     * @return the enriched observation specs (may return the same list if no changes needed)
     */
    List<ObservationSpec> prepareObservations(
        List<ObservationSpec> observations,
        FixtureValueResolver fixtures
    );
}
