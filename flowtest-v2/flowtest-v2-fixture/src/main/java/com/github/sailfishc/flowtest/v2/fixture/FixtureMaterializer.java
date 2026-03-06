package com.github.sailfishc.flowtest.v2.fixture;

import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sequentially materializes fixture instances and resolves trait dependencies.
 *
 * <p>When a {@link DataFiller} is provided, entities are auto-filled with random data
 * before traits are applied. Traits then override specific fields as needed.</p>
 */
public final class FixtureMaterializer {

    private final DataFiller dataFiller;

    /**
     * Creates a materializer without auto data filling.
     * Entities are created via no-arg constructor only.
     */
    public FixtureMaterializer() {
        this(NoOpDataFiller.INSTANCE);
    }

    /**
     * Creates a materializer with the specified data filler.
     * The filler creates and fills entities before traits are applied.
     *
     * @param dataFiller the data filler to use
     */
    public FixtureMaterializer(DataFiller dataFiller) {
        if (dataFiller == null) {
            throw new IllegalArgumentException("dataFiller must not be null");
        }
        this.dataFiller = dataFiller;
    }

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
        return draft.materialize(fillerFactory(fixture.getEntityType()), context);
    }

    private <T> void remember(DefaultTraitContext context, FixtureSpec<T> fixture, Object instance) {
        context.withResolved(fixture.getHandle(), fixture.getEntityType().cast(instance));
    }

    private <T> java.util.function.Supplier<T> fillerFactory(final Class<T> entityType) {
        return new java.util.function.Supplier<T>() {
            @Override
            public T get() {
                return dataFiller.fill(entityType);
            }
        };
    }
}
