package com.flowtest.core.fixture;

/**
 * ID generation strategies for entity primary keys.
 * 
 * <p>This enum defines how the primary key value is determined during entity persistence:
 * <ul>
 *   <li>{@link #AUTO} - Database auto-generates the ID (e.g., AUTO_INCREMENT)</li>
 *   <li>{@link #INPUT} - Application provides the ID before persistence</li>
 *   <li>{@link #ASSIGN_ID} - Framework assigns a distributed ID (e.g., snowflake)</li>
 *   <li>{@link #ASSIGN_UUID} - Framework assigns a UUID string</li>
 * </ul>
 * 
 * <p>The strategy is automatically detected from annotations like:
 * <ul>
 *   <li>MyBatis-Plus: {@code @TableId(type = IdType.INPUT)}</li>
 *   <li>JPA: {@code @GeneratedValue(strategy = GenerationType.*)}</li>
 * </ul>
 */
public enum IdStrategy {
    
    /**
     * Database auto-generates the ID.
     * The framework will retrieve the generated key after INSERT.
     */
    AUTO,
    
    /**
     * Application provides the ID before persistence.
     * The framework will NOT retrieve generated keys; it will use the existing ID value.
     */
    INPUT,
    
    /**
     * Framework assigns a distributed ID (e.g., snowflake algorithm).
     * Similar to INPUT, but the framework may generate the ID if not provided.
     */
    ASSIGN_ID,
    
    /**
     * Framework assigns a UUID string.
     * Similar to INPUT, but the framework generates a UUID if not provided.
     */
    ASSIGN_UUID
}
