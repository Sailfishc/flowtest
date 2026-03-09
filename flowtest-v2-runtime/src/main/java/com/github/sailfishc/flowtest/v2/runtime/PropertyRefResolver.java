package com.github.sailfishc.flowtest.v2.runtime;

import java.beans.Introspector;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;

final class PropertyRefResolver {

    private PropertyRefResolver() {
    }

    static String resolve(PropertyRef<?, ?> ref) {
        if (ref == null) {
            throw new IllegalArgumentException("propertyRef must not be null");
        }
        SerializedLambda lambda = serializedForm(ref);
        String methodName = lambda.getImplMethodName();
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return Introspector.decapitalize(methodName.substring(3));
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return Introspector.decapitalize(methodName.substring(2));
        }
        throw new IllegalArgumentException("Only getter method references are supported, but got " + methodName);
    }

    private static SerializedLambda serializedForm(PropertyRef<?, ?> ref) {
        try {
            Method writeReplace = ref.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            Object replacement = writeReplace.invoke(ref);
            if (!(replacement instanceof SerializedLambda)) {
                throw new IllegalArgumentException("Unsupported property reference implementation: " + ref.getClass().getName());
            }
            return (SerializedLambda) replacement;
        } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException("Only direct getter method references are supported", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to resolve property reference from " + ref.getClass().getName(), ex);
        }
    }
}
