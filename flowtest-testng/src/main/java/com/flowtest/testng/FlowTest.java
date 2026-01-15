package com.flowtest.testng;

import com.flowtest.core.lifecycle.CleanupMode;
import org.testng.annotations.Listeners;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class or method to use the FlowTest framework with TestNG.
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
 * public class OrderServiceTest extends AbstractTestNGSpringContextTests {
 *
 *     @Autowired TestFlow flow;
 *     @Autowired OrderService orderService;
 *
 *     @Test
 *     public void testCreateOrder() {
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
 *
 * <p>Note: When using Spring, test classes should extend
 * {@code AbstractTestNGSpringContextTests} or use {@code @ContextConfiguration}
 * for proper Spring context initialization.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Listeners(FlowTestListener.class)
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
