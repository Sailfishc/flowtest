package com.flowtest.core;

import com.flowtest.core.fixture.ArrangeBuilder;
import com.flowtest.core.fixture.DataFiller;
import com.flowtest.core.lifecycle.SnapshotBasedCleanup;
import com.flowtest.core.persistence.EntityPersister;
import com.flowtest.core.snapshot.SnapshotEngine;

import java.util.List;

/**
 * Main entry point for FlowTest framework.
 * This class should be injected via @Autowired in test classes.
 *
 * <p>Example usage:
 * <pre>{@code
 * @FlowTest
 * @SpringBootTest
 * class OrderServiceTest {
 *
 *     @Autowired TestFlow flow;
 *     @Autowired OrderService orderService;
 *
 *     @Test
 *     void testCreateOrder() {
 *         flow.arrange()
 *             .add(User.class, UserTraits.vip(), UserTraits.balance(100.00))
 *             .add(Product.class, ProductTraits.price(50.00))
 *             .persist()
 *
 *             .act(() -> orderService.createOrder(
 *                 flow.get(User.class).getId(),
 *                 flow.get(Product.class).getId()))
 *
 *             .assertThat()
 *                 .noException()
 *                 .dbChanges(db -> db
 *                     .table("t_order").hasNumberOfRows(1)
 *                 );
 *     }
 * }
 * }</pre>
 */
public class TestFlow {

    private final EntityPersister persister;
    private final DataFiller dataFiller;
    private SnapshotEngine snapshotEngine;

    /** Thread-local context for the current test */
    private final ThreadLocal<TestContext> contextHolder = new ThreadLocal<>();

    public TestFlow(EntityPersister persister, DataFiller dataFiller) {
        this.persister = persister;
        this.dataFiller = dataFiller;
    }

    public TestFlow(EntityPersister persister, DataFiller dataFiller, SnapshotEngine snapshotEngine) {
        this.persister = persister;
        this.dataFiller = dataFiller;
        this.snapshotEngine = snapshotEngine;
    }

    /**
     * Starts the Arrange phase of testing.
     *
     * @return the ArrangeBuilder for adding entities
     */
    public ArrangeBuilder arrange() {
        TestContext context = getOrCreateContext();
        return new ArrangeBuilder(context, persister, dataFiller, snapshotEngine);
    }

    /**
     * Gets the first entity of the given type from the current context.
     *
     * @param entityClass the entity class
     * @param <T> the entity type
     * @return the entity
     * @throws FlowTestException if no entity of this type exists
     */
    public <T> T get(Class<T> entityClass) {
        return getContext().get(entityClass);
    }

    /**
     * Gets an entity by alias from the current context.
     *
     * @param alias the alias
     * @param entityClass the expected type
     * @param <T> the entity type
     * @return the entity
     * @throws FlowTestException if no entity with this alias exists
     */
    public <T> T get(String alias, Class<T> entityClass) {
        return getContext().get(alias, entityClass);
    }

    /**
     * Gets all entities of the given type from the current context.
     *
     * @param entityClass the entity class
     * @param <T> the entity type
     * @return list of entities
     */
    public <T> List<T> getAll(Class<T> entityClass) {
        return getContext().getAll(entityClass);
    }

    /**
     * Gets the entity at the given index from the current context.
     *
     * @param entityClass the entity class
     * @param index the index
     * @param <T> the entity type
     * @return the entity
     */
    public <T> T get(Class<T> entityClass, int index) {
        return getContext().get(entityClass, index);
    }

    /**
     * Gets the current test context.
     *
     * @return the context
     * @throws FlowTestException if no context exists
     */
    public TestContext getContext() {
        TestContext context = contextHolder.get();
        if (context == null) {
            throw new FlowTestException("No test context available. " +
                "Make sure the test is properly annotated with @FlowTest");
        }
        return context;
    }

    /**
     * Gets or creates a test context.
     * Used internally by ArrangeBuilder and mock extensions.
     */
    public TestContext getOrCreateContext() {
        TestContext context = contextHolder.get();
        if (context == null) {
            context = new TestContext();
            contextHolder.set(context);
        }
        return context;
    }

    /**
     * Sets the test context.
     * Used by the framework infrastructure.
     */
    public void setContext(TestContext context) {
        contextHolder.set(context);
    }

    /**
     * Clears the test context.
     * Used by the framework infrastructure after each test.
     */
    public void clearContext() {
        TestContext context = contextHolder.get();
        if (context != null) {
            context.clear();
        }
        contextHolder.remove();
    }

    /**
     * Gets the entity persister.
     */
    public EntityPersister getPersister() {
        return persister;
    }

    /**
     * Gets the data filler.
     */
    public DataFiller getDataFiller() {
        return dataFiller;
    }

    /**
     * Gets the snapshot engine.
     */
    public SnapshotEngine getSnapshotEngine() {
        return snapshotEngine;
    }

    /**
     * Sets the snapshot engine.
     */
    public void setSnapshotEngine(SnapshotEngine snapshotEngine) {
        this.snapshotEngine = snapshotEngine;
    }

    /**
     * Manually triggers cleanup of all test data.
     * This includes both persist() phase data and act() phase data.
     *
     * <p>Use this when you need to clean up data before the test ends,
     * or when not using automatic cleanup modes.
     */
    public void cleanup() {
        TestContext context = contextHolder.get();
        if (context == null) {
            return;
        }

        if (snapshotEngine != null && persister != null) {
            SnapshotBasedCleanup cleanup = new SnapshotBasedCleanup(snapshotEngine, persister);
            cleanup.afterTest(context);
        }
    }
}
