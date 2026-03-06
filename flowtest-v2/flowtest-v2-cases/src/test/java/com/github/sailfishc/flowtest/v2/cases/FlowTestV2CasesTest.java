package com.github.sailfishc.flowtest.v2.cases;

import com.github.sailfishc.flowtest.v2.FlowTestV2;
import com.github.sailfishc.flowtest.v2.assertion.ModifiedRowAssertions;
import com.github.sailfishc.flowtest.v2.assertion.RowAssertions;
import com.github.sailfishc.flowtest.v2.fixture.jdbc.JdbcFixtureExecutor;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcEntity;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationExecutor;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationRegistry;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutionResult;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutor;
import com.github.sailfishc.flowtest.v2.spec.CleanupPolicy;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
import com.github.sailfishc.flowtest.v2.spec.RouteCondition;
import com.github.sailfishc.flowtest.v2.spec.RouteScope;
import com.github.sailfishc.flowtest.v2.spec.TableRouteScope;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class FlowTestV2CasesTest {

    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource jdbcDataSource = new JdbcDataSource();
        jdbcDataSource.setURL("jdbc:h2:mem:cases;MODE=MYSQL;DB_CLOSE_DELAY=-1");
        jdbcDataSource.setUser("sa");
        jdbcDataSource.setPassword("");
        this.dataSource = jdbcDataSource;

        executeSql("drop table if exists ft_order_item");
        executeSql("drop table if exists ft_order");
        executeSql("drop table if exists ft_order_dynamic_a");
        executeSql("drop table if exists ft_order_dynamic_b");
        executeSql("drop table if exists ft_user");
        executeSql("create table ft_user (id bigint primary key, tenant_id bigint, name varchar(64), balance bigint)");
        executeSql("create table ft_order (id bigint primary key, tenant_id bigint, user_id bigint, status varchar(32))");
        executeSql("create table ft_order_item (id bigint primary key, order_id bigint, tenant_id bigint, sku varchar(32))");
        executeSql("create table ft_order_dynamic_a (id bigint primary key, tenant_id bigint, status varchar(32))");
        executeSql("create table ft_order_dynamic_b (id bigint primary key, tenant_id bigint, status varchar(32))");
    }

    @Test
    void shouldExecuteActOnlyScenarioAgainstMultipleTables() throws Exception {
        executeSql("insert into ft_order(id, tenant_id, user_id, status) values (900, 200, 9, 'HISTORICAL')");

        ScenarioExecutor executor = new ScenarioExecutor(new JdbcObservationExecutor(
            dataSource,
            new JdbcObservationRegistry()
                .registerTable("ft_order", "id")
                .registerTable("ft_order_item", "id")
        ));

        ScenarioExecutionResult<Long> result = FlowTestV2.scenario("act-only-order-create")
            .observe(o -> o.shardedTable("ft_order", RouteScope.of(RouteCondition.eq("tenant_id", 100L)))
                .shardedTable("ft_order_item", RouteScope.of(RouteCondition.eq("tenant_id", 100L))))
            .when(() -> {
                executeSql("insert into ft_order(id, tenant_id, user_id, status) values (1, 100, 7, 'CREATED')");
                executeSql("insert into ft_order_item(id, order_id, tenant_id, sku) values (11, 1, 100, 'SKU-1')");
                executeSql("insert into ft_order_item(id, order_id, tenant_id, sku) values (12, 1, 100, 'SKU-2')");
                return 1L;
            })
            .then(t -> t.expectNoException().inserted("ft_order", 1).inserted("ft_order_item", 2))
            .execute(executor);

        assertThat(result.getResult()).isEqualTo(1L);
        assertThat(queryForLong("select count(*) from ft_order where tenant_id = 100")).isEqualTo(0L);
        assertThat(queryForLong("select count(*) from ft_order_item where tenant_id = 100")).isEqualTo(0L);
        assertThat(queryForLong("select count(*) from ft_order where tenant_id = 200")).isEqualTo(1L);
    }

    @Test
    void shouldExecuteMixedFixtureAndWatchOnlyScenario() throws Exception {
        FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");
        JdbcObservationRegistry observationRegistry = new JdbcObservationRegistry()
            .registerEntity(TestUser.class)
            .registerTable("ft_order", "id");
        ScenarioExecutor executor = new ScenarioExecutor(
            new JdbcFixtureExecutor(dataSource, observationRegistry),
            new JdbcObservationExecutor(dataSource, observationRegistry)
        );

        ScenarioExecutionResult<String> result = FlowTestV2.scenario("mixed-user-order")
            .given(g -> g.persist(user,
                idTrait(1L),
                tenantTrait(100L),
                nameTrait("Alice"),
                balanceTrait(100L)))
            .observe(o -> o.fixture(user).shardedTable("ft_order", RouteScope.of(RouteCondition.eq("tenant_id", 100L))))
            .when(() -> {
                executeSql("update ft_user set balance = 80 where id = 1");
                executeSql("insert into ft_order(id, tenant_id, user_id, status) values (2, 100, 1, 'CREATED')");
                return "ok";
            })
            .then(t -> t.expectNoException()
                .fixture(user, value -> assertThat(value.getBalance()).isEqualTo(80L))
                .inserted("ft_order", 1)
                .modified(TestUser.class.getName(), 1))
            .execute(executor);

        assertThat(result.getResult()).isEqualTo("ok");
        assertThat(queryForLong("select count(*) from ft_user")).isEqualTo(0L);
        assertThat(queryForLong("select count(*) from ft_order where tenant_id = 100")).isEqualTo(0L);
    }

    @Test
    void shouldSupportRowLevelAssertionsForInsertedAndModifiedData() throws Exception {
        executeSql("insert into ft_order(id, tenant_id, user_id, status) values (301, 100, 7, 'CREATED')");

        ScenarioExecutor executor = new ScenarioExecutor(new JdbcObservationExecutor(
            dataSource,
            new JdbcObservationRegistry().registerTable("ft_order", "id")
        ));

        ScenarioExecutionResult<String> result = FlowTestV2.scenario("row-level-order")
            .observe(o -> o.shardedTable("ft_order", RouteScope.of(RouteCondition.eq("tenant_id", 100L))))
            .cleanup(CleanupPolicy.RESTORE_BEFORE_IMAGE)
            .when(() -> {
                executeSql("update ft_order set status = 'PAID' where id = 301");
                executeSql("insert into ft_order(id, tenant_id, user_id, status) values (302, 100, 8, 'CREATED')");
                return "changed";
            })
            .then(t -> t.expectNoException()
                .insertedRow("ft_order", RowAssertions.allOf(
                    RowAssertions.columnEquals("id", 302L),
                    RowAssertions.columnEquals("status", "CREATED")
                ))
                .modifiedRow("ft_order", ModifiedRowAssertions.changed("status", "CREATED", "PAID")))
            .execute(executor);

        assertThat(result.getResult()).isEqualTo("changed");
        assertThat(queryForString("select status from ft_order where id = 301")).isEqualTo("CREATED");
        assertThat(queryForLong("select count(*) from ft_order where id = 302")).isEqualTo(0L);
    }

    @Test
    void shouldRestoreWatchOnlyModifiedRowsWithBeforeImageCleanup() throws Exception {
        executeSql("insert into ft_order(id, tenant_id, user_id, status) values (101, 100, 7, 'CREATED')");
        executeSql("insert into ft_order(id, tenant_id, user_id, status) values (102, 200, 7, 'HISTORICAL')");

        ScenarioExecutor executor = new ScenarioExecutor(new JdbcObservationExecutor(
            dataSource,
            new JdbcObservationRegistry().registerTable("ft_order", "id")
        ));

        ScenarioExecutionResult<String> result = FlowTestV2.scenario("restore-modified-order")
            .observe(o -> o.shardedTable("ft_order", RouteScope.of(RouteCondition.eq("tenant_id", 100L))))
            .cleanup(CleanupPolicy.RESTORE_BEFORE_IMAGE)
            .when(() -> {
                executeSql("update ft_order set status = 'PAID' where id = 101");
                return "updated";
            })
            .then(t -> t.expectNoException().modified("ft_order", 1))
            .execute(executor);

        assertThat(result.getResult()).isEqualTo("updated");
        assertThat(queryForString("select status from ft_order where id = 101")).isEqualTo("CREATED");
        assertThat(queryForString("select status from ft_order where id = 102")).isEqualTo("HISTORICAL");
    }

    @Test
    void shouldRestoreWatchOnlyDeletedRowsWithBeforeImageCleanup() throws Exception {
        executeSql("insert into ft_order(id, tenant_id, user_id, status) values (201, 100, 7, 'CREATED')");
        executeSql("insert into ft_order(id, tenant_id, user_id, status) values (202, 200, 7, 'HISTORICAL')");

        ScenarioExecutor executor = new ScenarioExecutor(new JdbcObservationExecutor(
            dataSource,
            new JdbcObservationRegistry().registerTable("ft_order", "id")
        ));

        ScenarioExecutionResult<String> result = FlowTestV2.scenario("restore-deleted-order")
            .observe(o -> o.shardedTable("ft_order", RouteScope.of(RouteCondition.eq("tenant_id", 100L))))
            .cleanup(CleanupPolicy.RESTORE_BEFORE_IMAGE)
            .when(() -> {
                executeSql("delete from ft_order where id = 201");
                return "deleted";
            })
            .then(t -> t.expectNoException().deleted("ft_order", 1))
            .execute(executor);

        assertThat(result.getResult()).isEqualTo("deleted");
        assertThat(queryForString("select status from ft_order where id = 201")).isEqualTo("CREATED");
        assertThat(queryForString("select status from ft_order where id = 202")).isEqualTo("HISTORICAL");
    }

    @Test
    void shouldObserveAndCleanupAgainstResolvedDynamicTableName() throws Exception {
        executeSql("insert into ft_order_dynamic_b(id, tenant_id, status) values (501, 200, 'HISTORICAL')");

        ScenarioExecutor executor = new ScenarioExecutor(new JdbcObservationExecutor(
            dataSource,
            new JdbcObservationRegistry()
                .table("ft_order_dynamic", "id")
                .dynamicByKey("bucket")
                .register()
        ));

        ScenarioExecutionResult<String> result = FlowTestV2.scenario("dynamic-table-order")
            .observe(o -> o.shardedTable(
                "ft_order_dynamic",
                TableRouteScope.of("bucket", "a"),
                RouteScope.of(RouteCondition.eq("tenant_id", 100L))
            ))
            .when(() -> {
                executeSql("insert into ft_order_dynamic_a(id, tenant_id, status) values (401, 100, 'CREATED')");
                executeSql("insert into ft_order_dynamic_b(id, tenant_id, status) values (402, 100, 'UNTOUCHED')");
                return "ok";
            })
            .then(t -> t.expectNoException().inserted("ft_order_dynamic", 1))
            .execute(executor);

        assertThat(result.getResult()).isEqualTo("ok");
        assertThat(queryForLong("select count(*) from ft_order_dynamic_a")).isEqualTo(0L);
        assertThat(queryForLong("select count(*) from ft_order_dynamic_b")).isEqualTo(2L);
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

    @JdbcEntity(table = "ft_user", keyColumns = {"id"})
    private static final class TestUser {

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
