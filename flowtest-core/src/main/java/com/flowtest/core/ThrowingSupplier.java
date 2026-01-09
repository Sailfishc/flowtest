package com.flowtest.core;

/**
 * A supplier that can throw checked exceptions.
 *
 * @param <T> the result type
 */
@FunctionalInterface
public interface ThrowingSupplier<T> {

    /**
     * Gets a result, potentially throwing an exception.
     *
     * @return the result
     * @throws Throwable if unable to produce a result
     */
    T get() throws Throwable;
}
