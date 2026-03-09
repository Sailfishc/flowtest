package com.github.sailfishc.flowtest.v2.fixture;

import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;

/**
 * Runtime access to prepared fixtures.
 */
public interface FixtureExecution {

    <T> T resolve(FixtureHandle<T> handle);

    <T> T reload(FixtureHandle<T> handle) throws Exception;

    /**
     * Describes which bean properties should participate in whole-state fixture verification.
     */
    default <T> FixtureStateMetadata describe(FixtureHandle<T> handle) {
        return FixtureStateMetadata.introspect(handle.getType());
    }

    void cleanup() throws Exception;
}
