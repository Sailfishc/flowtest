package com.github.sailfishc.flowtest.v2.spec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable fixture declaration captured during scenario building.
 */
public final class FixtureSpec<T> {

    private final FixtureHandle<T> handle;
    private final Class<T> entityType;
    private final List<FixtureTrait<? super T>> traits;

    public FixtureSpec(FixtureHandle<T> handle, Class<T> entityType, List<FixtureTrait<? super T>> traits) {
        this.handle = Objects.requireNonNull(handle, "handle must not be null");
        this.entityType = Objects.requireNonNull(entityType, "entityType must not be null");
        this.traits = Collections.unmodifiableList(new ArrayList<FixtureTrait<? super T>>(traits));
    }

    public FixtureHandle<T> getHandle() {
        return handle;
    }

    public Class<T> getEntityType() {
        return entityType;
    }

    public List<FixtureTrait<? super T>> getTraits() {
        return traits;
    }
}
