package com.github.sailfishc.flowtest.v2.spring.boot;

import com.github.sailfishc.flowtest.v2.FlowTestV2;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcEntity;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationRegistry;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutionResult;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutor;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
import com.github.sailfishc.flowtest.v2.spec.RouteCondition;
import com.github.sailfishc.flowtest.v2.spec.RouteScope;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = FlowTestV2MultiDataSourceAutoConfigurationTest.TestApplication.class,
    properties = {
        "flowtest.v2.datasource.default-name=orderDs",
        "flowtest.v2.datasource.bindings[0].name=orderDs",
        "flowtest.v2.datasource.bindings[0].tables[0]=ft_order",
        "flowtest.v2.datasource.bindings[1].name=accountDs",
        "flowtest.v2.datasource.bindings[1].tables[0]=ft_user"
    }
)
class FlowTestV2MultiDataSourceAutoConfigurationTest {

    @Autowired
    @Qualifier("orderDs")
    private DataSource orderDataSource;

    @Autowired
    @Qualifier("accountDs")
    private DataSource accountDataSource;

    @Autowired
    private ScenarioExecutor scenarioExecutor;

    @BeforeEach
    void setUp() throws Exception {
        executeSql(orderDataSource, "drop table if exists ft_order");
        executeSql(accountDataSource, "drop table if exists ft_user");
        executeSql(orderDataSource, "create table ft_order (id bigint primary key, tenant_id bigint, user_id bigint, status varchar(32))");
        executeSql(accountDataSource, "create table ft_user (id bigint primary key, tenant_id bigint, name varchar(64), balance bigint)");
    }

    @Test
    void shouldRouteFixtureAndObservationAcrossConfiguredDataSources() throws Exception {
        FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");

        ScenarioExecutionResult<String> result = FlowTestV2.scenario("spring-boot-multi-datasource")
            .given(g -> g.persist(user,
                trait(new TraitSetter() {
                    @Override
                    public void apply(TestUser user) {
                        user.setId(1L);
                    }
                }),
                trait(new TraitSetter() {
                    @Override
                    public void apply(TestUser user) {
                        user.setTenantId(100L);
                    }
                }),
                trait(new TraitSetter() {
                    @Override
                    public void apply(TestUser user) {
                        user.setName("Alice");
                    }
                }),
                trait(new TraitSetter() {
                    @Override
                    public void apply(TestUser user) {
                        user.setBalance(100L);
                    }
                })))
            .observe(o -> o
                .fixture(user)
                .shardedTable("ft_order", RouteScope.of(RouteCondition.eq("tenant_id", 100L))))
            .when(() -> {
                executeSql(accountDataSource, "update ft_user set balance = 80 where id = 1");
                executeSql(orderDataSource, "insert into ft_order(id, tenant_id, user_id, status) values (10, 100, 1, 'CREATED')");
                return "ok";
            })
            .then(t -> t.expectNoException()
                .modified(TestUser.class.getName(), 1)
                .inserted("ft_order", 1))
            .execute(scenarioExecutor);

        assertThat(result.getResult()).isEqualTo("ok");
        assertThat(queryForLong(accountDataSource, "select count(*) from ft_user")).isEqualTo(0L);
        assertThat(queryForLong(orderDataSource, "select count(*) from ft_order")).isEqualTo(0L);
    }

    private FixtureTrait<TestUser> trait(final TraitSetter setter) {
        return FixtureTrait.of(new java.util.function.Consumer<TestUser>() {
            @Override
            public void accept(TestUser user) {
                setter.apply(user);
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
            dataSource.setURL("jdbc:h2:mem:flowtest_v2_order_ds;MODE=MYSQL;DB_CLOSE_DELAY=-1");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean("accountDs")
        public DataSource accountDataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:flowtest_v2_account_ds;MODE=MYSQL;DB_CLOSE_DELAY=-1");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        public JdbcObservationRegistry jdbcObservationRegistry() {
            return new JdbcObservationRegistry()
                .registerEntity(TestUser.class)
                .registerTable("ft_order", "id");
        }
    }

    private interface TraitSetter {
        void apply(TestUser user);
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
