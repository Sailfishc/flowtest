package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.assertion.ModifiedRowAssertion;
import com.github.sailfishc.flowtest.v2.assertion.ModifiedRowListSpec;
import com.github.sailfishc.flowtest.v2.assertion.RowAssertion;
import com.github.sailfishc.flowtest.v2.assertion.RowListSpec;

import java.util.function.Consumer;

/**
 * Fluent DSL for declaring expectations on a specific table (or entity resource).
 * Methods eagerly append expectations to the parent {@link ThenSpec}.
 * Call {@link #and()} to return to the parent chain.
 *
 * <pre>{@code
 * .then(t -> t
 *     .table("table_b")
 *         .inserted(2)
 *         .insertedRows(rows -> rows
 *             .sortBy("ID")
 *             .row(0, RowAssertions.columns("A_ID", 1L, "DETAIL", "detail_1"))
 *             .row(1, RowAssertions.columns("A_ID", 1L, "DETAIL", "detail_2"))
 *         )
 *         .and()
 * )
 * }</pre>
 */
public interface TableChangeSpec<R> {

    /**
     * Expect the given number of inserted rows.
     */
    TableChangeSpec<R> inserted(long count);

    /**
     * Expect the given number of deleted rows.
     */
    TableChangeSpec<R> deleted(long count);

    /**
     * Expect the given number of modified rows.
     */
    TableChangeSpec<R> modified(long count);

    /**
     * Assert that at least one inserted row matches the given assertion.
     */
    TableChangeSpec<R> insertedRow(RowAssertion assertion);

    /**
     * Assert that at least one deleted row matches the given assertion.
     */
    TableChangeSpec<R> deletedRow(RowAssertion assertion);

    /**
     * Assert that at least one modified row matches the given assertion.
     */
    TableChangeSpec<R> modifiedRow(ModifiedRowAssertion assertion);

    /**
     * Assert inserted rows using a multi-row collector DSL with sorting and indexed access.
     */
    TableChangeSpec<R> insertedRows(Consumer<RowListSpec> spec);

    /**
     * Assert deleted rows using a multi-row collector DSL with sorting and indexed access.
     */
    TableChangeSpec<R> deletedRows(Consumer<RowListSpec> spec);

    /**
     * Assert modified rows using a multi-row collector DSL with sorting and indexed access.
     */
    TableChangeSpec<R> modifiedRows(Consumer<ModifiedRowListSpec> spec);

    /**
     * Return to the parent {@link ThenSpec} for further chaining.
     */
    ThenSpec<R> and();
}
