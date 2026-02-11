package com.flowtest.core.fixture;

import com.flowtest.core.TestContext;
import com.flowtest.core.assertion.ActPhase;
import com.flowtest.core.persistence.EntityPersister;
import com.flowtest.core.snapshot.SnapshotEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Builder for the Arrange phase of testing.
 * Allows adding entities with traits and persisting them to the database.
 *
 * <p>Example usage:
 * <pre>{@code
 * flow.arrange()
 *     .add(User.class, UserTraits.vip(), UserTraits.balance(100.00))
 *     .add("product", Product.class, ProductTraits.price(50.00))
 *     .persist()
 *     .act(() -> ...)
 * }</pre>
 */
public class ArrangeBuilder {

    private final TestContext context;
    private final EntityPersister persister;
    private final DataFiller dataFiller;
    private final SnapshotEngine snapshotEngine;
    private final List<EntitySpec<?>> entitySpecs = new ArrayList<>();

    public ArrangeBuilder(TestContext context, EntityPersister persister, DataFiller dataFiller) {
        this(context, persister, dataFiller, null);
    }

    public ArrangeBuilder(TestContext context, EntityPersister persister, DataFiller dataFiller, SnapshotEngine snapshotEngine) {
        this.context = context;
        this.persister = persister;
        this.dataFiller = dataFiller;
        this.snapshotEngine = snapshotEngine;
    }

    /**
     * Adds an entity with the given traits.
     *
     * @param entityClass the entity class
     * @param traits the traits to apply
     * @param <T> the entity type
     * @return this builder
     */
    @SafeVarargs
    public final <T> ArrangeBuilder add(Class<T> entityClass, Trait<T>... traits) {
        entitySpecs.add(new EntitySpec<>(entityClass, null, traits));
        return this;
    }

    /**
     * Adds an entity with an alias and the given traits.
     *
     * @param alias the alias for later retrieval
     * @param entityClass the entity class
     * @param traits the traits to apply
     * @param <T> the entity type
     * @return this builder
     */
    @SafeVarargs
    public final <T> ArrangeBuilder add(String alias, Class<T> entityClass, Trait<T>... traits) {
        entitySpecs.add(new EntitySpec<>(entityClass, alias, traits));
        return this;
    }

    /**
     * Adds multiple entities of the same type with the same traits.
     *
     * @param entityClass the entity class
     * @param count number of entities to add
     * @param traits the traits to apply
     * @param <T> the entity type
     * @return this builder
     */
    @SafeVarargs
    public final <T> ArrangeBuilder addMany(Class<T> entityClass, int count, Trait<T>... traits) {
        for (int i = 0; i < count; i++) {
            add(entityClass, traits);
        }
        return this;
    }

    // ==================== Lambda-style methods ====================

    /**
     * Adds an entity configured by a Consumer (Lambda-friendly).
     *
     * <p>Example usage:
     * <pre>{@code
     * flow.arrange()
     *     .add(User.class, user -> {
     *         user.setLevel(UserLevel.VIP);
     *         user.setBalance(BigDecimal.valueOf(1000));
     *     })
     *     .persist();
     * }</pre>
     *
     * @param entityClass the entity class
     * @param configurer the consumer to configure the entity
     * @param <T> the entity type
     * @return this builder
     */
    public <T> ArrangeBuilder add(Class<T> entityClass, Consumer<T> configurer) {
        entitySpecs.add(new EntitySpec<>(entityClass, null, entity -> configurer.accept(entity)));
        return this;
    }

    /**
     * Adds an entity with an alias, configured by a Consumer.
     *
     * @param alias the alias for later retrieval
     * @param entityClass the entity class
     * @param configurer the consumer to configure the entity
     * @param <T> the entity type
     * @return this builder
     */
    public <T> ArrangeBuilder add(String alias, Class<T> entityClass, Consumer<T> configurer) {
        entitySpecs.add(new EntitySpec<>(entityClass, alias, entity -> configurer.accept(entity)));
        return this;
    }

