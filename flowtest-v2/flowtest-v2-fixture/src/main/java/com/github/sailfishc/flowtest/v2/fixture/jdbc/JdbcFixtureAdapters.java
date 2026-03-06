package com.github.sailfishc.flowtest.v2.fixture.jdbc;

import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcEntityRegistration;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationRegistry;

/**
 * Shared support for deriving fixture adapters from registered JDBC entity metadata.
 */
public final class JdbcFixtureAdapters {

    private JdbcFixtureAdapters() {
    }

    public static FixtureAdapterRegistry fromObservationRegistry(JdbcObservationRegistry observationRegistry) {
        return merge(new FixtureAdapterRegistry(), observationRegistry);
    }

    public static FixtureAdapterRegistry merge(FixtureAdapterRegistry baseRegistry, JdbcObservationRegistry observationRegistry) {
        FixtureAdapterRegistry effective = new FixtureAdapterRegistry().registerAll(baseRegistry);
        for (JdbcEntityRegistration registration : observationRegistry.getEntityRegistrations().values()) {
            if (effective.hasAdapter(registration.getEntityType())) {
                continue;
            }
            effective.register(GenericJdbcFixtureEntityAdapter.of(
                registration.getEntityType(),
                registration.getIdentity().getTableName(),
                registration.getIdentity().getKeyColumns(),
                registration.getPropertyColumns(),
                registration.getIgnoredProperties()
            ));
        }
        return effective;
    }
}
