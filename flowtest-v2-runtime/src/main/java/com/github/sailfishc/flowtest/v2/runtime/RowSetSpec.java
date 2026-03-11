package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.spec.FixtureBuilder;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;

import java.util.function.Consumer;

/**
 * Batch fixture DSL for declaring multiple rows of the same entity type.
 */
public interface RowSetSpec<T> {

    // --- Trait-based defaults ---

    RowSetSpec<T> defaults(FixtureTrait<? super T>... traits);

    // --- Builder-based defaults ---

    RowSetSpec<T> defaults(Consumer<FixtureBuilder<T>> builder);

    // --- Trait-based rows ---

    RowSetSpec<T> row(FixtureTrait<? super T>... traits);

    RowSetSpec<T> row(String alias, FixtureTrait<? super T>... traits);

    // --- Builder-based rows ---

    RowSetSpec<T> row(Consumer<FixtureBuilder<T>> builder);

    RowSetSpec<T> row(String alias, Consumer<FixtureBuilder<T>> builder);
}
