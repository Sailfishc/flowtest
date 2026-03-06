package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.assertion.FixtureAssertion;
import com.github.sailfishc.flowtest.v2.assertion.ModifiedRowAssertion;
import com.github.sailfishc.flowtest.v2.assertion.ResourceChangeAssertion;
import com.github.sailfishc.flowtest.v2.assertion.ResultAssertion;
import com.github.sailfishc.flowtest.v2.assertion.RowAssertion;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;

/**
 * Collects declarative expectations for the scenario.
 */
public interface ThenSpec<R> {

    ThenSpec<R> expectNoException();

    ThenSpec<R> expectException(Class<? extends Throwable> exceptionType);

    ThenSpec<R> outcome(ResultAssertion<R> assertion);

    ThenSpec<R> inserted(String resourceName, long count);

    ThenSpec<R> deleted(String resourceName, long count);

    ThenSpec<R> modified(String resourceName, long count);

    ThenSpec<R> change(String resourceName, ResourceChangeAssertion assertion);

    ThenSpec<R> insertedRow(String resourceName, RowAssertion assertion);

    ThenSpec<R> deletedRow(String resourceName, RowAssertion assertion);

    ThenSpec<R> modifiedRow(String resourceName, ModifiedRowAssertion assertion);

    <T> ThenSpec<R> fixture(FixtureHandle<T> handle, FixtureAssertion<T> assertion);
}
