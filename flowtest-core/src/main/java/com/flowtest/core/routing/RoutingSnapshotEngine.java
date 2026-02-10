package com.flowtest.core.routing;

import com.flowtest.core.snapshot.SnapshotEngine;
import com.flowtest.core.snapshot.TableSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A {@link SnapshotEngine} that aggregates and delegates across multiple datasources.
 *
 * <p>Overrides all public DB-accessing methods to route operations to the correct
 * per-datasource engine via {@link DataSourceRouter}. Non-DB methods like
 * {@code computeDiff()} work on in-memory snapshots and do not need overriding.
 */
public class RoutingSnapshotEngine extends SnapshotEngine {

    private static final Logger log = LoggerFactory.getLogger(RoutingSnapshotEngine.class);

    private final DataSourceRouter router;

    public RoutingSnapshotEngine(DataSourceRouter router) {
        // Pass the default engine's JdbcTemplate to the parent constructor
        super(router.getDefaultSnapshotEngine().getJdbcTemplate());
        this.router = router;
    }

    @Override
    public Set<String> listTableNames() {
        Set<String> all = new LinkedHashSet<String>();
        for (SnapshotEngine engine : router.getAllSnapshotEngines().values()) {
            all.addAll(engine.listTableNames());
        }
        return all;
    }

    @Override
    public Map<String, TableSnapshot> takeBeforeSnapshot(Set<String> tables) {
        Map<String, TableSnapshot> merged = new LinkedHashMap<String, TableSnapshot>();
        Map<String, Set<String>> grouped = groupTablesByDataSource(tables);

        for (Map.Entry<String, Set<String>> entry : grouped.entrySet()) {
            SnapshotEngine engine = router.getAllSnapshotEngines().get(entry.getKey());
            if (engine == null) {
                engine = router.getDefaultSnapshotEngine();
            }
            merged.putAll(engine.takeBeforeSnapshot(entry.getValue()));
        }

        return merged;
    }

    @Override
    public Map<String, TableSnapshot> takeAfterSnapshot(Set<String> tables) {
        Map<String, TableSnapshot> merged = new LinkedHashMap<String, TableSnapshot>();
        Map<String, Set<String>> grouped = groupTablesByDataSource(tables);

        for (Map.Entry<String, Set<String>> entry : grouped.entrySet()) {
            SnapshotEngine engine = router.getAllSnapshotEngines().get(entry.getKey());
            if (engine == null) {
                engine = router.getDefaultSnapshotEngine();
            }
            merged.putAll(engine.takeAfterSnapshot(entry.getValue()));
        }

        return merged;
    }

    @Override
    public String getIdColumnForTable(String tableName) {
        return router.getSnapshotEngineForTable(tableName).getIdColumnForTable(tableName);
    }

    @Override
    public SnapshotEngine withEntityMetadata(Class<?> entityClass) {
        String dsName = router.resolveForEntity(entityClass);
        SnapshotEngine engine = router.getAllSnapshotEngines().get(dsName);
        if (engine == null) {
            engine = router.getDefaultSnapshotEngine();
        }
        engine.withEntityMetadata(entityClass);
        return this;
    }

    @Override
    public SnapshotEngine withEntityMetadata(Class<?>... entityClasses) {
        for (Class<?> entityClass : entityClasses) {
            withEntityMetadata(entityClass);
        }
        return this;
    }

    @Override
    public int deleteRowsByPrimaryKeys(String table, String idColumn, List<Object> pkValues) {
        return router.getSnapshotEngineForTable(table)
                .deleteRowsByPrimaryKeys(table, idColumn, pkValues);
    }

    /**
     * Groups table names by their owning datasource.
     */
    private Map<String, Set<String>> groupTablesByDataSource(Set<String> tables) {
        Map<String, Set<String>> grouped = new LinkedHashMap<String, Set<String>>();
        for (String table : tables) {
            String dsName = router.resolveForTable(table);
            Set<String> set = grouped.get(dsName);
            if (set == null) {
                set = new LinkedHashSet<String>();
                grouped.put(dsName, set);
            }
            set.add(table);
        }
        return grouped;
    }

    /**
     * Returns the underlying router (for testing/debugging).
     */
    public DataSourceRouter getRouter() {
        return router;
    }
}
