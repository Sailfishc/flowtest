package com.flowtest.core.routing;

import com.flowtest.core.fixture.EntityMetadata;
import com.flowtest.core.persistence.EntityPersister;
import com.flowtest.core.snapshot.SnapshotEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central routing registry that maps table names to named datasources.
 * Holds per-datasource {@link EntityPersister} and {@link SnapshotEngine} pairs.
 *
 * <p>At construction time, builds a {@code table → datasource} map via:
 * <ol>
 *   <li>Explicit tables from {@link DataSourceRoute} configuration</li>
 *   <li>Auto-discovery via {@link SnapshotEngine#listTableNames()} for routes without explicit tables</li>
 * </ol>
 * First-come-first-served if a table appears in multiple datasources.
 */
public class DataSourceRouter {

    private static final Logger log = LoggerFactory.getLogger(DataSourceRouter.class);

    /** Sentinel name for the default/fallback datasource. */
    public static final String DEFAULT = "__default__";

    private final Map<String, DataSourceRoute> routes;
    private final Map<String, EntityPersister> persisters;
    private final Map<String, SnapshotEngine> snapshotEngines;
    private final Map<String, String> tableToDataSource;
    private final Map<Class<?>, String> entityRouteCache = new ConcurrentHashMap<Class<?>, String>();
    /** Cache for wildcard pattern match results (table lowercase → datasource name). */
    private final Map<String, String> patternCache = new ConcurrentHashMap<String, String>();

    /**
     * Constructs the router and builds the table-to-datasource mapping.
     *
     * @param routes          named routes (may include {@link #DEFAULT})
     * @param persisters      per-datasource persisters (keys match route names)
     * @param snapshotEngines per-datasource snapshot engines (keys match route names)
     */
    public DataSourceRouter(
            Map<String, DataSourceRoute> routes,
            Map<String, EntityPersister> persisters,
            Map<String, SnapshotEngine> snapshotEngines) {
        this.routes = Collections.unmodifiableMap(new LinkedHashMap<String, DataSourceRoute>(routes));
        this.persisters = Collections.unmodifiableMap(new LinkedHashMap<String, EntityPersister>(persisters));
        this.snapshotEngines = Collections.unmodifiableMap(new LinkedHashMap<String, SnapshotEngine>(snapshotEngines));
        this.tableToDataSource = buildTableMapping();
    }

    private Map<String, String> buildTableMapping() {
        Map<String, String> mapping = new LinkedHashMap<String, String>();

        for (Map.Entry<String, DataSourceRoute> entry : routes.entrySet()) {
            String dsName = entry.getKey();
            DataSourceRoute route = entry.getValue();

            if (route.hasExplicitTables()) {
                // Exact table names — register them directly (patterns are handled at resolve time)
                for (String table : route.getTables()) {
                    registerTable(mapping, table, dsName);
                }
            } else {
                // Auto-discovery — query database metadata
                SnapshotEngine engine = snapshotEngines.get(dsName);
                if (engine != null) {
                    Set<String> discovered = engine.listTableNames();
                    Set<String> lowered = new LinkedHashSet<String>();
                    for (String t : discovered) {
                        lowered.add(t.toLowerCase());
                    }
                    route.addDiscoveredTables(lowered);
                    for (String table : lowered) {
                        registerTable(mapping, table, dsName);
                    }
                    log.debug("Auto-discovered {} tables for datasource '{}': {}", discovered.size(), dsName, discovered);
                }
            }
        }

        log.info("DataSourceRouter initialized with {} table mappings across {} datasources",
                mapping.size(), routes.size());
        return Collections.unmodifiableMap(mapping);
    }

    private void registerTable(Map<String, String> mapping, String table, String dsName) {
        String lower = table.toLowerCase();
        if (mapping.containsKey(lower)) {
            log.warn("Table '{}' already mapped to datasource '{}', ignoring mapping to '{}'",
                    lower, mapping.get(lower), dsName);
        } else {
            mapping.put(lower, dsName);
        }
    }

    /**
     * Resolves the datasource name for a table.
     * Resolution order:
     * <ol>
     *   <li>Exact match in pre-built table-to-datasource mapping</li>
     *   <li>Wildcard pattern match across all routes (first match wins)</li>
     *   <li>Fallback to {@link #DEFAULT}</li>
     * </ol>
     *
     * @param tableName the table name
     * @return the datasource name, or {@link #DEFAULT} if not found
     */
    public String resolveForTable(String tableName) {
        String lower = tableName.toLowerCase();

        // 1. Exact match
        String ds = tableToDataSource.get(lower);
        if (ds != null) {
            return ds;
        }

        // 2. Wildcard pattern match (cache the result for subsequent lookups)
        ds = patternCache.get(lower);
        if (ds != null) {
            return ds;
        }
        for (Map.Entry<String, DataSourceRoute> entry : routes.entrySet()) {
            if (entry.getValue().matchesPattern(lower)) {
                patternCache.put(lower, entry.getKey());
                return entry.getKey();
            }
        }

        // 3. Fallback
        return DEFAULT;
    }

    /**
     * Resolves the datasource name for an entity class by looking up its table name.
     *
     * @param entityClass the entity class
     * @return the datasource name
     */
    public String resolveForEntity(Class<?> entityClass) {
        return entityRouteCache.computeIfAbsent(entityClass, new java.util.function.Function<Class<?>, String>() {
            @Override
            public String apply(Class<?> cls) {
                String tableName = new EntityMetadata(cls).getTableName();
                return resolveForTable(tableName);
            }
        });
    }

    /**
     * Gets the persister for a given entity class.
     */
    public EntityPersister getPersister(Class<?> entityClass) {
        String dsName = resolveForEntity(entityClass);
        EntityPersister p = persisters.get(dsName);
        return p != null ? p : getDefaultPersister();
    }

    /**
     * Gets the snapshot engine for a given table name.
     */
    public SnapshotEngine getSnapshotEngineForTable(String tableName) {
        String dsName = resolveForTable(tableName);
        SnapshotEngine e = snapshotEngines.get(dsName);
        return e != null ? e : getDefaultSnapshotEngine();
    }

    /**
     * Returns all snapshot engines keyed by datasource name.
     */
    public Map<String, SnapshotEngine> getAllSnapshotEngines() {
        return snapshotEngines;
    }

    /**
     * Returns all persisters keyed by datasource name.
     */
    public Map<String, EntityPersister> getAllPersisters() {
        return persisters;
    }

    /**
     * Returns the default persister (for unmatched entities).
     */
    public EntityPersister getDefaultPersister() {
        EntityPersister p = persisters.get(DEFAULT);
        if (p != null) {
            return p;
        }
        // Fallback to first available
        if (!persisters.isEmpty()) {
            return persisters.values().iterator().next();
        }
        throw new IllegalStateException("No EntityPersister available in DataSourceRouter");
    }

    /**
     * Returns the default snapshot engine (for unmatched tables).
     */
    public SnapshotEngine getDefaultSnapshotEngine() {
        SnapshotEngine e = snapshotEngines.get(DEFAULT);
        if (e != null) {
            return e;
        }
        // Fallback to first available
        if (!snapshotEngines.isEmpty()) {
            return snapshotEngines.values().iterator().next();
        }
        throw new IllegalStateException("No SnapshotEngine available in DataSourceRouter");
    }

    /**
     * Returns the table-to-datasource mapping (for debugging/testing).
     */
    public Map<String, String> getTableToDataSource() {
        return tableToDataSource;
    }

    /**
     * Returns all registered route names.
     */
    public Set<String> getRouteNames() {
        return routes.keySet();
    }
}
