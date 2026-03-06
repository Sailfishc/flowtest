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

    WatchResourceSpec tableBy(String key, Object value);

    WatchResourceSpec tableRoute(TableRouteScope tableRouteScope);
}
