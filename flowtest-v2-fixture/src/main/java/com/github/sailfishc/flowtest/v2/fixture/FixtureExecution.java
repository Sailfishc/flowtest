package com.github.sailfishc.flowtest.v2.fixture;

import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;

/**
 * Runtime access to prepared fixtures.
 */
public interface FixtureExecution {

    <T> T resolve(FixtureHandle<T> handle);

    <T> T reload(FixtureHandle<T> handle) throws Exception;

    void cleanup() throws Exception;
}
