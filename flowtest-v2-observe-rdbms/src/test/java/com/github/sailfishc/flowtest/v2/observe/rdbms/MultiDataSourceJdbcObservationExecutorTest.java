package com.github.sailfishc.flowtest.v2.observe.rdbms;

import com.github.sailfishc.flowtest.v2.spec.ObservationSnapshot;
import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;
import com.github.sailfishc.flowtest.v2.spec.RouteScope;
import com.github.sailfishc.flowtest.v2.spec.TableRouteScope;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class MultiDataSourceJdbcObservationExecutorTest {

    private JdbcDataSource orderDataSource;
    private JdbcDataSource accountDataSource;

    @BeforeEach
    void setUp() throws Exception {
        orderDataSource = dataSource("mds_order");
        accountDataSource = dataSource("mds_account");

        executeSql(orderDataSource, "drop table if exists ft_order_a");
        executeSql(accountDataSource, "drop table if exists ft_account");
        executeSql(orderDataSource, "create table ft_order_a (id bigint primary key, bucket varchar(16), status varchar(16))");
        executeSql(accountDataSource, "create table ft_account (id bigint primary key, name varchar(32))");
        executeSql(orderDataSource, "insert into ft_order_a(id, bucket, status) values (1, 'a', 'CREATED')");
    }

    @Test
    void shouldRouteDynamicPhysicalTableToMatchingDataSourceBinding() throws Exception {
        FlowTestDataSourceRegistry dataSourceRegistry = new FlowTestDataSourceRegistry()
            .register("orderDs", orderDataSource)
            .register("accountDs", accountDataSource)
            .bind("orderDs").pattern("ft_order_*").register()
            .defaultDataSource("accountDs");
        JdbcObservationRegistry observationRegistry = new JdbcObservationRegistry()
            .table("ft_order", "id")
            .dynamicByKey("bucket")
            .register();
        MultiDataSourceJdbcObservationExecutor executor = new MultiDataSourceJdbcObservationExecutor(
            dataSourceRegistry,
            observationRegistry
        );

        ObservationSnapshot snapshot = executor.capture(Collections.singletonList(
            ObservationSpec.table("ft_order", TableRouteScope.of("bucket", "a"), RouteScope.empty(), true)
        ));

        assertThat(snapshot.getResource("ft_order").getRows()).hasSize(1);
    }

    private JdbcDataSource dataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";MODE=MYSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private void executeSql(JdbcDataSource dataSource, String sql) throws Exception {
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

    @SuppressWarnings("unused")
    private long queryForLong(JdbcDataSource dataSource, String sql) throws Exception {
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
}
