package com.github.sailfishc.flowtest.v2.spec;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Composable mutator for fixture instances.
 *
 * <p>Three ways to create traits:</p>
 * <ul>
 *   <li>{@link #set(BiConsumer, Object)} — single field trait</li>
 *   <li>{@link #mutate(Consumer)} — simple lambda trait</li>
 *   <li>{@link #draft(Consumer)} — multi-field trait via FixtureBuilder</li>
 * </ul>
 */
public interface FixtureTrait<T> {

    void apply(T target, TraitContext context);

    default FixtureTrait<T> and(final FixtureTrait<? super T> next) {
        final FixtureTrait<T> current = this;
        return new FixtureTrait<T>() {
            @Override
            public void apply(T target, TraitContext context) {
                current.apply(target, context);
                next.apply(target, context);
            }
        };
    }

    /**
     * Create a trait from a simple consumer (no TraitContext).
     */
    static <T> FixtureTrait<T> mutate(final Consumer<T> consumer) {
        return new FixtureTrait<T>() {
            @Override
            public void apply(T target, TraitContext context) {
                consumer.accept(target);
            }
        };
    }

    /**
     * Create a trait from a BiConsumer with TraitContext access.
     */
    static <T> FixtureTrait<T> mutate(final BiConsumer<T, TraitContext> consumer) {
        return new FixtureTrait<T>() {
            @Override
            public void apply(T target, TraitContext context) {
                consumer.accept(target, context);
            }
        };
    }

    /**
     * Create a single-field trait using a setter reference.
     *
     * <pre>{@code
     * FixtureTrait.set(User::setBalance, 100L)
     * }</pre>
     */
    static <T, V> FixtureTrait<T> set(final BiConsumer<T, V> setter, final V value) {
        return new FixtureTrait<T>() {
            @Override
            public void apply(T target, TraitContext context) {
                setter.accept(target, value);
            }
        };
    }

    /**
     * Create a multi-field trait via FixtureBuilder.
     *
     * <pre>{@code
     * FixtureTrait.draft(f -> f
     *     .set(User::setId, 1L)
     *     .set(User::setName, "Alice")
     *     .set(User::setBalance, 100L))
     * }</pre>
     */
    static <T> FixtureTrait<T> draft(final Consumer<FixtureBuilder<T>> builder) {
        return new FixtureTrait<T>() {
            @Override
            public void apply(T target, TraitContext context) {
                DefaultFixtureBuilder<T> b = new DefaultFixtureBuilder<T>();
                builder.accept(b);
                b.applyTo(target, context);
            }
        };
    }

    /**
     * Compose multiple traits into one. Alias for {@link #compose(List)}.
     */
    @SafeVarargs
    static <T> FixtureTrait<T> all(FixtureTrait<? super T>... traits) {
        return FixtureTrait.<T>compose(Arrays.<FixtureTrait<? super T>>asList(traits));
    }

    /**
     * @deprecated Use {@link #mutate(Consumer)} instead.
     */
    @Deprecated
    static <T> FixtureTrait<T> of(final Consumer<T> consumer) {
        return mutate(consumer);
    }

    /**
     * @deprecated Use {@link #all(FixtureTrait[])} instead.
     */
    @Deprecated
    @SafeVarargs
    static <T> FixtureTrait<T> compose(FixtureTrait<? super T>... traits) {
        return FixtureTrait.<T>compose(Arrays.<FixtureTrait<? super T>>asList(traits));
    }

    static <T> FixtureTrait<T> compose(List<? extends FixtureTrait<? super T>> traits) {
        return new FixtureTrait<T>() {
            @Override
            public void apply(T target, TraitContext context) {
                for (FixtureTrait<? super T> trait : traits) {
                    trait.apply(target, context);
                }
            }
        };
    }
}
