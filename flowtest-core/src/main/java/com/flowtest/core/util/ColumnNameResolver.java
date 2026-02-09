package com.flowtest.core.util;

import com.flowtest.core.assertion.ResultAssert.SerializableFunction;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Unified column name resolution utility.
 * Handles method reference → column name extraction, camelCase → snake_case conversion,
 * and case-insensitive map lookups.
 */
public final class ColumnNameResolver {

    private ColumnNameResolver() {
    }

    /**
     * Extracts the snake_case column name from a getter method reference.
     * Converts getXxx/isXxx to snake_case (e.g., getUserName → user_name).
     *
     * @param getter the method reference
     * @return the snake_case column name, or "unknown" if extraction fails
     */
    public static String extractColumnName(SerializableFunction<?, ?> getter) {
        String propertyName = extractPropertyName(getter);
        if ("unknown".equals(propertyName)) {
            return "unknown";
        }
        return camelToSnake(propertyName);
    }

    /**
     * Extracts the camelCase property name from a getter method reference.
     * Converts getXxx → xxx, isXxx → xxx.
     *
     * @param getter the method reference
     * @return the camelCase property name, or "unknown" if extraction fails
     */
    public static String extractPropertyName(SerializableFunction<?, ?> getter) {
        try {
            Method writeReplace = getter.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            SerializedLambda lambda = (SerializedLambda) writeReplace.invoke(getter);

            String methodName = lambda.getImplMethodName();

            if (methodName.startsWith("get") && methodName.length() > 3) {
                return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
            } else if (methodName.startsWith("is") && methodName.length() > 2) {
                return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
            }
            return methodName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Converts a camelCase string to snake_case.
     *
     * @param camelCase the camelCase string
     * @return the snake_case string
     */
    public static String camelToSnake(String camelCase) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Gets a value from a map with case-insensitive key lookup.
     * Throws AssertionError if the column is not found.
     *
     * @param row the row data map
     * @param columnName the column name to look up
     * @param entityDescription description for error messages (e.g., "entity User" or "new Order")
     * @return the value (may be null if the column exists but has a null value)
     * @throws AssertionError if the column name is not found in the map
     */
    public static Object getValueCaseInsensitive(Map<String, Object> row, String columnName, String entityDescription) {
        Object value = getValueCaseInsensitiveNullable(row, columnName);
        if (value == null && !containsKeyCaseInsensitive(row, columnName)) {
            throw new AssertionError(String.format(
                "Column '%s' not found in %s. Available columns: %s",
                columnName, entityDescription, row.keySet()));
        }
        return value;
    }

    /**
     * Gets a value from a map with case-insensitive key lookup.
     * Returns null if the column is not found (no error).
     *
     * @param row the row data map
     * @param columnName the column name to look up
     * @return the value, or null if not found
     */
    public static Object getValueCaseInsensitiveNullable(Map<String, Object> row, String columnName) {
        // Try exact match first
        if (row.containsKey(columnName)) {
            return row.get(columnName);
        }

        // Try case-insensitive match
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(columnName)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Checks if a map contains a key with case-insensitive matching.
     */
    public static boolean containsKeyCaseInsensitive(Map<String, Object> row, String columnName) {
        if (row.containsKey(columnName)) {
            return true;
        }
        for (String key : row.keySet()) {
            if (key.equalsIgnoreCase(columnName)) {
                return true;
            }
        }
        return false;
    }
}
