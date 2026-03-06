package com.github.sailfishc.flowtest.v2.spec;

/**
 * Functional supplier that can throw checked exceptions.
 */
public interface ThrowingSupplier<T> {

    T get() throws Exception;
}
