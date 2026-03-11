package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.spec.FixtureBuilder;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;

import java.util.function.Consumer;

/**
 * Collects fixture declarations for the arrange phase.
 */
public interface GivenSpec {

    // --- Trait-based (reusable traits) ---

    <T> FixtureHandle<T> fixture(Class<T> entityType, FixtureTrait<? super T>... traits);

    <T> GivenSpec fixture(String alias, Class<T> entityType, FixtureTrait<? super T>... traits);

    <T> GivenSpec fixture(FixtureHandle<T> handle, FixtureTrait<? super T>... traits);

    // --- Builder-based (inline construction) ---

    <T> FixtureHandle<T> fixture(Class<T> entityType, Consumer<FixtureBuilder<T>> builder);

    <T> GivenSpec fixture(String alias, Class<T> entityType, Consumer<FixtureBuilder<T>> builder);

    <T> GivenSpec fixture(FixtureHandle<T> handle, Consumer<FixtureBuilder<T>> builder);

    // --- Batch rows ---

    <T> GivenSpec fixtures(Class<T> entityType, Consumer<RowSetSpec<T>> rows);
}
