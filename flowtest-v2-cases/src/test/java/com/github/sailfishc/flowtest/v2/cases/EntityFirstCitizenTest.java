package com.github.sailfishc.flowtest.v2.cases;

import com.github.sailfishc.flowtest.v2.FlowTestV2;
import com.github.sailfishc.flowtest.v2.assertion.ModifiedRowAssertions;
import com.github.sailfishc.flowtest.v2.assertion.RowAssertions;
import com.github.sailfishc.flowtest.v2.fixture.jdbc.JdbcFixtureExecutor;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcDynamicTable;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcEntity;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcIgnore;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationExecutor;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationRegistry;
import com.github.sailfishc.flowtest.v2.runtime.FixtureStatePatch;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutionResult;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutor;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle; // only needed for multi-fixture or cross-phase handle
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Entity-as-first-citizen reference test cases.
 *
 * <p>Demonstrates that when entities have proper annotations ({@code @JdbcEntity},
 * {@code @JdbcDynamicTable}), users get maximum auto-inference with minimal boilerplate:</p>
 * <ul>
 *   <li>No manual {@code registerEntity()} or {@code registerTable()} calls needed</li>
 *   <li>No explicit {@code observe()} in most scenarios</li>
 *   <li>Entity metadata (table name, key columns, property→column mapping) is auto-introspected</li>
 * </ul>
 *
 * <h2>When is explicit {@code observe()} required?</h2>
 * <ol>
 *   <li>Non-PK route filtering (e.g. {@code tenant_id}) — PK routes are auto-derived from fixture</li>
 *   <li>Act-only dynamic table without fixture — framework cannot derive the table suffix</li>
 * </ol>
 */
class EntityFirstCitizenTest {

    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource jdbcDataSource = new JdbcDataSource();
        jdbcDataSource.setURL("jdbc:h2:mem:entity_first_citizen;MODE=MYSQL;DB_CLOSE_DELAY=-1");
        jdbcDataSource.setUser("sa");
        jdbcDataSource.setPassword("");
        this.dataSource = jdbcDataSource;

