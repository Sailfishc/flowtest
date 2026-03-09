package com.github.sailfishc.flowtest.v2.testng;

import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutor;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutorProvider;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutorResolutionSupport;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutors;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * TestNG listener that resolves and injects {@link ScenarioExecutor}.
 *
 * <p>This listener automatically resolves ScenarioExecutor in the following order:</p>
 * <ol>
 *   <li>Spring ApplicationContext (if the test is a Spring Boot test)</li>
 *   <li>Test class field of type ScenarioExecutor</li>
 *   <li>{@link ScenarioExecutorProvider} implementation (deprecated)</li>
 * </ol>
 *
 * <p>For Spring Boot tests, simply annotate with {@code @Listeners(FlowTestV2Listener.class)}
 * and the framework will automatically obtain ScenarioExecutor from the Spring container.
 * No need to inject or implement any interface.</p>
 */
public final class FlowTestV2Listener implements IInvokedMethodListener {

    private static final ThreadLocal<TestState> STATE_HOLDER = new ThreadLocal<TestState>();

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        if (!method.isTestMethod()) {
            return;
        }
        Object instance = testResult.getInstance();
        if (instance == null) {
            return;
        }
        try {
            ScenarioExecutor executor = resolveExecutor(instance);
            if (executor == null) {
                throw new IllegalStateException("No ScenarioExecutor available. "
                    + "For Spring Boot tests, ensure @SpringBootTest is configured. "
                    + "For non-Spring tests, declare a ScenarioExecutor field or implement "
                    + ScenarioExecutorProvider.class.getName() + " (deprecated).");
            }
            List<InjectedField> injectedFields = injectAnnotatedFields(instance, executor);
            ScenarioExecutors.bind(executor);
            STATE_HOLDER.set(new TestState(executor, injectedFields));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to prepare FlowTest V2 TestNG integration", ex);
        }
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        if (!method.isTestMethod()) {
            return;
        }
        TestState state = STATE_HOLDER.get();
        if (state == null) {
            return;
        }
        try {
            for (InjectedField injectedField : state.injectedFields) {
                injectedField.restore();
            }
        } finally {
            STATE_HOLDER.remove();
            ScenarioExecutors.clear();
        }
    }

    public static ScenarioExecutor currentExecutor() {
        return ScenarioExecutors.current();
    }

    private ScenarioExecutor resolveExecutor(Object instance) throws Exception {
        // 1. Try to get from Spring ApplicationContext (highest priority)
        ScenarioExecutor fromSpring = ScenarioExecutorResolutionSupport.resolveFromSpringContext(null, instance);
        if (fromSpring != null) {
            return fromSpring;
        }

        // 2. Try to find a ScenarioExecutor field in the test class
        ScenarioExecutor fromField = ScenarioExecutorResolutionSupport.findScenarioExecutor(instance);
        if (fromField != null) {
            return fromField;
        }

        // 3. Fallback to ScenarioExecutorProvider (deprecated)
        return ScenarioExecutorResolutionSupport.resolveFromProvider(instance);
    }

    private List<InjectedField> injectAnnotatedFields(Object instance, ScenarioExecutor executor) throws IllegalAccessException {
        List<InjectedField> injectedFields = new ArrayList<InjectedField>();
        Class<?> type = instance.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.isAnnotationPresent(FlowTestV2Executor.class)
                    || !ScenarioExecutor.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object previous = field.get(instance);
                field.set(instance, executor);
                injectedFields.add(new InjectedField(instance, field, previous));
            }
            type = type.getSuperclass();
        }
        return injectedFields;
    }

    private Object getEnclosingInstance(Object instance) {
        return ScenarioExecutorResolutionSupport.getEnclosingInstance(instance);
    }

    private static final class TestState {

        private final ScenarioExecutor executor;
        private final List<InjectedField> injectedFields;

        private TestState(ScenarioExecutor executor, List<InjectedField> injectedFields) {
            this.executor = executor;
            this.injectedFields = injectedFields;
        }
    }

    private static final class InjectedField {

        private final Object target;
        private final Field field;
        private final Object previousValue;

        private InjectedField(Object target, Field field, Object previousValue) {
            this.target = target;
            this.field = field;
            this.previousValue = previousValue;
        }

        private void restore() {
            try {
                field.set(target, previousValue);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("Failed to restore injected ScenarioExecutor field", ex);
            }
        }
    }
}
