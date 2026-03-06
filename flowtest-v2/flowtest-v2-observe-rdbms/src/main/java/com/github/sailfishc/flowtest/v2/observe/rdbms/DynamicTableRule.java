package com.github.sailfishc.flowtest.v2.observe.rdbms;

import com.github.sailfishc.flowtest.v2.spec.RouteCondition;
import com.github.sailfishc.flowtest.v2.spec.RouteScope;
import com.github.sailfishc.flowtest.v2.spec.TableRouteScope;
import com.github.sailfishc.flowtest.v2.spec.TableRouteValue;

import java.util.Locale;

final class DynamicTableRule {

    private final String propertyName;
    private final String tableRouteKey;
    private final DynamicTableNameResolver resolver;

    private DynamicTableRule(String propertyName, String tableRouteKey, DynamicTableNameResolver resolver) {
        this.propertyName = hasText(propertyName) ? propertyName.trim() : null;
        this.tableRouteKey = requireText(tableRouteKey, "tableRouteKey must not be blank").toLowerCase(Locale.ENGLISH);
        if (resolver == null) {
            throw new IllegalArgumentException("resolver must not be null");
        }
        this.resolver = resolver;
    }

    public static DynamicTableRule forRouteKey(String tableRouteKey, DynamicTableNameResolver resolver) {
        return new DynamicTableRule(null, tableRouteKey, resolver);
    }

    public static DynamicTableRule forProperty(String propertyName, DynamicTableNameResolver resolver) {
        return new DynamicTableRule(propertyName, propertyName, resolver);
    }

    public static DynamicTableRule forProperty(String propertyName, String tableRouteKey, DynamicTableNameResolver resolver) {
        return new DynamicTableRule(propertyName, tableRouteKey, resolver);
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String getTableRouteKey() {
        return tableRouteKey;
    }

    public String resolveTableName(String logicalTableName, Object routingValue) {
        String resolved = resolver.resolve(logicalTableName, routingValue);
        return requireText(resolved, "resolved table name must not be blank");
    }

    public String resolveTableName(String logicalTableName, TableRouteScope tableRouteScope) {
        if (tableRouteScope == null || tableRouteScope.isEmpty()) {
            throw new IllegalArgumentException("Dynamic table '" + logicalTableName
                + "' requires table route containing key '" + tableRouteKey + "'");
        }
        for (TableRouteValue value : tableRouteScope.getValues()) {
            if (tableRouteKey.equals(value.getKey().trim().toLowerCase(Locale.ENGLISH))) {
                return resolveTableName(logicalTableName, value.getValue());
            }
        }
        throw new IllegalArgumentException("Dynamic table '" + logicalTableName
            + "' requires table route key '" + tableRouteKey + "'");
    }

    public String resolveTableName(String logicalTableName, RouteScope routeScope) {
        if (routeScope == null || routeScope.isEmpty()) {
            throw new IllegalArgumentException("Dynamic table '" + logicalTableName
                + "' requires route scope containing key '" + tableRouteKey + "'");
        }
        for (RouteCondition condition : routeScope.getConditions()) {
            if (tableRouteKey.equals(condition.getColumnName().trim().toLowerCase(Locale.ENGLISH))) {
                return resolveTableName(logicalTableName, condition.getValue());
            }
        }
        throw new IllegalArgumentException("Dynamic table '" + logicalTableName
            + "' requires route condition for key '" + tableRouteKey + "'");
    }

    private static String requireText(String text, String message) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }

    private static boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }
}
