package com.github.sailfishc.flowtest.v2.observe.rdbms;

import com.github.sailfishc.flowtest.v2.spec.CleanupPolicy;
import com.github.sailfishc.flowtest.v2.spec.FixtureValueResolver;
import com.github.sailfishc.flowtest.v2.spec.ObservationDiff;
import com.github.sailfishc.flowtest.v2.spec.ObservationExecutor;
import com.github.sailfishc.flowtest.v2.spec.ObservationPreparationSupport;
import com.github.sailfishc.flowtest.v2.spec.ObservationSnapshot;
import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;
import com.github.sailfishc.flowtest.v2.spec.ResourceChange;
import com.github.sailfishc.flowtest.v2.spec.ResourceSnapshot;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Observation executor that routes each observed table to a configured data source.
 */
public final class MultiDataSourceJdbcObservationExecutor implements ObservationExecutor, ObservationPreparationSupport {

    private final FlowTestDataSourceRegistry dataSourceRegistry;
    private final JdbcObservationRegistry observationRegistry;
    private final Map<String, JdbcObservationExecutor> executorsByName = new LinkedHashMap<String, JdbcObservationExecutor>();

    public MultiDataSourceJdbcObservationExecutor(FlowTestDataSourceRegistry dataSourceRegistry,
                                                  JdbcObservationRegistry observationRegistry) {
        this.dataSourceRegistry = dataSourceRegistry;
        this.observationRegistry = observationRegistry;
    }

    @Override
    public List<ObservationSpec> prepareObservations(List<ObservationSpec> observations,
                                                     FixtureValueResolver fixtures) {
        return ObservationEnricher.enrichFixtureObservations(observations, fixtures, observationRegistry);
    }

    @Override
    public ObservationSnapshot capture(List<ObservationSpec> observations) throws Exception {
        List<ResourceSnapshot> snapshots = new ArrayList<ResourceSnapshot>();
        for (ObservationSpec observation : observations) {
            JdbcObservationExecutor executor = executorFor(observation);
            ObservationSnapshot snapshot = executor.capture(java.util.Collections.singletonList(observation));
            snapshots.add(snapshot.getResource(observation.getResourceName()));
        }
        return new ObservationSnapshot(snapshots);
    }

    @Override
    public void cleanup(List<ObservationSpec> observations, ObservationDiff diff, CleanupPolicy cleanupPolicy) throws Exception {
        Map<String, List<ObservationSpec>> grouped = new LinkedHashMap<String, List<ObservationSpec>>();
        for (ObservationSpec observation : observations) {
            String dataSourceName = dataSourceNameFor(observation);
            List<ObservationSpec> bucket = grouped.get(dataSourceName);
            if (bucket == null) {
                bucket = new ArrayList<ObservationSpec>();
                grouped.put(dataSourceName, bucket);
            }
            bucket.add(observation);
        }

        for (Map.Entry<String, List<ObservationSpec>> entry : grouped.entrySet()) {
            List<ResourceChange> changes = new ArrayList<ResourceChange>();
            for (ObservationSpec observation : entry.getValue()) {
                ResourceChange change = diff.getChange(observation.getResourceName());
                if (change != null) {
                    changes.add(change);
                }
            }
            executorFor(entry.getKey()).cleanup(entry.getValue(), new ObservationDiff(changes), cleanupPolicy);
        }
    }

    private JdbcObservationExecutor executorFor(ObservationSpec observation) {
        return executorFor(dataSourceNameFor(observation));
    }

    private JdbcObservationExecutor executorFor(String dataSourceName) {
        JdbcObservationExecutor executor = executorsByName.get(dataSourceName);
        if (executor != null) {
            return executor;
        }
        DataSource dataSource = dataSourceRegistry.requireDataSourceByName(dataSourceName);
        JdbcObservationExecutor created = new JdbcObservationExecutor(dataSource, observationRegistry);
        executorsByName.put(dataSourceName, created);
        return created;
    }

    private String dataSourceNameFor(ObservationSpec observation) {
        JdbcObservationRegistry.JdbcObservedResource resource = observationRegistry.resolve(observation);
        return dataSourceRegistry.requireDataSourceName(resource.getIdentity().getTableName());
    }
}
