package com.github.sailfishc.flowtest.v2.testng.springboot;

import com.github.sailfishc.flowtest.v2.FlowTestV2;
import com.github.sailfishc.flowtest.v2.assertion.RowAssertions;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcEntity;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationRegistry;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutionResult;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutor;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutorProvider;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
import com.github.sailfishc.flowtest.v2.spec.RouteCondition;
import com.github.sailfishc.flowtest.v2.spec.RouteScope;
import com.github.sailfishc.flowtest.v2.testng.FlowTestV2Executor;
import com.github.sailfishc.flowtest.v2.testng.FlowTestV2Listener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Complete copyable example for Spring Boot + TestNG users.
 */
@SpringBootTest(
    classes = FlowTestV2SpringBootTestNgExampleTest.TestApplication.class,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:flowtest_v2_testng_case;MODE=MYSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password="
    }
)
@Listeners(FlowTestV2Listener.class)
public class FlowTestV2SpringBootTestNgExampleTest extends AbstractTestNGSpringContextTests
    implements ScenarioExecutorProvider {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScenarioExecutor springScenarioExecutor;

    @Autowired
    private OrderService orderService;

    @FlowTestV2Executor
    private ScenarioExecutor executor;

    @BeforeMethod
    public void setUpSchema() {
        jdbcTemplate.execute("drop table if exists ft_order");
        jdbcTemplate.execute("drop table if exists ft_user");
        jdbcTemplate.execute("create table ft_user (id bigint primary key, tenant_id bigint, name varchar(64), balance bigint)");
        jdbcTemplate.execute("create table ft_order (id bigint primary key, tenant_id bigint, user_id bigint, status varchar(32))");
    }

    @Test
    public void shouldExecuteScenarioWithSpringBootAndTestNg() throws Exception {
        FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");

        ScenarioExecutionResult<Long> result = FlowTestV2.scenario("spring-boot-testng-create-order")
            .given(g -> g.persist(user,
                idTrait(1L),
                tenantTrait(100L),
                nameTrait("Alice"),
                balanceTrait(100L)))
            .observe(o -> o
                .fixture(user)
                .shardedTable("ft_order", RouteScope.of(RouteCondition.eq("tenant_id", 100L))))
            .when(() -> orderService.createOrder(100L, 1L, 10L))
            .then(t -> t.expectNoException()
                .fixture(user, value -> assertThat(value.getBalance()).isEqualTo(80L))
                .modified(TestUser.class.getName(), 1)
                .inserted("ft_order", 1)
                .insertedRow("ft_order", RowAssertions.allOf(
                    RowAssertions.columnEquals("id", 10L),
                    RowAssertions.columnEquals("tenant_id", 100L),
                    RowAssertions.columnEquals("status", "CREATED")
                )))
            .execute(executor);

        assertThat(result.getResult()).isEqualTo(10L);
        assertThat(queryForLong("select count(*) from ft_user")).isEqualTo(0L);
        assertThat(queryForLong("select count(*) from ft_order where tenant_id = 100")).isEqualTo(0L);
    }

    @Override
    public ScenarioExecutor createScenarioExecutor() {
        return springScenarioExecutor;
    }

    private long queryForLong(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value.longValue();
    }

    private FixtureTrait<TestUser> idTrait(final Long id) {
        return FixtureTrait.of(user -> user.setId(id));
    }

    private FixtureTrait<TestUser> tenantTrait(final Long tenantId) {
        return FixtureTrait.of(user -> user.setTenantId(tenantId));
    }

    private FixtureTrait<TestUser> nameTrait(final String name) {
        return FixtureTrait.of(user -> user.setName(name));
    }

    private FixtureTrait<TestUser> balanceTrait(final Long balance) {
        return FixtureTrait.of(user -> user.setBalance(balance));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        public JdbcObservationRegistry jdbcObservationRegistry() {
            return new JdbcObservationRegistry()
                .registerEntity(TestUser.class)
                .registerTable("ft_order", "id");
        }

        @Bean
        public OrderService orderService(JdbcTemplate jdbcTemplate) {
            return new OrderService(jdbcTemplate);
        }
    }

    static final class OrderService {

        private final JdbcTemplate jdbcTemplate;

        OrderService(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        long createOrder(long tenantId, long userId, long orderId) {
            jdbcTemplate.update("update ft_user set balance = balance - 20 where id = ?", userId);
            jdbcTemplate.update(
                "insert into ft_order(id, tenant_id, user_id, status) values (?, ?, ?, 'CREATED')",
                orderId,
                tenantId,
                userId
            );
            return orderId;
        }
    }

    @JdbcEntity(table = "ft_user", keyColumns = {"id"})
    static final class TestUser {

        private Long id;
        private Long tenantId;
        private String name;
        private Long balance;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Long getBalance() {
            return balance;
        }

        public void setBalance(Long balance) {
            this.balance = balance;
        }
    }
}
