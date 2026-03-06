package com.github.sailfishc.flowtest.v2.observe.rdbms;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the physical table name is derived from a routing property at runtime.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JdbcDynamicTable {

    String property() default "";

    String key() default "";

    /**
     * Deprecated alias kept for backward compatibility with the first dynamic-table implementation.
     */
    @Deprecated
    String column() default "";

    String separator() default "_";
}
