package com.github.sailfishc.flowtest.v2.fixture.jdbc;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GenericJdbcFixtureEntityAdapterTest {

    private JdbcDataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:generic_fixture_adapter;MODE=MYSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        executeSql("drop table if exists ft_user");
        executeSql("create table ft_user (id bigint primary key, tenant_id bigint, display_name varchar(64))");
    }

    @Test
    void shouldPersistReloadAndDeleteUsingConventionAndOverrides() throws Exception {
        Map<String, String> propertyColumns = new LinkedHashMap<String, String>();
        propertyColumns.put("name", "display_name");

        GenericJdbcFixtureEntityAdapter<TestUser> adapter = GenericJdbcFixtureEntityAdapter.of(
            TestUser.class,
            "ft_user",
            Collections.singletonList("id"),
            propertyColumns,
            Collections.<String>emptySet()
        );

        TestUser user = new TestUser();
        user.setId(1L);
        user.setTenantId(100L);
        user.setName("Alice");

        Connection connection = dataSource.getConnection();
        try {
            adapter.insert(connection, user);
            TestUser reloaded = adapter.reload(connection, user);
            assertThat(reloaded.getId()).isEqualTo(1L);
            assertThat(reloaded.getTenantId()).isEqualTo(100L);
            assertThat(reloaded.getName()).isEqualTo("Alice");

            adapter.delete(connection, user);
        } finally {
            connection.close();
        }

        assertThat(queryForLong("select count(*) from ft_user")).isEqualTo(0L);
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
