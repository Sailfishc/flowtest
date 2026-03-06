package com.github.sailfishc.flowtest.v2.observe.rdbms;

import com.github.sailfishc.flowtest.v2.spec.RouteScope;

import java.util.Objects;

/**
 * Relational observation request that carries both identity and route information.
 */
public final class RouteAwareObservation {

    private final TableIdentity identity;
    private final RouteScope routeScope;

    public RouteAwareObservation(TableIdentity identity, RouteScope routeScope) {
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
        this.routeScope = routeScope == null ? RouteScope.empty() : routeScope;
    }

    public TableIdentity getIdentity() {
        return identity;
    }

    public RouteScope getRouteScope() {
        return routeScope;
    }
}
