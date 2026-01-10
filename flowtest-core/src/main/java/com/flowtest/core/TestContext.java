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

    /** Cleanup snapshot: table name -> MAX(ID) before test */
    private Map<String, Long> cleanupSnapshot = new LinkedHashMap<>();

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

    public Map<String, Long> getCleanupSnapshot() {
        return Collections.unmodifiableMap(cleanupSnapshot);
    }

    public void setCleanupSnapshot(Map<String, Long> snapshot) {
        this.cleanupSnapshot = snapshot != null ? new LinkedHashMap<>(snapshot) : new LinkedHashMap<>();
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
        cleanupSnapshot.clear();
    }
}
