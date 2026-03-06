package com.github.sailfishc.flowtest.v2.observe.rdbms;

import com.github.sailfishc.flowtest.v2.spec.CleanupPolicy;
import com.github.sailfishc.flowtest.v2.spec.ObservationDiff;
import com.github.sailfishc.flowtest.v2.spec.ObservationSnapshot;
import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;
import com.github.sailfishc.flowtest.v2.spec.RouteCondition;
import com.github.sailfishc.flowtest.v2.spec.RouteScope;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcObservationExecutorTest {

    private DataSource dataSource;
    private JdbcObservationExecutor executor;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource jdbcDataSource = new JdbcDataSource();
        jdbcDataSource.setURL("jdbc:h2:mem:observe;MODE=MYSQL;DB_CLOSE_DELAY=-1");
        jdbcDataSource.setUser("sa");
        jdbcDataSource.setPassword("");
        this.dataSource = jdbcDataSource;
        this.executor = new JdbcObservationExecutor(dataSource, new JdbcObservationRegistry().registerTable("ft_order", "id"));

        Connection connection = dataSource.getConnection();
        try {
            Statement statement = connection.createStatement();
            try {
                statement.execute("drop table if exists ft_order");
                statement.execute("create table ft_order (id bigint primary key, tenant_id bigint, status varchar(32))");
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    @Test
    void shouldCaptureDiffAndDeleteInsertedRowsWithinRoute() throws Exception {
        List<ObservationSpec> observations = Collections.singletonList(
            ObservationSpec.table("ft_order", RouteScope.of(RouteCondition.eq("tenant_id", 100L)), false)
        );

        ObservationSnapshot before = executor.capture(observations);
        executeSql("insert into ft_order(id, tenant_id, status) values (1, 100, 'CREATED')");
        executeSql("insert into ft_order(id, tenant_id, status) values (2, 200, 'CREATED')");
        executeSql("update ft_order set status = 'PAID' where id = 1");
        ObservationSnapshot after = executor.capture(observations);

        ObservationDiff diff = ObservationDiff.between(before, after);

        assertThat(diff.getChange("ft_order").getInsertedCount()).isEqualTo(1L);
        assertThat(diff.getChange("ft_order").getModifiedCount()).isEqualTo(0L);
        executor.cleanup(observations, diff, CleanupPolicy.DELETE_INSERTED);
        assertThat(queryForLong("select count(*) from ft_order where tenant_id = 100")).isEqualTo(0L);
        assertThat(queryForLong("select count(*) from ft_order where tenant_id = 200")).isEqualTo(1L);
    }

    @Test
    void shouldRestoreModifiedAndDeletedRowsWithinRoute() throws Exception {
        List<ObservationSpec> observations = Collections.singletonList(
            ObservationSpec.table("ft_order", RouteScope.of(RouteCondition.eq("tenant_id", 100L)), false)
        );

        executeSql("insert into ft_order(id, tenant_id, status) values (1, 100, 'CREATED')");
        executeSql("insert into ft_order(id, tenant_id, status) values (2, 100, 'RESERVED')");
        executeSql("insert into ft_order(id, tenant_id, status) values (3, 200, 'HISTORICAL')");

        ObservationSnapshot before = executor.capture(observations);
        executeSql("update ft_order set status = 'PAID' where id = 1");
        executeSql("delete from ft_order where id = 2");
        executeSql("insert into ft_order(id, tenant_id, status) values (4, 100, 'CREATED')");
        ObservationSnapshot after = executor.capture(observations);

        ObservationDiff diff = ObservationDiff.between(before, after);

        assertThat(diff.getChange("ft_order").getInsertedCount()).isEqualTo(1L);
        assertThat(diff.getChange("ft_order").getModifiedCount()).isEqualTo(1L);
        assertThat(diff.getChange("ft_order").getDeletedCount()).isEqualTo(1L);

        executor.cleanup(observations, diff, CleanupPolicy.RESTORE_BEFORE_IMAGE);

        assertThat(queryForString("select status from ft_order where id = 1")).isEqualTo("CREATED");
        assertThat(queryForString("select status from ft_order where id = 2")).isEqualTo("RESERVED");
        assertThat(queryForString("select status from ft_order where id = 3")).isEqualTo("HISTORICAL");
        assertThat(queryForLong("select count(*) from ft_order where id = 4")).isEqualTo(0L);
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
            java.sql.ResultSet resultSet = connection.createStatement().executeQuery(sql);
            try {
                resultSet.next();
                return resultSet.getLong(1);
            } finally {
                resultSet.close();
            }
        } finally {
            connection.close();
        }
    }

    private String queryForString(String sql) throws Exception {
        Connection connection = dataSource.getConnection();
        try {
            Statement statement = connection.createStatement();
            try {
                ResultSet resultSet = statement.executeQuery(sql);
                try {
                    resultSet.next();
                    return resultSet.getString(1);
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
}
