package com.github.sailfishc.flowtest.v2.observe.rdbms;

/**
 * Decides whether a bean property should be excluded from JDBC persistence mapping.
 */
public interface JdbcIgnorePropertyResolver {

    boolean isIgnored(JdbcPropertyAccess propertyAccess);
}
