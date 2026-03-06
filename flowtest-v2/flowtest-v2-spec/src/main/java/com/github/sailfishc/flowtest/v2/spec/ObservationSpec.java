package com.github.sailfishc.flowtest.v2.spec;

import java.util.Objects;

/**
 * Immutable declaration of a resource that participates in observation, diffing, and cleanup.
 */
public final class ObservationSpec {

    private final ResourceKind resourceKind;
    private final ObservationMode observationMode;
    private final String resourceName;
    private final Class<?> resourceType;
    private final TableRouteScope tableRouteScope;
    private final RouteScope routeScope;
    private final boolean routeRequired;
    private final FixtureHandle<?> fixtureHandle;

    private ObservationSpec(ResourceKind resourceKind,
                            ObservationMode observationMode,
                            String resourceName,
                            Class<?> resourceType,
                            TableRouteScope tableRouteScope,
                            RouteScope routeScope,
                            boolean routeRequired,
                            FixtureHandle<?> fixtureHandle) {
        this.resourceKind = Objects.requireNonNull(resourceKind, "resourceKind must not be null");
        this.observationMode = Objects.requireNonNull(observationMode, "observationMode must not be null");
        this.resourceName = requireText(resourceName, "resourceName must not be blank");
        this.resourceType = resourceType;
        this.tableRouteScope = tableRouteScope == null ? TableRouteScope.empty() : tableRouteScope;
        this.routeScope = routeScope == null ? RouteScope.empty() : routeScope;
        this.routeRequired = routeRequired;
        this.fixtureHandle = fixtureHandle;
    }

    public static ObservationSpec fixture(FixtureHandle<?> handle) {
        return new ObservationSpec(
            ResourceKind.ENTITY,
            ObservationMode.FIXTURE_BACKED,
            handle.getType().getName(),
            handle.getType(),
            TableRouteScope.empty(),
            RouteScope.empty(),
            false,
            handle
        );
    }

    public static ObservationSpec table(String tableName, RouteScope routeScope, boolean routeRequired) {
        return table(tableName, TableRouteScope.empty(), routeScope, routeRequired);
    }

    public static ObservationSpec table(String tableName,
                                        TableRouteScope tableRouteScope,
                                        RouteScope routeScope,
                                        boolean routeRequired) {
        return new ObservationSpec(
            ResourceKind.TABLE,
            ObservationMode.WATCH_ONLY,
            tableName,
            null,
            tableRouteScope,
            routeScope,
            routeRequired,
            null
        );
    }

    public static ObservationSpec entity(Class<?> entityType, RouteScope routeScope, boolean routeRequired) {
        return entity(entityType, TableRouteScope.empty(), routeScope, routeRequired);
    }

    public static ObservationSpec entity(Class<?> entityType,
                                         TableRouteScope tableRouteScope,
                                         RouteScope routeScope,
                                         boolean routeRequired) {
        return new ObservationSpec(
            ResourceKind.ENTITY,
            ObservationMode.WATCH_ONLY,
            entityType.getName(),
            entityType,
            tableRouteScope,
            routeScope,
            routeRequired,
            null
        );
    }

    public ResourceKind getResourceKind() {
        return resourceKind;
    }

    public ObservationMode getObservationMode() {
        return observationMode;
    }

    public String getResourceName() {
        return resourceName;
    }

    public Class<?> getResourceType() {
        return resourceType;
    }

    public TableRouteScope getTableRouteScope() {
        return tableRouteScope;
    }

    public RouteScope getRouteScope() {
        return routeScope;
    }

    public boolean isRouteRequired() {
        return routeRequired;
    }

    public FixtureHandle<?> getFixtureHandle() {
        return fixtureHandle;
    }

    private static String requireText(String text, String message) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }
}
