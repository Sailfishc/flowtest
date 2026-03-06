package com.github.sailfishc.flowtest.v2.runtime;

/**
 * Thread-bound access to the active {@link ScenarioExecutor}.
 */
public final class ScenarioExecutors {

    private static final ThreadLocal<ScenarioExecutor> CURRENT = new ThreadLocal<ScenarioExecutor>();

    private ScenarioExecutors() {
    }

    public static void bind(ScenarioExecutor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("ScenarioExecutor must not be null");
        }
        CURRENT.set(executor);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static ScenarioExecutor current() {
        ScenarioExecutor executor = CURRENT.get();
        if (executor == null) {
            throw new IllegalStateException("No active ScenarioExecutor is bound to the current thread. "
                + "Use .execute(executor) for manual wiring or enable a test integration that binds the executor "
                + "before calling .run().");
        }
        return executor;
    }
}
