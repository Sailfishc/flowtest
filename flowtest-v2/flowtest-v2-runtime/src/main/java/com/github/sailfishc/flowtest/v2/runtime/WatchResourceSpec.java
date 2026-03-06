package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.spec.RouteCondition;
import com.github.sailfishc.flowtest.v2.spec.RouteScope;
import com.github.sailfishc.flowtest.v2.spec.TableRouteScope;

/**
 * Fluent resource-level watch configuration for route and physical-table concerns.
 */
public interface WatchResourceSpec extends WatchSpec {

    WatchResourceSpec route(String columnName, Object value);

    WatchResourceSpec route(RouteCondition condition);

    WatchResourceSpec route(RouteScope routeScope);

    /**
     * Adds a dynamic-table routing value used to resolve the physical table name.
     */
    WatchResourceSpec dynamicTableBy(String key, Object value);

    /**
     * Adds prebuilt dynamic-table routing values.
     */
    WatchResourceSpec dynamicTable(TableRouteScope tableRouteScope);

    /**
     * Compatibility alias for {@link #dynamicTableBy(String, Object)}.
     */
    WatchResourceSpec tableBy(String key, Object value);

    /**
     * Compatibility alias for {@link #dynamicTable(TableRouteScope)}.
     */
    WatchResourceSpec tableRoute(TableRouteScope tableRouteScope);
}
