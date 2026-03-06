package com.github.sailfishc.flowtest.v2.spec;

/**
 * Provides access to materialized fixture values by handle.
 * Used during observation preparation to derive routing metadata from fixtures.
 */
public interface FixtureValueResolver {

    /**
     * Resolves the materialized value for the given fixture handle.
     *
     * @param handle the fixture handle
     * @param <T> the entity type
     * @return the materialized entity
     */
    <T> T resolve(FixtureHandle<T> handle);
}
