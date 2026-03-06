package com.github.sailfishc.flowtest.v2.spec;

import java.util.Objects;

/**
 * Before and after images for a modified row.
 */
public final class ModifiedRow {

    private final RowSnapshot before;
    private final RowSnapshot after;

    public ModifiedRow(RowSnapshot before, RowSnapshot after) {
        this.before = Objects.requireNonNull(before, "before must not be null");
        this.after = Objects.requireNonNull(after, "after must not be null");
    }

    public RowSnapshot getBefore() {
        return before;
    }

    public RowSnapshot getAfter() {
        return after;
    }
}
