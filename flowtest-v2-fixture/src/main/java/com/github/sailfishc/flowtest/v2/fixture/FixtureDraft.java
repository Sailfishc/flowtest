package com.github.sailfishc.flowtest.v2.fixture;

import com.github.sailfishc.flowtest.v2.spec.FixtureSpec;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
import com.github.sailfishc.flowtest.v2.spec.TraitContext;

import java.util.function.Supplier;

/**
 * Applies a fixture specification onto a materialized instance.
 */
public final class FixtureDraft<T> {

    private final FixtureSpec<T> spec;

    public FixtureDraft(FixtureSpec<T> spec) {
        this.spec = spec;
    }

    public FixtureSpec<T> getSpec() {
        return spec;
    }

    public T materialize(Supplier<T> factory, TraitContext context) {
        T instance = factory.get();
        if (instance == null) {
            throw new IllegalStateException("Fixture factory returned null for " + spec.getEntityType().getName());
        }
        for (FixtureTrait<? super T> trait : spec.getTraits()) {
            trait.apply(instance, context);
        }
        return instance;
    }
}
