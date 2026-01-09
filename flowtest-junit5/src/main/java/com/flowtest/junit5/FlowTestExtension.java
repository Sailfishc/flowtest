package com.flowtest.junit5;

import com.flowtest.core.TestContext;
import com.flowtest.core.TestFlow;
import com.flowtest.core.lifecycle.CleanupMode;
import com.flowtest.core.lifecycle.CleanupStrategy;
import com.flowtest.core.lifecycle.CompensatingCleanup;
import com.flowtest.core.lifecycle.NoOpCleanup;
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
     */
    private TestFlow findTestFlow(Object testInstance) {
        Class<?> clazz = testInstance.getClass();

        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (TestFlow.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        return (TestFlow) field.get(testInstance);
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
     * Creates the appropriate cleanup strategy.
     */
    private CleanupStrategy createCleanupStrategy(CleanupMode mode, TestFlow flow) {
        switch (mode) {
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
