package com.github.sailfishc.flowtest.v2.spec;

/**
 * Cleanup policies supported by the new observation-first runtime.
 */
public enum CleanupPolicy {
    ROLLBACK,
    DELETE_INSERTED,
    DELETE_FIXTURE,
    RESTORE_BEFORE_IMAGE,
    CUSTOM_COMPENSATOR
}
