package com.github.sailfishc.flowtest.v2.spec;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Fluent builder for constructing fixture instances.
 * Shared by both inline given declarations and reusable trait definitions.
 *
 * <pre>{@code
 * // Inline usage in given:
 * g.fixture("user", User.class, f -> f
 *     .set(User::setId, 1L)
 *     .set(User::setName, "Alice"))
 *
 * // Reusable trait:
 * FixtureTrait.draft(f -> f
 *     .set(User::setId, 1L)
 *     .apply(UserTraits.balance(100L)))
 * }</pre>
 */
public interface FixtureBuilder<T> {

    /**
     * Set a field value using a setter reference.
     */
    <V> FixtureBuilder<T> set(BiConsumer<T, V> setter, V value);

    /**
     * Apply an existing reusable trait.
     */
    FixtureBuilder<T> apply(FixtureTrait<? super T> trait);

    /**
     * Apply multiple existing reusable traits.
     */
    @SuppressWarnings("unchecked")
    FixtureBuilder<T> apply(FixtureTrait<? super T> first, FixtureTrait<? super T>... more);

    /**
     * Apply a simple mutator without TraitContext.
     */
    FixtureBuilder<T> mutate(Consumer<T> mutator);

    /**
     * Apply a mutator with TraitContext access.
     */
    FixtureBuilder<T> mutate(BiConsumer<T, TraitContext> mutator);
}
