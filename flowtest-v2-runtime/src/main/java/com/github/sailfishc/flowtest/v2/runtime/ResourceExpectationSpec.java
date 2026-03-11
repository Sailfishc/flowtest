package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.assertion.ModifiedRowAssertion;
import com.github.sailfishc.flowtest.v2.assertion.ModifiedRowListSpec;
import com.github.sailfishc.flowtest.v2.assertion.ResourceChangeAssertion;
import com.github.sailfishc.flowtest.v2.assertion.RowAssertion;
import com.github.sailfishc.flowtest.v2.assertion.RowListSpec;

import java.util.function.Consumer;

/**
 * Fluent DSL for declaring expectations on a specific resource (table or entity).
 *
 * <pre>{@code
 * .then(t -> t
 *     .table("ft_order", order -> order
 *         .inserted(1)
 *         .modified(1)
 *         .insertedRow(RowAssertions.columnEquals("status", "CREATED"))
 *         .inspect(ctx -> assertThat(ctx.insertedOne().getColumn("id")).isEqualTo(10L))))
 * }</pre>
 */
public interface ResourceExpectationSpec {

    ResourceExpectationSpec inserted(long count);

    ResourceExpectationSpec deleted(long count);

    ResourceExpectationSpec modified(long count);

    ResourceExpectationSpec insertedRow(RowAssertion assertion);

    ResourceExpectationSpec deletedRow(RowAssertion assertion);

    ResourceExpectationSpec modifiedRow(ModifiedRowAssertion assertion);

    ResourceExpectationSpec insertedRows(Consumer<RowListSpec> spec);

    ResourceExpectationSpec deletedRows(Consumer<RowListSpec> spec);

    ResourceExpectationSpec modifiedRows(Consumer<ModifiedRowListSpec> spec);

    ResourceExpectationSpec satisfies(ResourceChangeAssertion assertion);

    ResourceExpectationSpec inspect(ResourceInspection inspection);
}
