package com.github.sailfishc.flowtest.v2.junit5;

import com.github.sailfishc.flowtest.v2.FlowTestV2;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcEntity;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationRegistry;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutionResult;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simplified Spring Boot + JUnit 5 example demonstrating the easiest usage pattern.
 *
 * <h2>Key Points:</h2>
 * <ul>
 *   <li>No need to inject {@code ScenarioExecutor}</li>
 *   <li>No need to implement {@code ScenarioExecutorProvider}</li>
 *   <li>Just add {@code @FlowTestV2Test} and call {@code .run()}</li>
 *   <li>The framework automatically obtains ScenarioExecutor from Spring container</li>
 * </ul>
 *
 * <h2>Setup Requirements:</h2>
 * <ol>
 *   <li>Add {@code @SpringBootTest}</li>
 *   <li>Add {@code @FlowTestV2Test} (which enables FlowTestV2Extension)</li>
 *   <li>Register {@code JdbcObservationRegistry} bean for the tables you want to observe</li>
 * </ol>
 */
@SpringBootTest(
    classes = FlowTestV2SimpleSpringBootTest.TestApplication.class,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:junit5_simple_test;MODE=MYSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password="
    }
)
@FlowTestV2Test
class FlowTestV2SimpleSpringBootTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderService orderService;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS simple_order");
        jdbcTemplate.execute("CREATE TABLE simple_order (id BIGINT PRIMARY KEY, product_name VARCHAR(64), quantity INT, total_price BIGINT)");
    }

    @Test
    void shouldCreateOrderAndVerifyDatabaseChanges() throws Exception {
        FixtureHandle<SimpleOrder> order = FixtureHandle.named(SimpleOrder.class, "order");

        ScenarioExecutionResult<Long> result = FlowTestV2.scenario("create-order")
            .given(g -> g.persist(order,
                idTrait(1L),
                productTrait("iPhone"),
                quantityTrait(2),
                totalPriceTrait(2000L)))
            .watch(w -> w
                .fixture(order)
                .table("simple_order"))
            .when(() -> orderService.applyDiscount(1L, 10)) // 10% discount
            .verify(ctx -> {
                ctx.success();
                assertThat(ctx.result()).isEqualTo(1800L); // 2000 * 0.9
                assertThat(ctx.fixture(order).after().getTotalPrice()).isEqualTo(1800L);
                assertThat(ctx.table("simple_order").modifiedCount()).isEqualTo(1L);
            })
            .run();

        assertThat(result.getResult()).isEqualTo(1800L);
    }

    @Test
    void shouldHandleOrderCancellation() throws Exception {
        FixtureHandle<SimpleOrder> order = FixtureHandle.named(SimpleOrder.class, "order");

        FlowTestV2.scenario("cancel-order")
            .given(g -> g.persist(order,
                idTrait(1L),
                productTrait("MacBook"),
                quantityTrait(1),
                totalPriceTrait(10000L)))
            .watch(w -> w.table("simple_order"))
            .when(() -> {
                orderService.cancelOrder(1L);
                return null;
            })
            .verify(ctx -> {
                ctx.success();
                assertThat(ctx.table("simple_order").deletedCount()).isEqualTo(1L);
            })
            .run();

        assertThat(countOrders()).isEqualTo(0L);
    }

    private FixtureTrait<SimpleOrder> idTrait(final Long id) {
        return FixtureTrait.of(order -> order.setId(id));
    }

    private FixtureTrait<SimpleOrder> productTrait(final String productName) {
        return FixtureTrait.of(order -> order.setProductName(productName));
    }

    private FixtureTrait<SimpleOrder> quantityTrait(final Integer quantity) {
        return FixtureTrait.of(order -> order.setQuantity(quantity));
    }

    private FixtureTrait<SimpleOrder> totalPriceTrait(final Long totalPrice) {
        return FixtureTrait.of(order -> order.setTotalPrice(totalPrice));
    }

    private long countOrders() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM simple_order", Long.class);
        return count == null ? 0L : count;
    }

    // ========== Test Application Configuration ==========

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        public JdbcObservationRegistry jdbcObservationRegistry() {
            return new JdbcObservationRegistry()
                .registerEntity(SimpleOrder.class)
                .registerTable("simple_order", "id");
        }

        @Bean
        public OrderService orderService(JdbcTemplate jdbcTemplate) {
            return new OrderService(jdbcTemplate);
        }
    }

    // ========== Domain Classes ==========

    @JdbcEntity(table = "simple_order", keyColumns = {"id"})
    static final class SimpleOrder {
        private Long id;
        private String productName;
        private Integer quantity;
        private Long totalPrice;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Long getTotalPrice() { return totalPrice; }
        public void setTotalPrice(Long totalPrice) { this.totalPrice = totalPrice; }
    }

    // ========== Service Under Test ==========

    static final class OrderService {
        private final JdbcTemplate jdbcTemplate;

        OrderService(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        long applyDiscount(Long orderId, int discountPercent) {
            long newPrice = getPrice(orderId) * (100 - discountPercent) / 100;
            jdbcTemplate.update("UPDATE simple_order SET total_price = ? WHERE id = ?", newPrice, orderId);
            return newPrice;
        }

        void cancelOrder(Long orderId) {
            jdbcTemplate.update("DELETE FROM simple_order WHERE id = ?", orderId);
        }

        private long getPrice(Long orderId) {
            Long price = jdbcTemplate.queryForObject(
                "SELECT total_price FROM simple_order WHERE id = ?", Long.class, orderId);
            return price == null ? 0L : price;
        }
    }
}
