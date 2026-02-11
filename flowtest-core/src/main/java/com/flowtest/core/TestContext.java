package com.flowtest.core;

import com.flowtest.core.snapshot.SnapshotDiff;
import com.flowtest.core.snapshot.TableSnapshot;

import java.util.*;

/**
 * Test context that holds all state for a single test execution.
 * Stores created entities, persisted IDs, snapshots, and execution results.
 */
public class TestContext {

    /** Entities created during arrange phase: Class -> List of entities */
    private final Map<Class<?>, List<Object>> entities = new LinkedHashMap<>();

    /** Aliased entities: alias -> entity */
    private final Map<String, Object> aliasedEntities = new LinkedHashMap<>();

    /** Persisted entity IDs for cleanup: Class -> List of IDs */
    private final Map<Class<?>, List<Object>> persistedIds = new LinkedHashMap<>();

    /** Tables to watch for snapshots */
    private final Set<String> watchedTables = new LinkedHashSet<>();

    /** Before snapshot */
    private Map<String, TableSnapshot> beforeSnapshot;

    /** After snapshot */
    private Map<String, TableSnapshot> afterSnapshot;

    /** Computed diff */
    private SnapshotDiff snapshotDiff;

    /** Exception thrown during act phase */
    private Throwable thrownException;

    /** Return value from act phase */
    private Object actResult;

    /** Full cleanup before snapshot: table name -> TableSnapshot (row-data-based cleanup) */
    private Map<String, TableSnapshot> cleanupBeforeSnapshot;

    /**
     * Cleanup snapshot: table name -> MAX(ID) before test (supports various ID types).
     * @deprecated Use {@link #cleanupBeforeSnapshot} for row-data-based cleanup instead.
     */
    @Deprecated
    private Map<String, Object> cleanupSnapshot = new LinkedHashMap<>();

    /** Mock context (optional, used when flowtest-mockito is on classpath) */
    private Object mockContext;

    /** Sharding key values by table name: table -> sharding key value */
    private final Map<String, Object> shardingKeyValues = new LinkedHashMap<>();

    /** Sharding key column names by table name: table -> column name */
    private final Map<String, String> shardingKeyColumns = new LinkedHashMap<>();

    /**
     * Gets the first entity of the given type.
     *
     * @param entityClass the entity class
     * @param <T> the entity type
     * @return the first entity
     * @throws FlowTestException if no entity of this type exists
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> entityClass) {
        List<Object> list = entities.get(entityClass);
        if (list == null || list.isEmpty()) {
            throw new FlowTestException("No entity of type " + entityClass.getName() + " found in context");
        }
        return (T) list.get(0);
    }

    /**
     * Gets an entity by alias.
     *
     * @param alias the alias
     * @param entityClass the expected type
     * @param <T> the entity type
     * @return the entity
     * @throws FlowTestException if no entity with this alias exists
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String alias, Class<T> entityClass) {
        Object entity = aliasedEntities.get(alias);
        if (entity == null) {
            throw new FlowTestException("No entity with alias '" + alias + "' found in context");
        }
        if (!entityClass.isInstance(entity)) {
            throw new FlowTestException("Entity with alias '" + alias + "' is not of type " + entityClass.getName());
        }
        return (T) entity;
    }

    /**
     * Gets all entities of the given type.
     *
     * @param entityClass the entity class
     * @param <T> the entity type
     * @return list of entities (empty if none)
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getAll(Class<T> entityClass) {
        List<Object> list = entities.get(entityClass);
        if (list == null) {
            return Collections.emptyList();
        }
        return (List<T>) new ArrayList<>(list);
    }

    /**
     * Gets the entity at the given index.
     *
     * @param entityClass the entity class
     * @param index the index
     * @param <T> the entity type
     * @return the entity at the index
     * @throws FlowTestException if index is out of bounds
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> entityClass, int index) {
        List<Object> list = entities.get(entityClass);
        if (list == null || index >= list.size()) {
            throw new FlowTestException("No entity of type " + entityClass.getName() + " at index " + index);
        }
        return (T) list.get(index);
    }

    // Internal methods for building context

    public void addEntity(Class<?> type, Object entity) {
        entities.computeIfAbsent(type, k -> new ArrayList<>()).add(entity);
    }

    public void addEntity(String alias, Class<?> type, Object entity) {
        addEntity(type, entity);
        if (alias != null && !alias.isEmpty()) {
            aliasedEntities.put(alias, entity);
        }
    }

    public void recordPersistedId(Class<?> type, Object id) {
        persistedIds.computeIfAbsent(type, k -> new ArrayList<>()).add(id);
    }

    public Map<Class<?>, List<Object>> getPersistedIds() {
        return Collections.unmodifiableMap(persistedIds);
    }

    public void addWatchedTable(String tableName) {
        watchedTables.add(tableName);
    }

    public Set<String> getWatchedTables() {
        return Collections.unmodifiableSet(watchedTables);
    }

    public Map<String, TableSnapshot> getBeforeSnapshot() {
        return beforeSnapshot;
    }

    public void setBeforeSnapshot(Map<String, TableSnapshot> beforeSnapshot) {
        this.beforeSnapshot = beforeSnapshot;
    }

    public Map<String, TableSnapshot> getAfterSnapshot() {
        return afterSnapshot;
    }

    public void setAfterSnapshot(Map<String, TableSnapshot> afterSnapshot) {
        this.afterSnapshot = afterSnapshot;
    }

    public SnapshotDiff getSnapshotDiff() {
        return snapshotDiff;
    }

    public void setSnapshotDiff(SnapshotDiff snapshotDiff) {
        this.snapshotDiff = snapshotDiff;
    }

    public Throwable getThrownException() {
        return thrownException;
    }

    public void setThrownException(Throwable thrownException) {
        this.thrownException = thrownException;
    }

    @SuppressWarnings("unchecked")
    public <T> T getActResult() {
        return (T) actResult;
    }

    public void setActResult(Object actResult) {
        this.actResult = actResult;
    }

    public Map<String, TableSnapshot> getCleanupBeforeSnapshot() {
        return cleanupBeforeSnapshot;
    }

    public void setCleanupBeforeSnapshot(Map<String, TableSnapshot> cleanupBeforeSnapshot) {
        this.cleanupBeforeSnapshot = cleanupBeforeSnapshot;
    }

    /**
     * @deprecated Use {@link #getCleanupBeforeSnapshot()} instead.
     */
    @Deprecated
    public Map<String, Object> getCleanupSnapshot() {
        return Collections.unmodifiableMap(cleanupSnapshot);
    }

