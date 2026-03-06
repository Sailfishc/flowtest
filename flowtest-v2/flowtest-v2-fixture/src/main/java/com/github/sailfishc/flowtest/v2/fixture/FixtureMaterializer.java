package com.github.sailfishc.flowtest.v2.fixture;

import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureSpec;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sequentially materializes fixture instances and resolves trait dependencies.
 */
public final class FixtureMaterializer {

    public Map<FixtureHandle<?>, Object> materialize(List<FixtureSpec<?>> fixtures) {
        DefaultTraitContext context = new DefaultTraitContext();
        Map<FixtureHandle<?>, Object> resolved = new LinkedHashMap<FixtureHandle<?>, Object>();
        for (FixtureSpec<?> fixture : fixtures) {
            Object instance = materializeFixture(fixture, context);
            resolved.put(fixture.getHandle(), instance);
            remember(context, fixture, instance);
        }
        return resolved;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object materializeFixture(FixtureSpec<?> fixture, DefaultTraitContext context) {
        FixtureDraft draft = new FixtureDraft(fixture);
        return draft.materialize(newInstanceFactory(fixture.getEntityType()), context);
    }

    private <T> void remember(DefaultTraitContext context, FixtureSpec<T> fixture, Object instance) {
        context.withResolved(fixture.getHandle(), fixture.getEntityType().cast(instance));
    }

    private <T> java.util.function.Supplier<T> newInstanceFactory(final Class<T> entityType) {
        return new java.util.function.Supplier<T>() {
            @Override
            public T get() {
                try {
                    Constructor<T> constructor = entityType.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    return constructor.newInstance();
                } catch (Exception ex) {
                    throw new IllegalStateException("Failed to instantiate fixture type " + entityType.getName(), ex);
                }
            }
        };
    }
}