        executeSql("drop table if exists ft_user");
        executeSql("drop table if exists ft_order");
        executeSql("drop table if exists ft_order_dynamic_a");
        executeSql("drop table if exists ft_order_dynamic_b");
        executeSql("create table ft_user (id bigint primary key, tenant_id bigint, name varchar(64), balance bigint)");
        executeSql("create table ft_order (id bigint primary key, tenant_id bigint, user_id bigint, amount bigint, status varchar(32))");
        executeSql("create table ft_order_dynamic_a (id bigint primary key, tenant_id bigint, user_id bigint, status varchar(32))");
        executeSql("create table ft_order_dynamic_b (id bigint primary key, tenant_id bigint, user_id bigint, status varchar(32))");
    }

    // ==================================================================================
    // Helper: build ScenarioExecutor with an empty registry (entity auto-registration)
    // ==================================================================================

    /**
     * Creates a ScenarioExecutor backed by a completely empty JdbcObservationRegistry.
     * No manual registerEntity() / registerTable() calls — all metadata is auto-introspected
     * from entity annotations when first referenced.
     */
    private ScenarioExecutor entityAutoExecutor() {
        JdbcObservationRegistry registry = new JdbcObservationRegistry();
        return new ScenarioExecutor(
            new JdbcFixtureExecutor(dataSource, registry),
            new JdbcObservationExecutor(dataSource, registry)
        );
    }

    // ==================================================================================
    // Section 1: Single Database — No observe() needed
    // ==================================================================================

    @Nested
    @DisplayName("Single DB — observe auto-inferred (no observe() call)")
    class SingleDbAutoInfer {

        /**
         * Simplest case: one fixture entity, modify it in act, assert via fixture + entity.
         *
         * <ul>
         *   <li>No FixtureHandle declaration — just use the Class</li>
         *   <li>No registerEntity() — auto-registered on first fixture/entity reference</li>
         *   <li>No observe() — auto-inferred from then().fixture() and then().entity()</li>
         * </ul>
         */
        @Test
        @DisplayName("fixture + entity assertion, zero observe, zero FixtureHandle")
        void shouldAutoInferSingleEntityObservation() throws Exception {
            FlowTestV2.scenario("single-entity-auto-infer")
                .given(g -> g.fixture(User.class,
                    withId(1L),
                    withTenantId(100L),
                    withName("Alice"),
                    withBalance(100L)))
                .when(() -> {
                    executeSql("update ft_user set balance = 80 where id = 1");
                    return "ok";
                })
                .then(t -> t
                    .success()
                    // fixture assertion: just pass the Class, framework matches by simpleName
                    .fixture(User.class, u -> u
                        .after(v -> assertThat(v.getBalance()).isEqualTo(80L)))
                    // entity assertion: auto-inferred observation for ft_user
                    .entity(User.class, e -> e.modified(1)))
                .execute(entityAutoExecutor());
        }

        /**
         * Two entity types: User (fixture) + Order (created by act).
         * Both observations are auto-inferred from then().
         */
        @Test
        @DisplayName("multiple entities, both auto-inferred from then()")
        void shouldAutoInferMultipleEntityObservations() throws Exception {
            ScenarioExecutionResult<Long> result = FlowTestV2.scenario("multi-entity-auto-infer")
                .given(g -> g.fixture(User.class,
                    withId(1L),
                    withTenantId(100L),
                    withName("Alice"),
                    withBalance(100L)))
                .when(() -> {
                    executeSql("update ft_user set balance = 80 where id = 1");
                    executeSql("insert into ft_order(id, tenant_id, user_id, amount, status) values (10, 100, 1, 20, 'CREATED')");
                    return 10L;
                })
                .then(t -> t
                    .success()
                    .returns(10L)
                    // User: fixture-backed observation (auto PK route: WHERE id = 1)
                    .fixture(User.class, u -> u.after(v -> assertThat(v.getBalance()).isEqualTo(80L)))
                    .entity(User.class, e -> e.modified(1))
                    // Order: watch-only observation (auto SELECT * FROM ft_order)
                    .entity(Order.class, e -> e.inserted(1)))
                .execute(entityAutoExecutor());

            assertThat(result.getResult()).isEqualTo(10L);
            // cleanup: both fixture + observed data are cleaned up
            assertThat(queryForLong("select count(*) from ft_user")).isEqualTo(0L);
            assertThat(queryForLong("select count(*) from ft_order")).isEqualTo(0L);
        }

        /**
         * Demonstrates fixture change assertion and afterMatches for concise property comparison.
         * Use change() when you need to compare before vs after — it gives reliable access to both.
         */
        @Test
        @DisplayName("fixture change() and afterMatches() — full lifecycle assertion")
        void shouldSupportFixtureLifecycleAssertions() throws Exception {
            FlowTestV2.scenario("fixture-lifecycle")
                .given(g -> g.fixture(User.class,
                    withId(1L),
                    withTenantId(100L),
                    withName("Alice"),
                    withBalance(100L)))
                .when(() -> {
                    executeSql("update ft_user set balance = 60, name = 'Alice (VIP)' where id = 1");
                    return null;
                })
                .then(t -> t
                    .success()
                    // change(): reliable before vs after comparison in a single assertion
                    .fixture(User.class, u -> u
                        .change((before, after) -> {
                            assertThat(before.getBalance()).isEqualTo(100L);
                            assertThat(after.getBalance()).isEqualTo(60L);
                            assertThat(after.getName()).isEqualTo("Alice (VIP)");
                        })))
                .execute(entityAutoExecutor());
        }

        /**
         * afterMatches(): declarative partial assertion — only check specific fields you care about.
         */
        @Test
        @DisplayName("afterMatches() — declarative partial field assertion")
        void shouldSupportAfterMatchesPatch() throws Exception {
            FlowTestV2.scenario("fixture-after-matches")
                .given(g -> g.fixture(User.class,
                    withId(1L),
                    withTenantId(100L),
                    withName("Alice"),
                    withBalance(100L)))
                .when(() -> {
                    executeSql("update ft_user set balance = 60, name = 'Alice (VIP)' where id = 1");
                    return null;
                })
                .then(t -> t
                    .success()
                    .fixture(User.class, u -> u
                        .afterMatches(FixtureStatePatch.of(User.class)
                            .set(User::getBalance, 60L)
                            .set(User::getName, "Alice (VIP)"))))
                .execute(entityAutoExecutor());
        }

        /**
         * Row-level assertion via entity(): insertedRow, modifiedRow.
         * Still no observe() needed — auto-inferred.
         */
        @Test
        @DisplayName("entity row-level assertions — insertedRow / modifiedRow")
        void shouldSupportEntityRowLevelAssertions() throws Exception {
            FlowTestV2.scenario("entity-row-level")
                .given(g -> g.fixture(User.class,
                    withId(1L),
                    withTenantId(100L),
                    withName("Alice"),
                    withBalance(100L)))
                .when(() -> {
                    executeSql("update ft_user set balance = 80 where id = 1");
                    executeSql("insert into ft_order(id, tenant_id, user_id, amount, status) values (10, 100, 1, 20, 'CREATED')");
                    return null;
                })
                .then(t -> t
                    .success()
                    .entity(User.class, e -> e
                        .modified(1)
                        .modifiedRow(ModifiedRowAssertions.changed("balance", 100L, 80L)))
                    .entity(Order.class, e -> e
                        .inserted(1)
                        .insertedRow(RowAssertions.columns(
                            "status", "CREATED",
                            "amount", 20L,
                            "user_id", 1L))))
                .execute(entityAutoExecutor());
        }

        /**
         * Multiple fixtures of the same entity type using batch rows DSL.
         * Each fixture gets its own PK-scoped observation for reload/assertion.
         */
        @Test
        @DisplayName("batch fixtures — multiple rows of same entity type")
        void shouldSupportBatchFixtures() throws Exception {
            FlowTestV2.scenario("batch-fixtures")
                .given(g -> g.fixtures(User.class, rows -> rows
                    .defaults(withTenantId(100L))
                    .row("alice", withId(1L), withName("Alice"), withBalance(100L))
                    .row("bob", withId(2L), withName("Bob"), withBalance(200L))))
                .when(() -> {
                    executeSql("update ft_user set balance = balance - 30 where tenant_id = 100");
                    return null;
                })
                .then(t -> t
                    .success()
                    // Use fixture assertions for each row — each auto-reloads by PK
                    .fixture("alice", User.class, u -> u.after(v -> assertThat(v.getBalance()).isEqualTo(70L)))
                    .fixture("bob", User.class, u -> u.after(v -> assertThat(v.getBalance()).isEqualTo(170L))))
                .execute(entityAutoExecutor());
        }
    }

    // ==================================================================================
    // Section 2: Single Database — Explicit observe() required
    // ==================================================================================

    @Nested
    @DisplayName("Single DB — explicit observe() required")
    class SingleDbExplicitObserve {

        /**
         * Non-PK route filtering: when you need WHERE tenant_id = ? (not just WHERE id = ?),
         * the framework cannot auto-derive it — you must declare observe().
         *
         * <p>This is necessary when the table contains rows from other tenants that you
         * don't want included in the observation diff.</p>
         */
        @Test
        @DisplayName("non-PK route (tenant_id) requires explicit observe")
        void shouldRequireObserveForNonPkRoute() throws Exception {
            // Pre-existing row from another tenant — should NOT be included in observation
            executeSql("insert into ft_order(id, tenant_id, user_id, amount, status) values (999, 200, 9, 50, 'HISTORICAL')");

            FlowTestV2.scenario("non-pk-route")
                .given(g -> g.fixture(User.class,
                    withId(1L),
                    withTenantId(100L),
                    withName("Alice"),
                    withBalance(100L)))
                // ⚠️ Required: filter ft_order by tenant_id so other tenants' rows are excluded
                .observe(o -> o.entity(Order.class, r -> r.route("tenant_id", 100L)))
                .when(() -> {
                    executeSql("update ft_user set balance = 80 where id = 1");
                    executeSql("insert into ft_order(id, tenant_id, user_id, amount, status) values (10, 100, 1, 20, 'CREATED')");
                    return 10L;
                })
                .then(t -> t
                    .success()
                    .fixture(User.class, u -> u.after(v -> assertThat(v.getBalance()).isEqualTo(80L)))
                    .entity(Order.class, e -> e.inserted(1)))
                .execute(entityAutoExecutor());

            // Verify: test data cleaned, historical row untouched
            assertThat(queryForLong("select count(*) from ft_order where tenant_id = 100")).isEqualTo(0L);
            assertThat(queryForLong("select count(*) from ft_order where tenant_id = 200")).isEqualTo(1L);
        }

        /**
         * Act-only scenario: no fixture, no entity reference in then() that can trigger auto-infer.
         * Must use observe() and entity() with explicit route.
         */
        @Test
        @DisplayName("act-only scenario with entity observe")
        void shouldRequireObserveForActOnlyEntity() throws Exception {
            executeSql("insert into ft_order(id, tenant_id, user_id, amount, status) values (999, 200, 9, 50, 'HISTORICAL')");

            ScenarioExecutionResult<Long> result = FlowTestV2.scenario("act-only-entity")
                // ⚠️ No given() — act-only, so we must explicitly declare what to observe
                .observe(o -> o.entity(Order.class, r -> r.route("tenant_id", 100L)))
                .when(() -> {
                    executeSql("insert into ft_order(id, tenant_id, user_id, amount, status) values (10, 100, 1, 20, 'CREATED')");
                    return 10L;
                })
                .then(t -> t
                    .success()
                    .entity(Order.class, e -> e
                        .inserted(1)
                        .insertedRow(RowAssertions.columns("id", 10L, "status", "CREATED"))))
                .execute(entityAutoExecutor());

            assertThat(result.getResult()).isEqualTo(10L);
            assertThat(queryForLong("select count(*) from ft_order where tenant_id = 100")).isEqualTo(0L);
            assertThat(queryForLong("select count(*) from ft_order where tenant_id = 200")).isEqualTo(1L);
        }
    }

    // ==================================================================================
    // Section 3: Dynamic Table — Fixture-backed auto-derivation (no observe)
    // ==================================================================================

    @Nested
    @DisplayName("Dynamic Table — fixture-backed auto-derive (no observe)")
    class DynamicTableAutoDerive {

        /**
         * When the dynamic table entity is used as a fixture AND the trait sets the routing
         * property (bucket), the framework auto-derives:
         * <ul>
         *   <li>TableRouteScope → resolves physical table name (ft_order_dynamic_a)</li>
         *   <li>RouteScope → WHERE id = ? from entity PK</li>
         * </ul>
         * Result: zero observe() needed.
         */
        @Test
        @DisplayName("fixture-backed dynamic table — auto-derives table suffix + PK route")
        void shouldAutoDeriveDynamicTableFromFixture() throws Exception {
            // Pre-existing row in shard b — should not be touched
            executeSql("insert into ft_order_dynamic_b(id, tenant_id, user_id, status) values (999, 200, 9, 'HISTORICAL')");

            // Two different entity types as fixtures — each uses Class-based reference
            FlowTestV2.scenario("dynamic-table-fixture-backed")
                .given(g -> {
                    g.fixture(User.class,
                        withId(1L),
                        withTenantId(100L),
                        withName("Alice"),
                        withBalance(100L));
                    g.fixture(DynamicOrder.class,
                        withOrderId(10L),
                        withOrderTenantId(100L),
                        withOrderUserId(1L),
                        withOrderStatus("PENDING"),
                        withBucket("a"));  // ← sets the dynamic table routing property
                })
                // No observe() — ObservationEnricher auto-derives from fixture:
                //   DynamicOrder.bucket="a" → ft_order_dynamic_a
                //   DynamicOrder.id=10 → WHERE id = 10
                .when(() -> {
                    executeSql("update ft_order_dynamic_a set status = 'PAID' where id = 10");
                    executeSql("update ft_user set balance = 80 where id = 1");
                    return "ok";
                })
                .then(t -> t
                    .success()
                    .fixture(User.class, u -> u.after(v -> assertThat(v.getBalance()).isEqualTo(80L)))
                    // Use change() for reliable before/after comparison
                    .fixture(DynamicOrder.class, o -> o
                        .change((before, after) -> {
                            assertThat(before.getStatus()).isEqualTo("PENDING");
                            assertThat(after.getStatus()).isEqualTo("PAID");
                        })))
                .execute(entityAutoExecutor());

            // Verify cleanup
            assertThat(queryForLong("select count(*) from ft_user")).isEqualTo(0L);
            assertThat(queryForLong("select count(*) from ft_order_dynamic_a")).isEqualTo(0L);
            // Shard b untouched
            assertThat(queryForLong("select count(*) from ft_order_dynamic_b")).isEqualTo(1L);
        }
    }

    // ==================================================================================
    // Section 4: Dynamic Table — Explicit observe() required
    // ==================================================================================

    @Nested
    @DisplayName("Dynamic Table — explicit observe() required")
    class DynamicTableExplicitObserve {

        /**
         * Act-only dynamic table: no fixture for the dynamic entity, so the framework
         * cannot derive the table suffix. Must declare observe() with dynamicTableBy().
         */
        @Test
        @DisplayName("act-only dynamic table requires observe with dynamicTableBy")
        void shouldRequireObserveForActOnlyDynamicTable() throws Exception {
            executeSql("insert into ft_order_dynamic_b(id, tenant_id, user_id, status) values (999, 200, 9, 'HISTORICAL')");

            ScenarioExecutionResult<String> result = FlowTestV2.scenario("dynamic-table-act-only")
                // ⚠️ Required: tell the framework which shard to observe
                .observe(o -> o.entity(DynamicOrder.class, r -> r
                    .dynamicTableBy("bucket", "a")
                    .route("tenant_id", 100L)))
                .when(() -> {
                    executeSql("insert into ft_order_dynamic_a(id, tenant_id, user_id, status) values (10, 100, 1, 'CREATED')");
                    return "ok";
                })
                .then(t -> t
                    .success()
                    .entity(DynamicOrder.class, e -> e
                        .inserted(1)
                        .insertedRow(RowAssertions.columns("id", 10L, "status", "CREATED"))))
                .execute(entityAutoExecutor());

            assertThat(result.getResult()).isEqualTo("ok");
            assertThat(queryForLong("select count(*) from ft_order_dynamic_a")).isEqualTo(0L);
            assertThat(queryForLong("select count(*) from ft_order_dynamic_b")).isEqualTo(1L);
        }

        /**
         * Fixture-backed dynamic table BUT with additional non-PK route filtering.
         * When using explicit observe() for a dynamic table, you must provide BOTH
         * dynamicTableBy (table suffix) and route (row filter).
         *
         * <p>This is because explicit observe() overrides fixture-backed auto-derive —
         * it uses WATCH_ONLY mode, not FIXTURE_BACKED mode.</p>
         */
        @Test
        @DisplayName("dynamic table + non-PK route requires both dynamicTableBy and route")
        void shouldRequireObserveForDynamicTableWithNonPkRoute() throws Exception {
            // Other tenant's row in shard a — must be excluded by tenant_id route
            executeSql("insert into ft_order_dynamic_a(id, tenant_id, user_id, status) values (999, 200, 9, 'OTHER_TENANT')");

            FlowTestV2.scenario("dynamic-table-fixture-with-route")
                .given(g -> {
                    g.fixture(User.class,
                        withId(1L),
                        withTenantId(100L),
                        withName("Alice"),
                        withBalance(100L));
                    g.fixture(DynamicOrder.class,
                        withOrderId(10L),
                        withOrderTenantId(100L),
                        withOrderUserId(1L),
                        withOrderStatus("PENDING"),
                        withBucket("a"));
                })
                // ⚠️ Explicit observe overrides auto-derive, so must provide full config:
                //    dynamicTableBy → table suffix, route → row filter
                .observe(o -> o.entity(DynamicOrder.class, r -> r
                    .dynamicTableBy("bucket", "a")
                    .route("tenant_id", 100L)))
                .when(() -> {
                    executeSql("update ft_order_dynamic_a set status = 'PAID' where id = 10");
                    return null;
                })
                .then(t -> t
                    .success()
                    .fixture(DynamicOrder.class, o -> o.after(v -> assertThat(v.getStatus()).isEqualTo("PAID")))
                    .entity(DynamicOrder.class, e -> e.modified(1)))
                .execute(entityAutoExecutor());

            // Cleanup only tenant 100 data
            assertThat(queryForLong("select count(*) from ft_order_dynamic_a where tenant_id = 100")).isEqualTo(0L);
            // Other tenant's row untouched
            assertThat(queryForLong("select count(*) from ft_order_dynamic_a where tenant_id = 200")).isEqualTo(1L);
        }
    }

    // ==================================================================================
    // Entity definitions — annotated, zero manual registration needed
    // ==================================================================================

    @JdbcEntity(table = "ft_user", keyColumns = {"id"})
    static final class User {
        private Long id;
        private Long tenantId;
        private String name;
        private Long balance;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getBalance() { return balance; }
        public void setBalance(Long balance) { this.balance = balance; }
    }

    @JdbcEntity(table = "ft_order", keyColumns = {"id"})
    static final class Order {
        private Long id;
        private Long tenantId;
        private Long userId;
        private Long amount;
        private String status;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getAmount() { return amount; }
        public void setAmount(Long amount) { this.amount = amount; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    @JdbcEntity(table = "ft_order_dynamic", keyColumns = {"id"})
    @JdbcDynamicTable(property = "bucket")
    static final class DynamicOrder {
        private Long id;
        private Long tenantId;
        private Long userId;
        private String status;
        @JdbcIgnore
        private String bucket;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
    }

    // ==================================================================================
    // Trait factories — reusable, composable entity customizers
    // ==================================================================================

    // --- User traits ---
    private static FixtureTrait<User> withId(Long id) { return FixtureTrait.mutate(u -> u.setId(id)); }
    private static FixtureTrait<User> withTenantId(Long tenantId) { return FixtureTrait.mutate(u -> u.setTenantId(tenantId)); }
    private static FixtureTrait<User> withName(String name) { return FixtureTrait.mutate(u -> u.setName(name)); }
    private static FixtureTrait<User> withBalance(Long balance) { return FixtureTrait.mutate(u -> u.setBalance(balance)); }

    // --- DynamicOrder traits ---
    private static FixtureTrait<DynamicOrder> withOrderId(Long id) { return FixtureTrait.mutate(o -> o.setId(id)); }
    private static FixtureTrait<DynamicOrder> withOrderTenantId(Long tenantId) { return FixtureTrait.mutate(o -> o.setTenantId(tenantId)); }
    private static FixtureTrait<DynamicOrder> withOrderUserId(Long userId) { return FixtureTrait.mutate(o -> o.setUserId(userId)); }
    private static FixtureTrait<DynamicOrder> withOrderStatus(String status) { return FixtureTrait.mutate(o -> o.setStatus(status)); }
    private static FixtureTrait<DynamicOrder> withBucket(String bucket) { return FixtureTrait.mutate(o -> o.setBucket(bucket)); }

    // ==================================================================================
    // JDBC helpers
    // ==================================================================================

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
}
