package com.github.sailfishc.flowtest.v2.spec;

/**
 * Cleanup policies supported by the observation-first runtime.
 *
 * <p>Currently supported policies:
 * <ul>
 *   <li>{@link #DELETE_INSERTED} — deletes rows inserted during the action (default)</li>
 *   <li>{@link #DELETE_FIXTURE} — deletes only fixture data; act-inserted data is retained</li>
 *   <li>{@link #RESTORE_BEFORE_IMAGE} — restores the full before-state (deletes inserts, restores deletes, reverts modifications)</li>
 * </ul>
 *
 * <p>Not yet supported (will fail at compile time):
 * <ul>
 *   <li>{@link #ROLLBACK} — reserved for future external transaction manager integration</li>
 *   <li>{@link #CUSTOM_COMPENSATOR} — reserved for future cleanup SPI</li>
 * </ul>
 */
public enum CleanupPolicy {
    /** Reserved — not yet implemented. Requires external transaction boundary integration. */
    ROLLBACK,
    /** Deletes rows inserted during the action. This is the default policy. */
    DELETE_INSERTED,
    /** Deletes only fixture-prepared data; rows created by the action are retained. */
    DELETE_FIXTURE,
    /** Restores the complete before-state: undoes inserts, restores deletes, reverts modifications. */
    RESTORE_BEFORE_IMAGE,
    /** Reserved — not yet implemented. Requires a cleanup compensator SPI. */
    CUSTOM_COMPENSATOR
}
