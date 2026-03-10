package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;

/**
 * Collects fixture declarations.
 */
public interface GivenSpec {

    <T> FixtureHandle<T> persist(Class<T> entityType, FixtureTrait<? super T>... traits);

    <T> GivenSpec persist(String alias, Class<T> entityType, FixtureTrait<? super T>... traits);

    <T> GivenSpec persist(FixtureHandle<T> handle, FixtureTrait<? super T>... traits);

    <T> GivenSpec persistRows(Class<T> entityType, java.util.function.Consumer<RowSetSpec<T>> rows);
}
