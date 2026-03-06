package com.github.sailfishc.flowtest.v2.spec;

import java.util.Objects;

/**
 * Single key/value used to resolve a physical table name from a logical table name.
 */
public final class TableRouteValue {

    private final String key;
    private final Object value;

    private TableRouteValue(String key, Object value) {
        this.key = requireText(key, "key must not be blank");
        this.value = value;
    }

    public static TableRouteValue of(String key, Object value) {
        return new TableRouteValue(key, value);
    }

    public String getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TableRouteValue)) {
            return false;
        }
        TableRouteValue that = (TableRouteValue) other;
        return key.equals(that.key) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return 31 * key.hashCode() + (value == null ? 0 : value.hashCode());
    }

    private static String requireText(String text, String message) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }
}
