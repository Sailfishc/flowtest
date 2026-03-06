package com.github.sailfishc.flowtest.v2.spec;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Composable mutator for fixture instances.
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

    static <T> FixtureTrait<T> of(final Consumer<T> consumer) {
        return new FixtureTrait<T>() {
            @Override
            public void apply(T target, TraitContext context) {
                consumer.accept(target);
            }
        };
    }

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
