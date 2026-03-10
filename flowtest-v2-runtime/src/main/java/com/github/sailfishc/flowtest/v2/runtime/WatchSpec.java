package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;

/**
 * High-level observation DSL that focuses on resources instead of overloaded scope methods.
 */
public interface WatchSpec {

    WatchSpec fixture(FixtureHandle<?> handle);

    WatchSpec fixture(String alias);

    WatchResourceSpec table(String tableName);

    WatchResourceSpec entity(Class<?> entityType);
}
