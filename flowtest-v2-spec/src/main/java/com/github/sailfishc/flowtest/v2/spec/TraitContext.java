package com.github.sailfishc.flowtest.v2.spec;

/**
 * Read-only context exposed to fixture traits.
 */
public interface TraitContext {

    TraitContext EMPTY = new TraitContext() {
        @Override
        public <T> T resolve(FixtureHandle<T> handle) {
            throw new IllegalStateException("No fixtures are available in the empty trait context");
        }
    };

    <T> T resolve(FixtureHandle<T> handle);
}
