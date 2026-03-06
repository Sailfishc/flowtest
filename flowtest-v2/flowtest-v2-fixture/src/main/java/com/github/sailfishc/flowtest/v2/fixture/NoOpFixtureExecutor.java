package com.github.sailfishc.flowtest.v2.fixture;

import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureSpec;

import java.util.List;

/**
 * Empty fixture executor used when a scenario has no persisted fixtures.
 */
public final class NoOpFixtureExecutor implements FixtureExecutor {

    public static final NoOpFixtureExecutor INSTANCE = new NoOpFixtureExecutor();

    private static final FixtureExecution EMPTY_EXECUTION = new FixtureExecution() {
        @Override
        public <T> T resolve(FixtureHandle<T> handle) {
            throw new IllegalArgumentException("No fixture execution available for handle " + handle.identifier());
        }

        @Override
        public <T> T reload(FixtureHandle<T> handle) {
            return resolve(handle);
        }

        @Override
        public void cleanup() {
        }
    };

    private NoOpFixtureExecutor() {
    }

    @Override
    public FixtureExecution prepare(List<FixtureSpec<?>> fixtures) {
        return EMPTY_EXECUTION;
    }
}
