package com.github.sailfishc.flowtest.v2.observe.rdbms;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares relational metadata for an observed entity.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JdbcEntity {

    String table();

    String[] keyColumns();

    String resourceName() default "";
}
