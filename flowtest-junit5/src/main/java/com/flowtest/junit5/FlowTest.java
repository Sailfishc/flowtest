package com.flowtest.junit5;

import com.flowtest.core.lifecycle.CleanupMode;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

/**
 * Marks a test class or method to use the FlowTest framework.
 * This annotation enables:
 * <ul>
 *   <li>Automatic test context management</li>
 *   <li>Database cleanup after each test</li>
 *   <li>Snapshot tracking for database assertions</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * @FlowTest
 * @SpringBootTest
 * class OrderServiceTest {
 *
 *     @Autowired TestFlow flow;
 *     @Autowired OrderService orderService;
 *
 *     @Test
 *     void testCreateOrder() {
 *         flow.arrange()
 *             .add(User.class, UserTraits.vip())
 *             .persist()
 *             .act(() -> orderService.createOrder(...))
 *             .assertThat()
 *                 .noException()
 *                 .dbChanges(db -> db
 *                     .table("t_order").hasNumberOfRows(1)
 *                 );
 *     }
 * }
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(FlowTestExtension.class)
public @interface FlowTest {

    /**
     * The cleanup mode to use after each test.
     * Default is TRANSACTION (rollback).
     */
    CleanupMode cleanup() default CleanupMode.TRANSACTION;

    /**
     * Tables to monitor for snapshot assertions.
     * If empty, tables are discovered from entities used in the test.
     */
    String[] snapshotTables() default {};
}
