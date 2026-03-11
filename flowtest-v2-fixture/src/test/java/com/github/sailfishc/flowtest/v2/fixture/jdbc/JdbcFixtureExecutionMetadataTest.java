package com.github.sailfishc.flowtest.v2.fixture.jdbc;

import com.github.sailfishc.flowtest.v2.fixture.FixtureExecution;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationRegistry;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureSpec;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcFixtureExecutionMetadataTest {

    private JdbcDataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:jdbc_fixture_execution_metadata;MODE=MYSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        executeSql("drop table if exists ft_user");
        executeSql("create table ft_user (id bigint primary key, name varchar(64))");
    }

    @Test
    void shouldDescribeComparablePropertiesUsingRegistrationIgnores() throws Exception {
        FixtureHandle<MetadataUser> user = FixtureHandle.named(MetadataUser.class, "user");
        JdbcObservationRegistry registry = new JdbcObservationRegistry()
            .entity(MetadataUser.class, "ft_user", "id")
            .ignore("note")
            .register();
        JdbcFixtureExecutor executor = new JdbcFixtureExecutor(dataSource, registry);

        FixtureExecution execution = executor.prepare(Collections.<FixtureSpec<?>>singletonList(
            new FixtureSpec<MetadataUser>(user, MetadataUser.class, Arrays.<FixtureTrait<? super MetadataUser>>asList(
                FixtureTrait.mutate(v -> v.setId(1L)),
                FixtureTrait.mutate(v -> v.setName("Alice")),
                FixtureTrait.mutate(v -> v.setNote("internal"))
            ))
        ));
        try {
            assertThat(execution.describe(user).getComparableProperties())
                .containsExactlyInAnyOrder("id", "name");
        } finally {
            execution.cleanup();
        }
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

    static final class MetadataUser {
        private Long id;
        private String name;
        private String note;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }
}
