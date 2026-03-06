package com.github.sailfishc.flowtest.v2.spec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Collection of route predicates for a single observed resource.
 */
public final class RouteScope {

    private static final RouteScope EMPTY = new RouteScope(Collections.<RouteCondition>emptyList());

    private final List<RouteCondition> conditions;

    private RouteScope(List<RouteCondition> conditions) {
        this.conditions = Collections.unmodifiableList(new ArrayList<RouteCondition>(conditions));
    }

    public static RouteScope empty() {
        return EMPTY;
    }

    public static RouteScope of(RouteCondition... conditions) {
        return new RouteScope(Arrays.asList(conditions));
    }

    public RouteScope append(RouteCondition condition) {
        List<RouteCondition> updated = new ArrayList<RouteCondition>(conditions);
        updated.add(condition);
        return new RouteScope(updated);
    }

    public boolean isEmpty() {
        return conditions.isEmpty();
    }

    public List<RouteCondition> getConditions() {
        return conditions;
    }
}
