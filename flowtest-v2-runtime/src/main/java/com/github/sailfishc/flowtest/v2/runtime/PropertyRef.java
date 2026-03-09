package com.github.sailfishc.flowtest.v2.runtime;

import java.io.Serializable;

/**
 * Serializable getter reference used for type-safe property selection.
 */
@FunctionalInterface
public interface PropertyRef<T, R> extends Serializable {

    R apply(T source);
}
