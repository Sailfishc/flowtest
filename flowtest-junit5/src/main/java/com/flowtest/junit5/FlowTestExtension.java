package com.flowtest.junit5;

import com.flowtest.core.TestContext;
import com.flowtest.core.TestFlow;
import com.flowtest.core.lifecycle.CleanupMode;
import com.flowtest.core.lifecycle.CleanupStrategy;
import com.flowtest.core.lifecycle.CompensatingCleanup;
import com.flowtest.core.lifecycle.NoOpCleanup;
import com.flowtest.core.lifecycle.SnapshotBasedCleanup;
import com.flowtest.core.lifecycle.TransactionalCleanup;
import org.junit.jupiter.api.extension.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * JUnit 5 Extension for FlowTest framework.
 * Handles:
 * <ul>
 *   <li>Test context lifecycle (creation and cleanup)</li>
 *   <li>Cleanup strategy selection based on @FlowTest annotation</li>
 *   <li>Snapshot table configuration</li>
 * </ul>
 */
public class FlowTestExtension implements BeforeEachCallback, AfterEachCallback {

    private static final Logger log = LoggerFactory.getLogger(FlowTestExtension.class);

    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(FlowTestExtension.class);

    private static final String CONTEXT_KEY = "flowtest.context";
    private static final String CLEANUP_KEY = "flowtest.cleanup";
    private static final String FLOW_KEY = "flowtest.flow";

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        log.debug("FlowTest beforeEach: {}", context.getDisplayName());

        // Get FlowTest annotation
        FlowTest annotation = getFlowTestAnnotation(context);
        CleanupMode cleanupMode = annotation != null ? annotation.cleanup() : CleanupMode.TRANSACTION;
        Set<String> snapshotTables = annotation != null ?
            new HashSet<>(Arrays.asList(annotation.snapshotTables())) : new HashSet<>();

        // Create test context
        TestContext testContext = new TestContext();
        for (String table : snapshotTables) {
            testContext.addWatchedTable(table);
        }

        // Find TestFlow instance in test class and configure it
        Object testInstance = context.getRequiredTestInstance();
        TestFlow flow = findTestFlow(testInstance);

        if (flow != null) {
            flow.setContext(testContext);
            context.getStore(NAMESPACE).put(FLOW_KEY, flow);
        }

        // Create cleanup strategy
        CleanupStrategy cleanup = createCleanupStrategy(cleanupMode, flow);

        // Store in extension context
        context.getStore(NAMESPACE).put(CONTEXT_KEY, testContext);
        context.getStore(NAMESPACE).put(CLEANUP_KEY, cleanup);

        // Execute before hook
        cleanup.beforeTest(testContext);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        log.debug("FlowTest afterEach: {}", context.getDisplayName());

        TestContext testContext = context.getStore(NAMESPACE).get(CONTEXT_KEY, TestContext.class);
        CleanupStrategy cleanup = context.getStore(NAMESPACE).get(CLEANUP_KEY, CleanupStrategy.class);
        TestFlow flow = context.getStore(NAMESPACE).get(FLOW_KEY, TestFlow.class);

        try {
            // Execute after hook (cleanup)
            if (cleanup != null && testContext != null) {
                cleanup.afterTest(testContext);
            }
        } finally {
            // Clear TestFlow context
            if (flow != null) {
                flow.clearContext();
            }

            // Remove from store
            context.getStore(NAMESPACE).remove(CONTEXT_KEY);
            context.getStore(NAMESPACE).remove(CLEANUP_KEY);
            context.getStore(NAMESPACE).remove(FLOW_KEY);
        }
    }

    /**
     * Gets the @FlowTest annotation from the test method or class.
     */
    private FlowTest getFlowTestAnnotation(ExtensionContext context) {
        // Check method first
        Optional<FlowTest> methodAnnotation = context.getTestMethod()
            .map(m -> m.getAnnotation(FlowTest.class));
        if (methodAnnotation.isPresent()) {
            return methodAnnotation.get();
        }

        // Check class
        return context.getTestClass()
            .map(c -> c.getAnnotation(FlowTest.class))
            .orElse(null);
    }

    /**
     * Finds the TestFlow field in the test instance.
     * For nested classes, also searches the enclosing class instance.
     */
    private TestFlow findTestFlow(Object testInstance) {
        // First try to find in the instance itself and its superclasses
        TestFlow flow = findTestFlowInInstance(testInstance);
        if (flow != null) {
            return flow;
        }

        // For nested classes, try to find in the enclosing class instance
        Object enclosingInstance = getEnclosingInstance(testInstance);
        if (enclosingInstance != null) {
            return findTestFlowInInstance(enclosingInstance);
        }

        return null;
    }

    /**
     * Finds the TestFlow field in the given instance and its superclasses.
     */
    private TestFlow findTestFlowInInstance(Object instance) {
        Class<?> clazz = instance.getClass();

        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (TestFlow.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        return (TestFlow) field.get(instance);
                    } catch (IllegalAccessException e) {
                        log.warn("Failed to access TestFlow field: {}", e.getMessage());
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }

        return null;
    }

    /**
     * Gets the enclosing class instance for nested test classes.
     * For non-static inner classes, Java creates a synthetic field "this$0" that
     * references the enclosing instance.
     */
    private Object getEnclosingInstance(Object testInstance) {
        Class<?> clazz = testInstance.getClass();

        // Check if this is a nested class (has an enclosing class)
        if (clazz.getEnclosingClass() == null) {
            return null;
        }

        // Try to find and access the synthetic field "this$0"
        try {
            Field enclosingField = clazz.getDeclaredField("this$0");
            enclosingField.setAccessible(true);
            return enclosingField.get(testInstance);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.debug("Could not access enclosing instance: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Creates the appropriate cleanup strategy.
     */
    private CleanupStrategy createCleanupStrategy(CleanupMode mode, TestFlow flow) {
        switch (mode) {
            case SNAPSHOT_BASED:
                if (flow != null && flow.getPersister() != null && flow.getSnapshotEngine() != null) {
                    return new SnapshotBasedCleanup(flow.getSnapshotEngine(), flow.getPersister());
                }
                log.warn("SNAPSHOT_BASED cleanup requires TestFlow with SnapshotEngine. Falling back to COMPENSATING.");
                // Fall through to COMPENSATING

            case COMPENSATING:
                if (flow != null && flow.getPersister() != null) {
                    return new CompensatingCleanup(flow.getPersister());
                }
                log.warn("COMPENSATING cleanup requires TestFlow with EntityPersister. Falling back to TRANSACTION.");
                return new TransactionalCleanup();

            case NONE:
                return new NoOpCleanup();

            case TRANSACTION:
            default:
                return new TransactionalCleanup();
        }
    }
}
