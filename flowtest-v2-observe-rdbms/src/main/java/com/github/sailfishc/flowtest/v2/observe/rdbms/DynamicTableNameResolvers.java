package com.github.sailfishc.flowtest.v2.observe.rdbms;

/**
 * Built-in dynamic table name resolvers.
 */
public final class DynamicTableNameResolvers {

    private DynamicTableNameResolvers() {
    }

    public static DynamicTableNameResolver suffix() {
        return suffix("_");
    }

    public static DynamicTableNameResolver suffix(final String separator) {
        final String actualSeparator = requireText(separator, "separator must not be blank");
        return new DynamicTableNameResolver() {
            @Override
            public String resolve(String logicalTableName, Object routingValue) {
                if (routingValue == null) {
                    throw new IllegalArgumentException("Dynamic table routing value must not be null");
                }
                String suffix = String.valueOf(routingValue).trim();
                if (suffix.isEmpty()) {
                    throw new IllegalArgumentException("Dynamic table routing value must not be blank");
                }
                return requireText(logicalTableName, "logicalTableName must not be blank") + actualSeparator + suffix;
            }
        };
    }

    private static String requireText(String text, String message) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }
}
