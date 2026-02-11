package com.flowtest.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as the sharding key for database sharding scenarios.
 * 
 * <p>When an entity has a sharding key, FlowTest will use its value to filter
 * snapshot queries, allowing the sharding middleware (e.g., ShardingSphere, MyCat)
 * to route queries to the correct physical database/table.
 *
 * <p>Example usage:
 * <pre>{@code
 * @Table(name = "t_order")
 * public class Order {
 *     @Id
 *     private Long id;
 *     
 *     @ShardingKey
 *     private Long userId;  // Sharding key field
 *     
 *     // ...
 * }
 * }</pre>
 *
 * <p>During testing, the sharding key value will be automatically extracted from
 * entities created in the arrange phase and used to filter snapshot queries:
 * <pre>{@code
 * flow.arrange()
 *     .add(Order.class, order -> order.setUserId(12345L))
 *     .persist()
 *     .act(() -> orderService.createOrder(...))
 *     .assertThat()
 *         .dbChanges(db -> db.table("t_order").hasNewRows(1));
 * // Snapshot queries will include: WHERE user_id = 12345
 * }</pre>
 *
 * <p>Notes:
 * <ul>
 *   <li>Only one field per entity can be annotated with {@code @ShardingKey}</li>
 *   <li>If no {@code @ShardingKey} is present, the table is treated as a regular non-sharded table</li>
 *   <li>The column name is resolved using the same rules as other columns
 *       ({@code @Column(name)}, {@code @TableField(value)}, or camelCase to snake_case)</li>
 * </ul>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ShardingKey {

    /**
     * Optional explicit column name for the sharding key.
     * If not specified, the column name is resolved from the field name
     * using the standard naming convention (camelCase to snake_case).
     *
     * @return the column name, or empty string to use default resolution
     */
    String column() default "";
}
