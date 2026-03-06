package com.github.sailfishc.flowtest.v2.spec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Composite identity of one observed row.
 */
public final class RowKey {

    private final List<Object> values;

    private RowKey(List<Object> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        this.values = Collections.unmodifiableList(new ArrayList<Object>(values));
    }

    public static RowKey of(Object... values) {
        return new RowKey(Arrays.asList(values));
    }

    public List<Object> getValues() {
        return values;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RowKey)) {
            return false;
        }
        RowKey that = (RowKey) other;
        return values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return "RowKey" + values;
    }
}
