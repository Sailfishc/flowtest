package com.github.sailfishc.flowtest.v2.fixture;

import java.lang.reflect.Constructor;

/**
 * No-op data filler that creates instances via no-arg constructor without filling any data.
 * Use this to opt out of auto-filling behavior.
 */
public final class NoOpDataFiller implements DataFiller {

    public static final NoOpDataFiller INSTANCE = new NoOpDataFiller();

    @Override
    public <T> T fill(Class<T> entityType) {
        try {
            Constructor<T> constructor = entityType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to instantiate fixture type " + entityType.getName(), ex);
        }
    }
}
