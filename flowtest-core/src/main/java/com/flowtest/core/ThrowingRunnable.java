package com.flowtest.core;

/**
 * A runnable that can throw checked exceptions.
 */
@FunctionalInterface
public interface ThrowingRunnable {

    /**
     * Runs the action, potentially throwing an exception.
     *
     * @throws Throwable if unable to complete
     */
    void run() throws Throwable;
}
