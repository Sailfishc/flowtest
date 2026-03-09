package com.github.sailfishc.flowtest.v2.testng.springboot;

import com.github.sailfishc.flowtest.v2.FlowTestV2;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcEntity;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationRegistry;
import com.github.sailfishc.flowtest.v2.testng.FlowTestV2Listener;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
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

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simplified Spring Boot + TestNG example demonstrating the easiest usage pattern.
 *
 * <h2>Key Points:</h2>
 * <ul>
 *   <li>No need to inject {@code ScenarioExecutor}</li>
 *   <li>No need to implement {@code ScenarioExecutorProvider}</li>
 *   <li>Just add {@code @Listeners(FlowTestV2Listener.class)} and call {@code .run()}</li>
 *   <li>The framework automatically obtains ScenarioExecutor from Spring container</li>
 * </ul>
 *
 * <h2>Setup Requirements:</h2>
 * <ol>
 *   <li>Extend {@code AbstractTestNGSpringContextTests}</li>
 *   <li>Add {@code @SpringBootTest}</li>
 *   <li>Add {@code @Listeners(FlowTestV2Listener.class)}</li>
 *   <li>Register {@code JdbcObservationRegistry} bean for the tables you want to observe</li>
 * </ol>
 */
@SpringBootTest(
    classes = FlowTestV2SimpleSpringBootTest.TestApplication.class,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:simple_test;MODE=MYSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password="
    }
)
@Listeners(FlowTestV2Listener.class)
public class FlowTestV2SimpleSpringBootTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserService userService;

    @BeforeMethod
    public void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS simple_user");
        jdbcTemplate.execute("CREATE TABLE simple_user (id BIGINT PRIMARY KEY, name VARCHAR(64), balance BIGINT)");
    }

    @Test
    public void shouldCreateUserAndVerifyDatabaseChanges() throws Exception {
        FixtureHandle<SimpleUser> user = FixtureHandle.named(SimpleUser.class, "testUser");

        FlowTestV2.scenario("create-user")
            .given(g -> g.persist(user,
                idTrait(1L),
                nameTrait("Alice"),
                balanceTrait(100L)))
            .watch(w -> w
                .fixture(user)
                .table("simple_user"))
            .when(() -> userService.addBalance(1L, 50L))
            .verify(ctx -> {
                ctx.success();
                assertThat(ctx.result()).isEqualTo(150L);
                assertThat(ctx.fixture(user).after().getBalance()).isEqualTo(150L);
                assertThat(ctx.table("simple_user").modifiedCount()).isEqualTo(1L);
            })
            .run();

        assertThat(countUsers()).isEqualTo(0L);
    }

    @Test
    public void shouldHandleMultipleOperations() throws Exception {
        FixtureHandle<SimpleUser> user = FixtureHandle.named(SimpleUser.class, "user1");

        FlowTestV2.scenario("multiple-operations")
            .given(g -> g.persist(user,
                idTrait(1L),
                nameTrait("Alice"),
                balanceTrait(100L)))
            .watch(w -> w.table("simple_user"))
            .when(() -> {
                userService.addBalance(1L, 50L);
                userService.addBalance(1L, 25L);
                return userService.getBalance(1L);
            })
            .verify(ctx -> {
                ctx.success();
                assertThat(ctx.result()).isEqualTo(175L);
                assertThat(ctx.table("simple_user").modifiedCount()).isEqualTo(1L);
            })
            .run();
    }

    private long countUsers() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM simple_user", Long.class);
        return count == null ? 0L : count;
    }

    private FixtureTrait<SimpleUser> idTrait(final Long id) {
        return FixtureTrait.of(user -> user.setId(id));
    }

    private FixtureTrait<SimpleUser> nameTrait(final String name) {
        return FixtureTrait.of(user -> user.setName(name));
    }

    private FixtureTrait<SimpleUser> balanceTrait(final Long balance) {
        return FixtureTrait.of(user -> user.setBalance(balance));
    }

    // ========== Test Application Configuration ==========

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        public JdbcObservationRegistry jdbcObservationRegistry() {
            return new JdbcObservationRegistry()
                .registerEntity(SimpleUser.class)
                .registerTable("simple_user", "id");
        }

        @Bean
        public UserService userService(JdbcTemplate jdbcTemplate) {
            return new UserService(jdbcTemplate);
        }
    }

    // ========== Domain Classes ==========

    @JdbcEntity(table = "simple_user", keyColumns = {"id"})
    static final class SimpleUser {
        private Long id;
        private String name;
        private Long balance;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getBalance() { return balance; }
        public void setBalance(Long balance) { this.balance = balance; }
    }

    // ========== Service Under Test ==========

    static final class UserService {
        private final JdbcTemplate jdbcTemplate;

        UserService(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        long addBalance(Long userId, Long amount) {
            jdbcTemplate.update("UPDATE simple_user SET balance = balance + ? WHERE id = ?", amount, userId);
            return getBalance(userId);
        }

        long getBalance(Long userId) {
            Long balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM simple_user WHERE id = ?", Long.class, userId);
            return balance == null ? 0L : balance;
        }
    }
}