    /**
     * @deprecated Use {@link #setCleanupBeforeSnapshot(Map)} instead.
     */
    @Deprecated
    public void setCleanupSnapshot(Map<String, Object> snapshot) {
        this.cleanupSnapshot = snapshot != null ? new LinkedHashMap<>(snapshot) : new LinkedHashMap<>();
    }

    /**
     * Gets the mock context (if flowtest-mockito is used).
     */
    public Object getMockContext() {
        return mockContext;
    }

    /**
     * Sets the mock context.
     */
    public void setMockContext(Object mockContext) {
        this.mockContext = mockContext;
    }

    /**
     * Records a sharding key value for a table.
     *
     * @param tableName the table name
     * @param columnName the sharding key column name
     * @param value the sharding key value
     */
    public void recordShardingKey(String tableName, String columnName, Object value) {
        if (value != null) {
            String tableKey = tableName.toLowerCase();
            shardingKeyValues.put(tableKey, value);
            shardingKeyColumns.put(tableKey, columnName);
        }
    }

    /**
     * Gets the sharding key value for a table.
     *
     * @param tableName the table name
     * @return the sharding key value, or null if not set
     */
    public Object getShardingKeyValue(String tableName) {
        return shardingKeyValues.get(tableName.toLowerCase());
    }

    /**
     * Gets the sharding key column name for a table.
     *
     * @param tableName the table name
     * @return the sharding key column name, or null if not set
     */
    public String getShardingKeyColumn(String tableName) {
        return shardingKeyColumns.get(tableName.toLowerCase());
    }

    /**
     * Returns all sharding key values by table name.
     *
     * @return unmodifiable map of table name to sharding key value
     */
    public Map<String, Object> getShardingKeyValues() {
        return Collections.unmodifiableMap(shardingKeyValues);
    }

    /**
     * Returns all sharding key column names by table name.
     *
     * @return unmodifiable map of table name to sharding key column name
     */
    public Map<String, String> getShardingKeyColumns() {
        return Collections.unmodifiableMap(shardingKeyColumns);
    }

    /**
     * Checks if a table has a sharding key configured.
     *
     * @param tableName the table name
     * @return true if sharding key is configured for this table
     */
    public boolean hasShardingKey(String tableName) {
        return shardingKeyValues.containsKey(tableName.toLowerCase());
    }

    /**
     * Clears all state in this context.
     */
    public void clear() {
        entities.clear();
        aliasedEntities.clear();
        persistedIds.clear();
        watchedTables.clear();
        beforeSnapshot = null;
        afterSnapshot = null;
        snapshotDiff = null;
        thrownException = null;
        actResult = null;
        cleanupBeforeSnapshot = null;
        cleanupSnapshot.clear();
        mockContext = null;
        shardingKeyValues.clear();
        shardingKeyColumns.clear();
    }
}
