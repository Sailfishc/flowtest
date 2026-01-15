package com.flowtest.testng;

import com.flowtest.core.TestContext;
import com.flowtest.core.TestFlow;
import com.flowtest.core.lifecycle.CleanupMode;
import com.flowtest.core.lifecycle.CleanupStrategy;
import com.flowtest.core.lifecycle.CompensatingCleanup;
import com.flowtest.core.lifecycle.NoOpCleanup;
import com.flowtest.core.lifecycle.SnapshotBasedCleanup;
import com.flowtest.core.lifecycle.TransactionalCleanup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * TestNG Listener for FlowTest framework.
 * Handles:
 * <ul>
 *   <li>Test context lifecycle (creation and cleanup)</li>
 *   <li>Cleanup strategy selection based on @FlowTest annotation</li>
 *   <li>Snapshot table configuration</li>
 * </ul>
 *
 * <p>This listener is automatically registered when using the {@link FlowTest} annotation.
 */
public class FlowTestListener implements ITestListener {

    private static final Logger log = LoggerFactory.getLogger(FlowTestListener.class);

    /**
     * Thread-local storage for per-test state.
     * Ensures thread safety for parallel test execution.
     */
    private static final ThreadLocal<TestState> stateHolder = new ThreadLocal<>();

    /**
     * Internal class to hold per-test state.
     */
    private static class TestState {
        final TestContext context;
        final CleanupStrategy cleanup;
        final TestFlow flow;

        TestState(TestContext context, CleanupStrategy cleanup, TestFlow flow) {
            this.context = context;
            this.cleanup = cleanup;
            this.flow = flow;
        }
    }

    @Override
    public void onTestStart(ITestResult result) {
        log.debug("FlowTest onTestStart: {}", result.getName());

        // Get FlowTest annotation
        FlowTest annotation = getFlowTestAnnotation(result);
        CleanupMode cleanupMode = annotation != null ? annotation.cleanup() : CleanupMode.TRANSACTION;
        Set<String> snapshotTables = annotation != null ?
            new HashSet<String>(Arrays.asList(annotation.snapshotTables())) : new HashSet<String>();

        // Create test context
        TestContext testContext = new TestContext();
        for (String table : snapshotTables) {
            testContext.addWatchedTable(table);
        }

        // Find TestFlow instance in test class and configure it
        Object testInstance = result.getInstance();
        TestFlow flow = findTestFlow(testInstance);

        if (flow != null) {
            flow.setContext(testContext);
        }

        // Create cleanup strategy
        CleanupStrategy cleanup = createCleanupStrategy(cleanupMode, flow);

        // Store in thread-local
        stateHolder.set(new TestState(testContext, cleanup, flow));

        // Execute before hook
        cleanup.beforeTest(testContext);
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        log.debug("FlowTest onTestSuccess: {}", result.getName());
        performCleanup(result);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        log.debug("FlowTest onTestFailure: {}", result.getName());
        performCleanup(result);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        log.debug("FlowTest onTestSkipped: {}", result.getName());
        performCleanup(result);
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        log.debug("FlowTest onTestFailedButWithinSuccessPercentage: {}", result.getName());
        performCleanup(result);
    }

    @Override
    public void onStart(ITestContext context) {
        // No-op: class-level lifecycle not needed
    }

    @Override
    public void onFinish(ITestContext context) {
        // No-op: class-level lifecycle not needed
    }

    /**
     * Performs cleanup after test execution.
     * Called from all terminal test states (success, failure, skipped).
     */
    private void performCleanup(ITestResult result) {
        TestState state = stateHolder.get();
        if (state == null) {
            log.debug("No FlowTest state found for cleanup: {}", result.getName());
            return;
        }

        try {
            // Execute after hook (cleanup)
            if (state.cleanup != null && state.context != null) {
                state.cleanup.afterTest(state.context);
            }
        } catch (Exception e) {
            log.warn("Error during FlowTest cleanup: {}", e.getMessage(), e);
        } finally {
            // Clear TestFlow context
            if (state.flow != null) {
                state.flow.clearContext();
            }

            // Remove from thread-local
            stateHolder.remove();
        }
    }

    /**
     * Gets the @FlowTest annotation from the test method or class.
     */
    private FlowTest getFlowTestAnnotation(ITestResult result) {
        // Check method first
        Method method = result.getMethod().getConstructorOrMethod().getMethod();
        if (method != null) {
            FlowTest methodAnnotation = method.getAnnotation(FlowTest.class);
            if (methodAnnotation != null) {
                return methodAnnotation;
            }
        }

        // Check class
        Class<?> testClass = result.getTestClass().getRealClass();
        return testClass.getAnnotation(FlowTest.class);
    }

    /**
     * Finds the TestFlow field in the test instance.
     * For nested classes, also searches the enclosing class instance.
     */
    private TestFlow findTestFlow(Object testInstance) {
        if (testInstance == null) {
            return null;
        }

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
        } catch (NoSuchFieldException e) {
            log.debug("Could not find enclosing instance field: {}", e.getMessage());
            return null;
        } catch (IllegalAccessException e) {
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
