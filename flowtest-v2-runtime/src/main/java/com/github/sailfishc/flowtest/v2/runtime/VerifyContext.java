package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.ObservationDiff;

/**
 * Rich verification context that exposes result, fixtures, and diff data together.
 */
public interface VerifyContext<R> {

    R result();

    Exception failure();

    void success();

    void failure(Class<? extends Throwable> exceptionType);

    ObservationDiff diff();

    <T> FixtureVerifyContext<T> fixture(FixtureHandle<T> handle);

    <T> FixtureVerifyContext<T> fixture(String alias, Class<T> type);

    ResourceVerifyContext resource(String resourceName);

    ResourceVerifyContext table(String tableName);

    ResourceVerifyContext entity(Class<?> entityType);
}
