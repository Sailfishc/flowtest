package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.RouteScope;

/**
 * Collects observation scope declarations.
 */
public interface ObserveSpec {

    ObserveSpec fixture(FixtureHandle<?> handle);

    ObserveSpec table(String tableName);

    ObserveSpec table(String tableName, RouteScope routeScope);

    ObserveSpec shardedTable(String tableName, RouteScope routeScope);

    ObserveSpec entity(Class<?> entityType);

    ObserveSpec entity(Class<?> entityType, RouteScope routeScope);

    ObserveSpec shardedEntity(Class<?> entityType, RouteScope routeScope);
}
