package com.flowtest.core.routing;

import com.flowtest.core.persistence.EntityPersister;
import com.flowtest.core.persistence.JdbcEntityPersister;
import com.flowtest.core.snapshot.SnapshotDiff;
import com.flowtest.core.snapshot.SnapshotEngine;
import com.flowtest.core.snapshot.TableSnapshot;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test with two H2 in-memory databases:
 * - orderDb: contains T_ORDER, T_PRODUCT
 * - userDb:  contains T_USER
 *
 * Verifies that DataSourceRouter, RoutingEntityPersister, and RoutingSnapshotEngine
 * correctly route operations to the right database.
 */
class DataSourceRoutingIntegrationTest {

    private DataSource orderDs;
    private DataSource userDs;
    private SnapshotEngine orderEngine;
    private SnapshotEngine userEngine;
    private EntityPersister orderPersister;
    private EntityPersister userPersister;
    private DataSourceRouter router;

    @BeforeEach
    void setUp() throws Exception {
        // Create two separate H2 in-memory databases
        JdbcDataSource orderJdbc = new JdbcDataSource();
        orderJdbc.setURL("jdbc:h2:mem:orderDb_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        orderDs = orderJdbc;

        JdbcDataSource userJdbc = new JdbcDataSource();
        userJdbc.setURL("jdbc:h2:mem:userDb_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        userDs = userJdbc;

        // Create tables in each database
        try (Connection conn = orderDs.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE T_ORDER (id BIGINT AUTO_INCREMENT PRIMARY KEY, amount DECIMAL(10,2))");
            stmt.execute("CREATE TABLE T_PRODUCT (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100))");
        }
        try (Connection conn = userDs.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE T_USER (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(100))");
        }

        // Create per-datasource engines and persisters
        orderEngine = new SnapshotEngine(orderDs);
        userEngine = new SnapshotEngine(userDs);
        orderPersister = new JdbcEntityPersister(orderDs);
        userPersister = new JdbcEntityPersister(userDs);
    }

    private DataSourceRouter buildAutoDiscoveryRouter() {
        // Build routes with auto-discovery (no explicit tables)
        Map<String, DataSourceRoute> routes = new LinkedHashMap<String, DataSourceRoute>();
        routes.put("orderDs", new DataSourceRoute("orderDs"));
        routes.put("userDs", new DataSourceRoute("userDs"));

        Map<String, EntityPersister> persisters = new LinkedHashMap<String, EntityPersister>();
        persisters.put("orderDs", orderPersister);
        persisters.put("userDs", userPersister);

        Map<String, SnapshotEngine> engines = new LinkedHashMap<String, SnapshotEngine>();
        engines.put("orderDs", orderEngine);
        engines.put("userDs", userEngine);

        return new DataSourceRouter(routes, persisters, engines);
    }

    private DataSourceRouter buildExplicitRouter() {
        // Build routes with explicit table mapping
        Map<String, DataSourceRoute> routes = new LinkedHashMap<String, DataSourceRoute>();
        routes.put("orderDs", new DataSourceRoute("orderDs",
                new LinkedHashSet<String>(Arrays.asList("t_order", "t_product"))));
        routes.put("userDs", new DataSourceRoute("userDs",
                new LinkedHashSet<String>(Arrays.asList("t_user"))));

        Map<String, EntityPersister> persisters = new LinkedHashMap<String, EntityPersister>();
        persisters.put("orderDs", orderPersister);
        persisters.put("userDs", userPersister);

        Map<String, SnapshotEngine> engines = new LinkedHashMap<String, SnapshotEngine>();
        engines.put("orderDs", orderEngine);
        engines.put("userDs", userEngine);

        return new DataSourceRouter(routes, persisters, engines);
    }

    // --- DataSourceRouter tests ---

    @Test
    void autoDiscoveryBuildsCorrectTableMapping() {
        DataSourceRouter router = buildAutoDiscoveryRouter();

        // T_ORDER and T_PRODUCT should be in orderDs
        assertThat(router.resolveForTable("T_ORDER")).isEqualTo("orderDs");
        assertThat(router.resolveForTable("T_PRODUCT")).isEqualTo("orderDs");

        // T_USER should be in userDs
        assertThat(router.resolveForTable("T_USER")).isEqualTo("userDs");
    }

    @Test
    void explicitTablesOverrideDiscovery() {
        DataSourceRouter router = buildExplicitRouter();

        assertThat(router.resolveForTable("t_order")).isEqualTo("orderDs");
        assertThat(router.resolveForTable("t_product")).isEqualTo("orderDs");
        assertThat(router.resolveForTable("t_user")).isEqualTo("userDs");
    }

    @Test
    void unmatchedTableFallsBackToDefault() {
        DataSourceRouter router = buildAutoDiscoveryRouter();
        // A table not in either database should resolve to default
        assertThat(router.resolveForTable("t_unknown")).isEqualTo(DataSourceRouter.DEFAULT);
    }

    @Test
    void getSnapshotEngineForTableRoutesCorrectly() {
        DataSourceRouter router = buildAutoDiscoveryRouter();

        assertThat(router.getSnapshotEngineForTable("T_ORDER")).isSameAs(orderEngine);
        assertThat(router.getSnapshotEngineForTable("T_USER")).isSameAs(userEngine);
    }

    // --- RoutingSnapshotEngine tests ---

    @Test
    void routingSnapshotEngineListsAllTables() {
        DataSourceRouter router = buildAutoDiscoveryRouter();
        RoutingSnapshotEngine routingEngine = new RoutingSnapshotEngine(router);

        Set<String> allTables = routingEngine.listTableNames();
        // H2 returns uppercase table names
        assertThat(allTables).contains("T_ORDER", "T_PRODUCT", "T_USER");
    }

    @Test
    void routingSnapshotEngineTakesSnapshotsAcrossDatasources() throws Exception {
        DataSourceRouter router = buildAutoDiscoveryRouter();
        RoutingSnapshotEngine routingEngine = new RoutingSnapshotEngine(router);

        // Insert some data
        try (Connection conn = orderDs.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO T_ORDER (amount) VALUES (99.99)");
            stmt.execute("INSERT INTO T_PRODUCT (name) VALUES ('Widget')");
        }
        try (Connection conn = userDs.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO T_USER (username) VALUES ('alice')");
        }

        // Take snapshots across both datasources
        Set<String> tables = new LinkedHashSet<String>(Arrays.asList("T_ORDER", "T_PRODUCT", "T_USER"));
        Map<String, TableSnapshot> snapshots = routingEngine.takeBeforeSnapshot(tables);

        assertThat(snapshots).hasSize(3);
        assertThat(snapshots.get("T_ORDER").getRowCount()).isEqualTo(1L);
        assertThat(snapshots.get("T_PRODUCT").getRowCount()).isEqualTo(1L);
        assertThat(snapshots.get("T_USER").getRowCount()).isEqualTo(1L);
    }

    @Test
    void routingSnapshotEngineComputesDiffAcrossDatasources() throws Exception {
        DataSourceRouter router = buildAutoDiscoveryRouter();
        RoutingSnapshotEngine routingEngine = new RoutingSnapshotEngine(router);

        Set<String> tables = new LinkedHashSet<String>(Arrays.asList("T_ORDER", "T_USER"));

        // Take before snapshot (empty tables)
        Map<String, TableSnapshot> before = routingEngine.takeBeforeSnapshot(tables);

        // Insert data into both datasources
        try (Connection conn = orderDs.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO T_ORDER (amount) VALUES (50.00)");
        }
        try (Connection conn = userDs.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO T_USER (username) VALUES ('bob')");
        }

        // Take after snapshot
        Map<String, TableSnapshot> after = routingEngine.takeAfterSnapshot(tables);

        // computeDiff works on in-memory snapshots (not overridden, but uses overridden getIdColumnForTable)
        SnapshotDiff diff = routingEngine.computeDiff(before, after);

        assertThat(diff.getNewRowCount("T_ORDER")).isEqualTo(1L);
        assertThat(diff.getNewRowCount("T_USER")).isEqualTo(1L);
    }

    @Test
    void routingSnapshotEngineDeletesByPrimaryKeyAcrossDatasources() throws Exception {
        DataSourceRouter router = buildAutoDiscoveryRouter();
        RoutingSnapshotEngine routingEngine = new RoutingSnapshotEngine(router);

        // Insert data
        try (Connection conn = orderDs.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO T_ORDER (amount) VALUES (10.00)");
            stmt.execute("INSERT INTO T_ORDER (amount) VALUES (20.00)");
        }
        try (Connection conn = userDs.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO T_USER (username) VALUES ('carol')");
        }

        // Delete from order table (routes to orderDs)
        int deletedOrders = routingEngine.deleteRowsByPrimaryKeys("T_ORDER", "ID",
                Arrays.<Object>asList(1L, 2L));
        assertThat(deletedOrders).isEqualTo(2);

        // Delete from user table (routes to userDs)
        int deletedUsers = routingEngine.deleteRowsByPrimaryKeys("T_USER", "ID",
                Arrays.<Object>asList(1L));
        assertThat(deletedUsers).isEqualTo(1);

        // Verify tables are empty
        assertThat(orderEngine.takeAfterSnapshot(Collections.singleton("T_ORDER"))
                .get("T_ORDER").getRowCount()).isEqualTo(0L);
        assertThat(userEngine.takeAfterSnapshot(Collections.singleton("T_USER"))
                .get("T_USER").getRowCount()).isEqualTo(0L);
    }

    @Test
    void routingSnapshotEngineRoutesIdColumnDetection() throws Exception {
        DataSourceRouter router = buildAutoDiscoveryRouter();
        RoutingSnapshotEngine routingEngine = new RoutingSnapshotEngine(router);

        // Both engines should detect ID column from their respective databases
        String orderIdCol = routingEngine.getIdColumnForTable("T_ORDER");
        String userIdCol = routingEngine.getIdColumnForTable("T_USER");

        assertThat(orderIdCol).isEqualToIgnoringCase("ID");
        assertThat(userIdCol).isEqualToIgnoringCase("ID");
    }

    // --- Wildcard pattern routing tests ---

    private DataSourceRouter buildWildcardRouter() {
        Map<String, DataSourceRoute> routes = new LinkedHashMap<String, DataSourceRoute>();
        routes.put("orderDs", new DataSourceRoute("orderDs",
                new LinkedHashSet<String>(Arrays.asList("t_order*"))));
        routes.put("userDs", new DataSourceRoute("userDs",
                new LinkedHashSet<String>(Arrays.asList("t_user*"))));

        Map<String, EntityPersister> persisters = new LinkedHashMap<String, EntityPersister>();
        persisters.put("orderDs", orderPersister);
        persisters.put("userDs", userPersister);

        Map<String, SnapshotEngine> engines = new LinkedHashMap<String, SnapshotEngine>();
        engines.put("orderDs", orderEngine);
        engines.put("userDs", userEngine);

        return new DataSourceRouter(routes, persisters, engines);
    }

    @Test
    void wildcardPatternRoutesTablesByPrefix() {
        DataSourceRouter router = buildWildcardRouter();

        // t_order* pattern matches T_ORDER and T_PRODUCT? No — only t_order prefix
        assertThat(router.resolveForTable("t_order")).isEqualTo("orderDs");
        assertThat(router.resolveForTable("t_order_item")).isEqualTo("orderDs");
        assertThat(router.resolveForTable("t_order_detail")).isEqualTo("orderDs");

        assertThat(router.resolveForTable("t_user")).isEqualTo("userDs");
        assertThat(router.resolveForTable("t_user_info")).isEqualTo("userDs");

        // t_product doesn't match either pattern — fallback
        assertThat(router.resolveForTable("t_product")).isEqualTo(DataSourceRouter.DEFAULT);
    }

    @Test
    void exactMatchTakesPriorityOverWildcard() {
        // Mix exact + wildcard: exact "t_order" goes to userDs, wildcard "t_order*" goes to orderDs
        Map<String, DataSourceRoute> routes = new LinkedHashMap<String, DataSourceRoute>();
        routes.put("userDs", new DataSourceRoute("userDs",
                new LinkedHashSet<String>(Arrays.asList("t_order"))));
        routes.put("orderDs", new DataSourceRoute("orderDs",
                new LinkedHashSet<String>(Arrays.asList("t_order*"))));

        Map<String, EntityPersister> persisters = new LinkedHashMap<String, EntityPersister>();
        persisters.put("orderDs", orderPersister);
        persisters.put("userDs", userPersister);

        Map<String, SnapshotEngine> engines = new LinkedHashMap<String, SnapshotEngine>();
        engines.put("orderDs", orderEngine);
        engines.put("userDs", userEngine);

        DataSourceRouter router = new DataSourceRouter(routes, persisters, engines);

        // Exact match wins for "t_order"
        assertThat(router.resolveForTable("t_order")).isEqualTo("userDs");
        // Wildcard still works for other order tables
        assertThat(router.resolveForTable("t_order_item")).isEqualTo("orderDs");
    }

    @Test
    void wildcardSnapshotEngineRoutesCorrectly() throws Exception {
        DataSourceRouter router = buildWildcardRouter();
        RoutingSnapshotEngine routingEngine = new RoutingSnapshotEngine(router);

        // T_ORDER matches "t_order*" → orderDs engine
        // T_USER matches "t_user*" → userDs engine
        assertThat(router.getSnapshotEngineForTable("T_ORDER")).isSameAs(orderEngine);
        assertThat(router.getSnapshotEngineForTable("T_USER")).isSameAs(userEngine);

        // Snapshots should work across both datasources via wildcard routing
        try (Connection conn = orderDs.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO T_ORDER (amount) VALUES (42.00)");
        }
        try (Connection conn = userDs.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO T_USER (username) VALUES ('dave')");
        }

        Set<String> tables = new LinkedHashSet<String>(Arrays.asList("T_ORDER", "T_USER"));
        Map<String, TableSnapshot> snapshots = routingEngine.takeBeforeSnapshot(tables);

        assertThat(snapshots.get("T_ORDER").getRowCount()).isEqualTo(1L);
        assertThat(snapshots.get("T_USER").getRowCount()).isEqualTo(1L);
    }
}
