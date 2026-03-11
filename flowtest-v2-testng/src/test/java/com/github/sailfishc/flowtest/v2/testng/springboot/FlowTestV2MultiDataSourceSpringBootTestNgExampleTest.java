package com.github.sailfishc.flowtest.v2.testng.springboot;

import com.github.sailfishc.flowtest.v2.FlowTestV2;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcEntity;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationRegistry;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
import com.github.sailfishc.flowtest.v2.testng.FlowTestV2Listener;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Copyable example for Spring Boot + TestNG + multi-datasource routing.
 */
@SpringBootTest(
    classes = FlowTestV2MultiDataSourceSpringBootTestNgExampleTest.TestApplication.class,
    properties = {
        "flowtest.v2.datasource.default-name=orderDs",
        "flowtest.v2.datasource.bindings[0].name=orderDs",
        "flowtest.v2.datasource.bindings[0].tables[0]=ft_order",
        "flowtest.v2.datasource.bindings[1].name=accountDs",
        "flowtest.v2.datasource.bindings[1].tables[0]=ft_user"
    }
)
@Listeners(FlowTestV2Listener.class)
public class FlowTestV2MultiDataSourceSpringBootTestNgExampleTest extends AbstractTestNGSpringContextTests {

    @Autowired
    @Qualifier("orderDs")
    private DataSource orderDataSource;

    @Autowired
    @Qualifier("accountDs")
    private DataSource accountDataSource;

    @BeforeMethod
    public void setUpSchema() throws Exception {
        executeSql(orderDataSource, "drop table if exists ft_order");
        executeSql(accountDataSource, "drop table if exists ft_user");
        executeSql(orderDataSource, "create table ft_order (id bigint primary key, tenant_id bigint, user_id bigint, status varchar(32))");
        executeSql(accountDataSource, "create table ft_user (id bigint primary key, tenant_id bigint, name varchar(64), balance bigint)");
    }

    @Test
    public void shouldRouteAcrossConfiguredDataSources() throws Exception {
        FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");

        FlowTestV2.scenario("spring-boot-testng-multi-datasource")
            .given(g -> g.fixture(user,
                idTrait(1L),
                tenantTrait(100L),
                nameTrait("Alice"),
                balanceTrait(100L)))
            .observe(o -> o.table("ft_order", r -> r.route("tenant_id", 100L)))
            .when(() -> {
                executeSql(accountDataSource, "update ft_user set balance = 80 where id = 1");
                executeSql(orderDataSource, "insert into ft_order(id, tenant_id, user_id, status) values (10, 100, 1, 'CREATED')");
                return 10L;
            })
            .then(t -> t
                .success()
                .returns(10L)
                .fixture(user, u -> u.after(v -> assertThat(v.getBalance()).isEqualTo(80L)))
                .entity(TestUser.class, e -> e.modified(1))
                .table("ft_order", order -> order.inserted(1)))
            .run();

        assertThat(queryForLong(accountDataSource, "select count(*) from ft_user")).isEqualTo(0L);
        assertThat(queryForLong(orderDataSource, "select count(*) from ft_order")).isEqualTo(0L);
    }

    private FixtureTrait<TestUser> idTrait(final Long id) {
        return FixtureTrait.mutate(new java.util.function.Consumer<TestUser>() {
            @Override
            public void accept(TestUser user) {
                user.setId(id);
            }
        });
    }

    private FixtureTrait<TestUser> tenantTrait(final Long tenantId) {
        return FixtureTrait.mutate(new java.util.function.Consumer<TestUser>() {
            @Override
            public void accept(TestUser user) {
                user.setTenantId(tenantId);
            }
        });
    }

    private FixtureTrait<TestUser> nameTrait(final String name) {
        return FixtureTrait.mutate(new java.util.function.Consumer<TestUser>() {
            @Override
            public void accept(TestUser user) {
                user.setName(name);
            }
        });
    }

    private FixtureTrait<TestUser> balanceTrait(final Long balance) {
        return FixtureTrait.mutate(new java.util.function.Consumer<TestUser>() {
            @Override
            public void accept(TestUser user) {
                user.setBalance(balance);
            }
        });
    }

    private void executeSql(DataSource dataSource, String sql) throws Exception {
        Connection connection = dataSource.getConnection();
        try {
            Statement statement = connection.createStatement();
            try {
                statement.execute(sql);
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    private long queryForLong(DataSource dataSource, String sql) throws Exception {
        Connection connection = dataSource.getConnection();
        try {
            Statement statement = connection.createStatement();
            try {
                ResultSet resultSet = statement.executeQuery(sql);
                try {
                    resultSet.next();
                    return resultSet.getLong(1);
                } finally {
                    resultSet.close();
                }
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean("orderDs")
        public DataSource orderDataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:flowtest_v2_testng_order_ds;MODE=MYSQL;DB_CLOSE_DELAY=-1");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean("accountDs")
        public DataSource accountDataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:flowtest_v2_testng_account_ds;MODE=MYSQL;DB_CLOSE_DELAY=-1");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        public JdbcObservationRegistry jdbcObservationRegistry() {
            return new JdbcObservationRegistry()
                .registerTable("ft_order", "id");
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
