package com.github.sailfishc.flowtest.v2.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Default implementation of {@link FixtureBuilder}.
 * Collects mutations and applies them in declaration order.
 */
final class DefaultFixtureBuilder<T> implements FixtureBuilder<T> {

    private final List<BiConsumer<T, TraitContext>> mutations = new ArrayList<BiConsumer<T, TraitContext>>();

    @Override
    public <V> FixtureBuilder<T> set(final BiConsumer<T, V> setter, final V value) {
        mutations.add(new BiConsumer<T, TraitContext>() {
            @Override
            public void accept(T target, TraitContext context) {
                setter.accept(target, value);
            }
        });
        return this;
    }

    @Override
    public FixtureBuilder<T> apply(final FixtureTrait<? super T> trait) {
        mutations.add(new BiConsumer<T, TraitContext>() {
            @Override
            public void accept(T target, TraitContext context) {
                trait.apply(target, context);
            }
        });
        return this;
    }

    @Override
    @SafeVarargs
    public final FixtureBuilder<T> apply(FixtureTrait<? super T> first, FixtureTrait<? super T>... more) {
        apply(first);
        if (more != null) {
            for (FixtureTrait<? super T> trait : more) {
                apply(trait);
            }
        }
        return this;
    }

    @Override
    public FixtureBuilder<T> mutate(final Consumer<T> mutator) {
        mutations.add(new BiConsumer<T, TraitContext>() {
            @Override
            public void accept(T target, TraitContext context) {
                mutator.accept(target);
            }
        });
        return this;
    }

    @Override
    public FixtureBuilder<T> mutate(final BiConsumer<T, TraitContext> mutator) {
        mutations.add(mutator);
        return this;
    }

    /**
     * Apply all collected mutations to the target in declaration order.
     */
    void applyTo(T target, TraitContext context) {
        for (BiConsumer<T, TraitContext> mutation : mutations) {
            mutation.accept(target, context);
        }
    }

    /**
     * Convert collected mutations into a single FixtureTrait.
     */
    FixtureTrait<T> toTrait() {
        final List<BiConsumer<T, TraitContext>> captured = new ArrayList<BiConsumer<T, TraitContext>>(mutations);
        return new FixtureTrait<T>() {
            @Override
            public void apply(T target, TraitContext context) {
                for (BiConsumer<T, TraitContext> mutation : captured) {
                    mutation.accept(target, context);
                }
            }
        };
    }
}
