package com.github.sailfishc.flowtest.v2.observe.rdbms;

/**
 * Resolves a logical table name to a physical table name using a routing value.
 */
public interface DynamicTableNameResolver {

    String resolve(String logicalTableName, Object routingValue);
}
