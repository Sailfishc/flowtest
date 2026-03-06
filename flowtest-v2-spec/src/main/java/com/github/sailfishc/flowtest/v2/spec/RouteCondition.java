package com.github.sailfishc.flowtest.v2.spec;

import java.util.Objects;

/**
 * Single routing predicate attached to an observed resource.
 */
public final class RouteCondition {

    private final String columnName;
    private final RouteConditionOperator operator;
    private final Object value;

    public RouteCondition(String columnName, RouteConditionOperator operator, Object value) {
        this.columnName = requireText(columnName, "columnName must not be blank");
        this.operator = Objects.requireNonNull(operator, "operator must not be null");
        this.value = value;
    }

    public static RouteCondition eq(String columnName, Object value) {
        return new RouteCondition(columnName, RouteConditionOperator.EQ, value);
    }

    public String getColumnName() {
        return columnName;
    }

    public RouteConditionOperator getOperator() {
        return operator;
    }

    public Object getValue() {
        return value;
    }

    private static String requireText(String text, String message) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }
}
