package com.github.sailfishc.flowtest.v2.spec;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stable handle used to refer to a fixture across given, observe, and then phases.
 */
public final class FixtureHandle<T> {

    private static final AtomicLong ANONYMOUS_COUNTER = new AtomicLong();

    private final Class<T> type;
    private final String name;

    private FixtureHandle(Class<T> type, String name) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.name = name == null || name.trim().isEmpty() ? type.getSimpleName() : name.trim();
    }

    public static <T> FixtureHandle<T> named(Class<T> type, String name) {
        return new FixtureHandle<T>(type, name);
    }

    public static <T> FixtureHandle<T> anonymous(Class<T> type) {
        return new FixtureHandle<T>(type, type.getSimpleName() + "_" + ANONYMOUS_COUNTER.incrementAndGet());
    }

    public Class<T> getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String identifier() {
        return type.getName() + "#" + name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FixtureHandle)) {
            return false;
        }
        FixtureHandle<?> that = (FixtureHandle<?>) other;
        return type.equals(that.type) && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name);
    }

    @Override
    public String toString() {
        return "FixtureHandle{" + identifier() + '}';
    }
}
