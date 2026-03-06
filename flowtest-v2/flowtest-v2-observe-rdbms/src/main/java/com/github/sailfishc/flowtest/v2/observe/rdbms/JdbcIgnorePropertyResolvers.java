package com.github.sailfishc.flowtest.v2.observe.rdbms;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Built-in ignore-property resolvers for common ORM annotations.
 */
public final class JdbcIgnorePropertyResolvers {

    private JdbcIgnorePropertyResolvers() {
    }

    public static List<JdbcIgnorePropertyResolver> defaults() {
        List<JdbcIgnorePropertyResolver> resolvers = new ArrayList<JdbcIgnorePropertyResolver>();
        resolvers.add(annotation(JdbcIgnore.class.getName()));
        resolvers.add(annotation("javax.persistence.Transient"));
        resolvers.add(annotation("jakarta.persistence.Transient"));
        resolvers.add(annotation("org.springframework.data.annotation.Transient"));
        resolvers.add(mybatisPlusTableField());
        return Collections.unmodifiableList(resolvers);
    }

    public static JdbcIgnorePropertyResolver annotation(final String annotationClassName) {
        return new JdbcIgnorePropertyResolver() {
            @Override
            public boolean isIgnored(JdbcPropertyAccess propertyAccess) {
                return propertyAccess.hasAnnotation(annotationClassName);
            }
        };
    }

    public static JdbcIgnorePropertyResolver mybatisPlusTableField() {
        return new JdbcIgnorePropertyResolver() {
            @Override
            public boolean isIgnored(JdbcPropertyAccess propertyAccess) {
                Annotation annotation = propertyAccess.findAnnotation("com.baomidou.mybatisplus.annotation.TableField");
                if (annotation == null) {
                    return false;
                }
                try {
                    Method method = annotation.annotationType().getMethod("exist");
                    Object value = method.invoke(annotation);
                    return Boolean.FALSE.equals(value);
                } catch (Exception ex) {
                    throw new IllegalStateException("Failed to inspect @TableField(exist=...) on "
                        + propertyAccess.getEntityType().getName() + "." + propertyAccess.getPropertyName(), ex);
                }
            }
        };
    }
}
