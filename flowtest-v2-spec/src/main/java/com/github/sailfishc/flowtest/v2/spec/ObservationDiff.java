package com.github.sailfishc.flowtest.v2.spec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Diff between two observation snapshots.
 */
public final class ObservationDiff {

    private final Map<String, ResourceChange> changes;

    public ObservationDiff(List<ResourceChange> changes) {
        Map<String, ResourceChange> indexed = new LinkedHashMap<String, ResourceChange>();
        for (ResourceChange change : changes) {
            indexed.put(change.getResourceName(), change);
        }
        this.changes = Collections.unmodifiableMap(indexed);
    }

    public static ObservationDiff empty() {
        return new ObservationDiff(Collections.<ResourceChange>emptyList());
    }

    public static ObservationDiff between(ObservationSnapshot before, ObservationSnapshot after) {
        Set<String> names = new LinkedHashSet<String>();
        names.addAll(before.asMap().keySet());
        names.addAll(after.asMap().keySet());

        List<ResourceChange> changes = new ArrayList<ResourceChange>();
        for (String name : names) {
            ResourceSnapshot beforeResource = before.getResource(name);
            ResourceSnapshot afterResource = after.getResource(name);
            changes.add(ResourceChange.between(
                beforeResource == null ? ResourceSnapshot.empty(name) : beforeResource,
                afterResource == null ? ResourceSnapshot.empty(name) : afterResource
            ));
        }
        return new ObservationDiff(changes);
    }

    public List<ResourceChange> getChanges() {
        return Collections.unmodifiableList(new ArrayList<ResourceChange>(changes.values()));
    }

    public ResourceChange getChange(String resourceName) {
        return changes.get(resourceName);
    }
}
