package com.github.sailfishc.flowtest.v2.spec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of all observed resources at a single point in time.
 */
public final class ObservationSnapshot {

    private final Map<String, ResourceSnapshot> resources;

    public ObservationSnapshot(List<ResourceSnapshot> resources) {
        Map<String, ResourceSnapshot> indexed = new LinkedHashMap<String, ResourceSnapshot>();
        for (ResourceSnapshot resource : resources) {
            indexed.put(resource.getResourceName(), resource);
        }
        this.resources = Collections.unmodifiableMap(indexed);
    }

    public static ObservationSnapshot empty() {
        return new ObservationSnapshot(Collections.<ResourceSnapshot>emptyList());
    }

    public List<ResourceSnapshot> getResources() {
        return Collections.unmodifiableList(new ArrayList<ResourceSnapshot>(resources.values()));
    }

    public ResourceSnapshot getResource(String resourceName) {
        return resources.get(resourceName);
    }

    public Map<String, ResourceSnapshot> asMap() {
        return resources;
    }
}
