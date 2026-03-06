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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = FlowTestV2AutoConfigurationTest.TestApplication.class)
class FlowTestV2AutoConfigurationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ScenarioExecutor scenarioExecutor;

    @BeforeEach
    void setUp() throws Exception {
        executeSql("drop table if exists ft_order");
        executeSql("drop table if exists ft_user");
        executeSql("create table ft_user (id bigint primary key, tenant_id bigint, name varchar(64), balance bigint)");
        executeSql("create table ft_order (id bigint primary key, tenant_id bigint, user_id bigint, status varchar(32))");
    }

    @Test
    void shouldAutoConfigureScenarioExecutorForJdbcFlow() throws Exception {
        FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");

        ScenarioExecutionResult<String> result = FlowTestV2.scenario("spring-boot")
            .given(g -> g.persist(user,
                idTrait(1L),
                tenantTrait(100L),
                nameTrait("Alice"),
                balanceTrait(100L)))
            .observe(o -> o.fixture(user).shardedTable("ft_order", RouteScope.of(RouteCondition.eq("tenant_id", 100L))))
            .when(() -> {
                executeSql("update ft_user set balance = 80 where id = 1");
                executeSql("insert into ft_order(id, tenant_id, user_id, status) values (10, 100, 1, 'CREATED')");
                return "ok";
            })
            .then(t -> t.expectNoException().modified(TestUser.class.getName(), 1).inserted("ft_order", 1))
            .execute(scenarioExecutor);

        assertThat(result.getResult()).isEqualTo("ok");
        assertThat(queryForLong("select count(*) from ft_user")).isEqualTo(0L);
        assertThat(queryForLong("select count(*) from ft_order where tenant_id = 100")).isEqualTo(0L);
    }

    private FixtureTrait<TestUser> idTrait(final Long id) {
        return new FixtureTrait<TestUser>() {
            @Override
            public void apply(TestUser target, com.github.sailfishc.flowtest.v2.spec.TraitContext context) {
                target.setId(id);
            }
        };
    }

    private FixtureTrait<TestUser> tenantTrait(final Long tenantId) {
        return new FixtureTrait<TestUser>() {
            @Override
            public void apply(TestUser target, com.github.sailfishc.flowtest.v2.spec.TraitContext context) {
                target.setTenantId(tenantId);
            }
        };
    }

    private FixtureTrait<TestUser> nameTrait(final String name) {
        return new FixtureTrait<TestUser>() {
            @Override
            public void apply(TestUser target, com.github.sailfishc.flowtest.v2.spec.TraitContext context) {
                target.setName(name);
            }
        };
    }

    private FixtureTrait<TestUser> balanceTrait(final Long balance) {
        return new FixtureTrait<TestUser>() {
            @Override
            public void apply(TestUser target, com.github.sailfishc.flowtest.v2.spec.TraitContext context) {
                target.setBalance(balance);
            }
        };
    }

    private void executeSql(String sql) throws Exception {
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

    private long queryForLong(String sql) throws Exception {
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

        @Bean
        public JdbcObservationRegistry jdbcObservationRegistry() {
            return new JdbcObservationRegistry()
                .registerEntity(TestUser.class)
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
