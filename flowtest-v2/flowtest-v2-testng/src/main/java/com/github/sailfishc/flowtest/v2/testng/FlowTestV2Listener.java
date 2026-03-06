package com.github.sailfishc.flowtest.v2.testng;

import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutor;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutorProvider;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * TestNG listener that resolves and injects {@link ScenarioExecutor}.
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
                throw new IllegalStateException("No ScenarioExecutor available. Implement "
                    + ScenarioExecutorProvider.class.getName()
                    + " or assign an existing ScenarioExecutor field before the test method runs.");
            }
            List<InjectedField> injectedFields = injectAnnotatedFields(instance, executor);
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
        }
    }

    public static ScenarioExecutor currentExecutor() {
        TestState state = STATE_HOLDER.get();
        if (state == null) {
            throw new IllegalStateException("No active ScenarioExecutor for the current TestNG thread");
        }
        return state.executor;
    }

    private ScenarioExecutor resolveExecutor(Object instance) throws Exception {
        if (instance instanceof ScenarioExecutorProvider) {
            return ((ScenarioExecutorProvider) instance).createScenarioExecutor();
        }
        ScenarioExecutor direct = findScenarioExecutor(instance);
        if (direct != null) {
            return direct;
        }
        Object enclosing = getEnclosingInstance(instance);
        if (enclosing instanceof ScenarioExecutorProvider) {
            return ((ScenarioExecutorProvider) enclosing).createScenarioExecutor();
        }
        if (enclosing != null) {
            return findScenarioExecutor(enclosing);
        }
        return null;
    }

    private ScenarioExecutor findScenarioExecutor(Object instance) throws IllegalAccessException {
        Class<?> type = instance.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!ScenarioExecutor.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(instance);
                if (value != null) {
                    return (ScenarioExecutor) value;
                }
            }
            type = type.getSuperclass();
        }
        return null;
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
        Class<?> type = instance.getClass();
        if (type.getEnclosingClass() == null) {
            return null;
        }
        try {
            Field field = type.getDeclaredField("this$0");
            field.setAccessible(true);
            return field.get(instance);
        } catch (NoSuchFieldException ex) {
            return null;
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Failed to access enclosing instance", ex);
        }
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
