package com.github.sailfishc.flowtest.v2.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Shared reflective resolution helpers for test-framework integrations.
 */
public final class ScenarioExecutorResolutionSupport {

    private static final String SPRING_EXECUTOR_BEAN_NAME = "flowTestV2ScenarioExecutor";

    private ScenarioExecutorResolutionSupport() {
    }

    public static ScenarioExecutor resolveFromSpringContext(Object frameworkContext, Object testInstance) {
        ScenarioExecutor fromFramework = resolveFromApplicationContext(resolveFrameworkApplicationContext(frameworkContext));
        if (fromFramework != null) {
            return fromFramework;
        }

        Object current = testInstance;
        while (current != null) {
            ScenarioExecutor fromFieldContext = resolveFromApplicationContext(readField(current, "applicationContext"));
            if (fromFieldContext != null) {
                return fromFieldContext;
            }
            current = getEnclosingInstance(current);
        }
        return null;
    }

    public static ScenarioExecutor findScenarioExecutor(Object testInstance) throws IllegalAccessException {
        Object current = testInstance;
        while (current != null) {
            ScenarioExecutor executor = findScenarioExecutorInInstance(current);
            if (executor != null) {
                return executor;
            }
            current = getEnclosingInstance(current);
        }
        return null;
    }

    public static ScenarioExecutor resolveFromProvider(Object testInstance) throws Exception {
        Object current = testInstance;
        while (current != null) {
            if (current instanceof ScenarioExecutorProvider) {
                return ((ScenarioExecutorProvider) current).createScenarioExecutor();
            }
            current = getEnclosingInstance(current);
        }
        return null;
    }

    public static Object getEnclosingInstance(Object instance) {
        if (instance == null) {
            return null;
        }
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

    private static ScenarioExecutor findScenarioExecutorInInstance(Object instance) throws IllegalAccessException {
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

    private static Object resolveFrameworkApplicationContext(Object frameworkContext) {
        if (frameworkContext == null) {
            return null;
        }
        try {
            Class<?> extensionContextType = Class.forName("org.junit.jupiter.api.extension.ExtensionContext");
            if (!extensionContextType.isInstance(frameworkContext)) {
                return null;
            }
            Class<?> springExtensionType = Class.forName("org.springframework.test.context.junit.jupiter.SpringExtension");
            Method getApplicationContext = springExtensionType.getMethod("getApplicationContext", extensionContextType);
            return getApplicationContext.invoke(null, frameworkContext);
        } catch (ClassNotFoundException ex) {
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static Object readField(Object instance, String fieldName) {
        Class<?> type = instance.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(instance);
            } catch (NoSuchFieldException ex) {
                type = type.getSuperclass();
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    private static ScenarioExecutor resolveFromApplicationContext(Object applicationContext) {
        if (applicationContext == null) {
            return null;
        }

        ScenarioExecutor named = resolveNamedBean(applicationContext);
        if (named != null) {
            return named;
        }
        return resolveTypedBean(applicationContext);
    }

    private static ScenarioExecutor resolveNamedBean(Object applicationContext) {
        try {
            Method containsBean = applicationContext.getClass().getMethod("containsBean", String.class);
            Object present = containsBean.invoke(applicationContext, SPRING_EXECUTOR_BEAN_NAME);
            if (!Boolean.TRUE.equals(present)) {
                return null;
            }
            try {
                Method getBean = applicationContext.getClass().getMethod("getBean", String.class, Class.class);
                Object bean = getBean.invoke(applicationContext, SPRING_EXECUTOR_BEAN_NAME, ScenarioExecutor.class);
                return bean instanceof ScenarioExecutor ? (ScenarioExecutor) bean : null;
            } catch (NoSuchMethodException ex) {
                Method getBean = applicationContext.getClass().getMethod("getBean", String.class);
                Object bean = getBean.invoke(applicationContext, SPRING_EXECUTOR_BEAN_NAME);
                return bean instanceof ScenarioExecutor ? (ScenarioExecutor) bean : null;
            }
        } catch (Exception ex) {
            return null;
        }
    }

    private static ScenarioExecutor resolveTypedBean(Object applicationContext) {
        try {
            Method getBean = applicationContext.getClass().getMethod("getBean", Class.class);
            Object bean = getBean.invoke(applicationContext, ScenarioExecutor.class);
            return bean instanceof ScenarioExecutor ? (ScenarioExecutor) bean : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
