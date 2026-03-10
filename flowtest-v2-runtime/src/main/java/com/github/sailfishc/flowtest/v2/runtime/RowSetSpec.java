package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;

/**
 * Batch fixture DSL for declaring multiple rows of the same entity type.
 */
public interface RowSetSpec<T> {

    RowSetSpec<T> defaults(FixtureTrait<? super T>... traits);

    RowSetSpec<T> row(FixtureTrait<? super T>... traits);

    RowSetSpec<T> row(String alias, FixtureTrait<? super T>... traits);
}
