package com.github.sailfishc.flowtest.v2.runtime;

import com.github.sailfishc.flowtest.v2.spec.RouteCondition;
import com.github.sailfishc.flowtest.v2.spec.RouteScope;
import com.github.sailfishc.flowtest.v2.spec.TableRouteScope;

/**
 * Configuration for an observed resource (route conditions, dynamic table parameters).
 * Used inside {@code observe(o -> o.table("name", r -> r.route(...)))}.
 */
public interface ObservedResourceSpec {

    ObservedResourceSpec route(String columnName, Object value);

    ObservedResourceSpec route(RouteCondition condition);

    ObservedResourceSpec route(RouteScope routeScope);

    ObservedResourceSpec dynamicTableBy(String key, Object value);

    ObservedResourceSpec dynamicTable(TableRouteScope tableRouteScope);
}