    /**
     * Adds multiple entities with indexed configuration.
     *
     * <p>Example usage:
     * <pre>{@code
     * flow.arrange()
     *     .addMany(User.class, 3, (user, index) -> {
     *         user.setName("User" + index);
     *         user.setBalance(BigDecimal.valueOf(100 * (index + 1)));
     *     })
     *     .persist();
     * // Creates: User0(balance=100), User1(balance=200), User2(balance=300)
     * }</pre>
     *
     * @param entityClass the entity class
     * @param count number of entities to add
     * @param configurer the consumer with index to configure each entity
     * @param <T> the entity type
     * @return this builder
     */
    public <T> ArrangeBuilder addMany(Class<T> entityClass, int count, BiConsumer<T, Integer> configurer) {
        for (int i = 0; i < count; i++) {
            final int index = i;
            add(entityClass, entity -> configurer.accept(entity, index));
        }
        return this;
    }

    /**
     * Persists all added entities to the database and returns the ActPhase.
     *
     * @return the ActPhase for continuing the test flow
     */
    public ActPhase persist() {
        // First pass: build all entities to extract sharding keys before taking snapshot
        List<Object> builtEntities = new ArrayList<>();
        for (EntitySpec<?> spec : entitySpecs) {
            Object entity = buildEntity(spec);
            builtEntities.add(entity);

            // Extract and record sharding key from the entity
            EntityMetadata metadata = new EntityMetadata(spec.getEntityClass());
            if (metadata.hasShardingKey()) {
                Object shardingKeyValue = metadata.getShardingKeyValue(entity);
                context.recordShardingKey(
                    metadata.getTableName(),
                    metadata.getShardingKeyColumnName(),
                    shardingKeyValue
                );
            }

            // Track table for snapshot
            context.addWatchedTable(metadata.getTableName());

            // Register entity metadata to SnapshotEngine so it knows the correct primary key column
            if (snapshotEngine != null) {
                snapshotEngine.withEntityMetadata(spec.getEntityClass());
            }
        }

        // Record cleanup snapshot baseline AFTER extracting sharding keys but BEFORE persisting
        // This ensures snapshot queries can use sharding key conditions
        recordCleanupSnapshot();

        // Second pass: persist all entities
        for (int i = 0; i < entitySpecs.size(); i++) {
            EntitySpec<?> spec = entitySpecs.get(i);
            Object entity = builtEntities.get(i);

            // Persist and record ID
            Object id = persister.persist(entity);
            context.recordPersistedId(spec.getEntityClass(), id);
            context.addEntity(spec.getAlias(), spec.getEntityClass(), entity);
        }

        return new ActPhase(context, persister, snapshotEngine);
    }

    /**
     * Builds entities without persisting (for validation/testing).
     *
     * @return the ActPhase
     */
    public ActPhase build() {
        for (EntitySpec<?> spec : entitySpecs) {
            Object entity = buildEntity(spec);
            context.addEntity(spec.getAlias(), spec.getEntityClass(), entity);
        }
        return new ActPhase(context, persister, snapshotEngine);
    }

    @SuppressWarnings("unchecked")
    private <T> T buildAndPersist(EntitySpec<T> spec) {
        T entity = buildEntity(spec);

        // Persist and record ID
        Object id = persister.persist(entity);
        context.recordPersistedId(spec.getEntityClass(), id);

        return entity;
    }

    private <T> T buildEntity(EntitySpec<T> spec) {
        // Create entity with auto-filled values
        T entity = dataFiller.fill(spec.getEntityClass());

        // Apply traits (overrides auto-filled values)
        spec.applyTraits(entity);

        return entity;
    }

    /**
     * Records cleanup snapshot baseline for all tables.
     * This is called before persist() to enable cleanup of act-produced data.
     * Uses full row-data snapshots for primary-key-type-agnostic cleanup.
     * When sharding keys are present, snapshot queries will be filtered by sharding key.
     */
    private void recordCleanupSnapshot() {
        if (snapshotEngine == null) {
            return;
        }
        if (context.getCleanupBeforeSnapshot() != null) {
            return; // Already recorded
        }

        java.util.Set<String> tables = snapshotEngine.listTableNames();
        java.util.Map<String, com.flowtest.core.snapshot.TableSnapshot> beforeSnapshots =
            snapshotEngine.takeBeforeSnapshot(
                tables,
                context.getShardingKeyColumns(),
                context.getShardingKeyValues()
            );
        context.setCleanupBeforeSnapshot(beforeSnapshots);
    }
}
