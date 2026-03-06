package com.github.sailfishc.flowtest.v2.assertion;

import com.github.sailfishc.flowtest.v2.spec.ResourceChange;

import java.util.function.Consumer;

/**
 * Factory that converts {@link RowListSpec} and {@link ModifiedRowListSpec} collectors
 * into {@link ResourceChangeAssertion} instances for use with the existing expectation pipeline.
 */
public final class RowListAssertions {

    private RowListAssertions() {
    }

    /**
     * Build a {@link ResourceChangeAssertion} that asserts inserted rows using the given spec.
     */
    public static ResourceChangeAssertion insertedRows(Consumer<RowListSpec> specConsumer) {
        final DefaultRowListSpec spec = new DefaultRowListSpec();
        specConsumer.accept(spec);
        final DefaultRowListSpec.RowListVerifier verifier = spec.buildVerifier();
        return new ResourceChangeAssertion() {
            @Override
            public void verify(ResourceChange change) {
                verifier.verify(change.getInsertedRows());
            }
        };
    }

    /**
     * Build a {@link ResourceChangeAssertion} that asserts deleted rows using the given spec.
     */
    public static ResourceChangeAssertion deletedRows(Consumer<RowListSpec> specConsumer) {
        final DefaultRowListSpec spec = new DefaultRowListSpec();
        specConsumer.accept(spec);
        final DefaultRowListSpec.RowListVerifier verifier = spec.buildVerifier();
        return new ResourceChangeAssertion() {
            @Override
            public void verify(ResourceChange change) {
                verifier.verify(change.getDeletedRows());
            }
        };
    }

    /**
     * Build a {@link ResourceChangeAssertion} that asserts modified rows using the given spec.
     */
    public static ResourceChangeAssertion modifiedRows(Consumer<ModifiedRowListSpec> specConsumer) {
        final DefaultModifiedRowListSpec spec = new DefaultModifiedRowListSpec();
        specConsumer.accept(spec);
        final DefaultModifiedRowListSpec.ModifiedRowListVerifier verifier = spec.buildVerifier();
        return new ResourceChangeAssertion() {
            @Override
            public void verify(ResourceChange change) {
                verifier.verify(change.getModifiedRows());
            }
        };
    }
}
