package com.github.sailfishc.flowtest.v2.observe.rdbms;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Property metadata exposed to ignore resolvers.
 */
public final class JdbcPropertyAccess {

    private final Class<?> entityType;
    private final PropertyDescriptor propertyDescriptor;
    private final Field field;

    JdbcPropertyAccess(Class<?> entityType, PropertyDescriptor propertyDescriptor, Field field) {
        this.entityType = entityType;
        this.propertyDescriptor = propertyDescriptor;
        this.field = field;
    }

    public Class<?> getEntityType() {
        return entityType;
    }

    public String getPropertyName() {
        return propertyDescriptor.getName();
    }

    public PropertyDescriptor getPropertyDescriptor() {
        return propertyDescriptor;
    }

    public Field getField() {
        return field;
    }

    public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        return findAnnotation(annotationType) != null;
    }

    public boolean hasAnnotation(String annotationClassName) {
        return findAnnotation(annotationClassName) != null;
    }

    public Annotation findAnnotation(String annotationClassName) {
        Annotation annotation = findByName(field, annotationClassName);
        if (annotation != null) {
            return annotation;
        }
        annotation = findByName(propertyDescriptor.getReadMethod(), annotationClassName);
        if (annotation != null) {
            return annotation;
        }
        return findByName(propertyDescriptor.getWriteMethod(), annotationClassName);
    }

    public <A extends Annotation> A findAnnotation(Class<A> annotationType) {
        A annotation = find(field, annotationType);
        if (annotation != null) {
            return annotation;
        }
        annotation = find(propertyDescriptor.getReadMethod(), annotationType);
        if (annotation != null) {
            return annotation;
        }
        return find(propertyDescriptor.getWriteMethod(), annotationType);
    }

    private Annotation findByName(Field candidate, String annotationClassName) {
        if (candidate == null) {
            return null;
        }
        for (Annotation annotation : candidate.getAnnotations()) {
            if (annotation.annotationType().getName().equals(annotationClassName)) {
                return annotation;
            }
        }
        return null;
    }

    private Annotation findByName(Method candidate, String annotationClassName) {
        if (candidate == null) {
            return null;
        }
        for (Annotation annotation : candidate.getAnnotations()) {
            if (annotation.annotationType().getName().equals(annotationClassName)) {
                return annotation;
            }
        }
        return null;
    }

    private <A extends Annotation> A find(Field candidate, Class<A> annotationType) {
        return candidate == null ? null : candidate.getAnnotation(annotationType);
    }

    private <A extends Annotation> A find(Method candidate, Class<A> annotationType) {
        return candidate == null ? null : candidate.getAnnotation(annotationType);
    }
}
