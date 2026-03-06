package com.github.sailfishc.flowtest.v2.spec;

/**
 * Distinguishes fixtures that already exist before the action from pure watch-only resources.
 */
public enum ObservationMode {
    FIXTURE_BACKED,
    WATCH_ONLY
}
