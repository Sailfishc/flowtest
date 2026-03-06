package com.github.sailfishc.flowtest.v2.junit5;

import com.github.sailfishc.flowtest.v2.FlowTestV2;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcEntity;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutionResult;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutor;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class FlowTestV2ExtensionAutoFixtureAdapterTest {

    private static final JdbcDataSource DATA_SOURCE = createDataSource();

    @RegisterExtension
    static final FlowTestV2Extension FLOW = FlowTestV2Extension.builder()
        .dataSource(DATA_SOURCE)
        .registerObservedEntity(TestUser.class)
        .build();

    @BeforeEach
    void setUp() throws Exception {
        executeSql("drop table if exists ft_user");
        executeSql("create table ft_user (id bigint primary key, tenant_id bigint, name varchar(64))");
    }

    @Test
    void shouldPersistFixtureWithoutCustomAdapter(ScenarioExecutor executor) throws Exception {
        FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");

        ScenarioExecutionResult<String> result = FlowTestV2.scenario("junit5-generic-fixture")
            .given(g -> g.persist(user,
                idTrait(1L),
                tenantTrait(100L),
                nameTrait("Alice")))
            .watch(w -> w.fixture(user))
            .when(() -> "ok")
            .then(t -> t.expectNoException().fixture(user, value -> assertThat(value.getName()).isEqualTo("Alice")))
            .run();

        assertThat(result.getResult()).isEqualTo("ok");
        assertThat(queryForLong("select count(*) from ft_user")).isEqualTo(0L);
        assertThat(executor).isNotNull();
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

    private static JdbcDataSource createDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:junit5_auto_fixture_adapter;MODE=MYSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private void executeSql(String sql) throws Exception {
        Connection connection = DATA_SOURCE.getConnection();
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
        Connection connection = DATA_SOURCE.getConnection();
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

    @JdbcEntity(table = "ft_user", keyColumns = {"id"})
    static final class TestUser {

        private Long id;
        private Long tenantId;
        private String name;

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
    }
}
