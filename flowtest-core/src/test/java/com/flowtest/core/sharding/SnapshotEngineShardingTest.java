package com.flowtest.core.sharding;

import com.flowtest.core.snapshot.SnapshotEngine;
import com.flowtest.core.snapshot.TableSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotEngineShardingTest {

    private EmbeddedDatabase db;
    private JdbcTemplate jdbcTemplate;
    private SnapshotEngine snapshotEngine;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName("testdb;DB_CLOSE_DELAY=-1")
            .build();
        jdbcTemplate = new JdbcTemplate(db);
        snapshotEngine = new SnapshotEngine(db);

        // Create test table with sharding key column
        jdbcTemplate.execute(
            "CREATE TABLE t_order (" +
            "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  user_id BIGINT NOT NULL," +
            "  order_no VARCHAR(50)," +
            "  amount DECIMAL(10,2)" +
            ")"
        );

        // Insert test data for different sharding key values
        jdbcTemplate.execute("INSERT INTO t_order (user_id, order_no, amount) VALUES (100, 'ORD001', 10.00)");
        jdbcTemplate.execute("INSERT INTO t_order (user_id, order_no, amount) VALUES (100, 'ORD002', 20.00)");
        jdbcTemplate.execute("INSERT INTO t_order (user_id, order_no, amount) VALUES (200, 'ORD003', 30.00)");
        jdbcTemplate.execute("INSERT INTO t_order (user_id, order_no, amount) VALUES (200, 'ORD004', 40.00)");
        jdbcTemplate.execute("INSERT INTO t_order (user_id, order_no, amount) VALUES (200, 'ORD005', 50.00)");
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    void snapshotWithoutShardingKeyReturnsAllRows() {
        Set<String> tables = Collections.singleton("t_order");
        Map<String, TableSnapshot> snapshot = snapshotEngine.takeBeforeSnapshot(tables);

        TableSnapshot orderSnapshot = snapshot.get("t_order");
        assertThat(orderSnapshot).isNotNull();
        assertThat(orderSnapshot.getRowCount()).isEqualTo(5);
        assertThat(orderSnapshot.getRowsByPrimaryKey()).hasSize(5);
    }

    @Test
    void snapshotWithShardingKeyFiltersRows() {
        Set<String> tables = Collections.singleton("t_order");
        Map<String, String> shardingColumns = Collections.singletonMap("t_order", "user_id");
        Map<String, Object> shardingValues = Collections.<String, Object>singletonMap("t_order", 100L);

        Map<String, TableSnapshot> snapshot = snapshotEngine.takeBeforeSnapshot(tables, shardingColumns, shardingValues);

        TableSnapshot orderSnapshot = snapshot.get("t_order");
        assertThat(orderSnapshot).isNotNull();
        assertThat(orderSnapshot.getRowCount()).isEqualTo(2);
        assertThat(orderSnapshot.getRowsByPrimaryKey()).hasSize(2);
    }

    @Test
    void snapshotWithDifferentShardingKeyValues() {
        Set<String> tables = Collections.singleton("t_order");

        // Snapshot for user_id = 100
        Map<String, String> shardingColumns = Collections.singletonMap("t_order", "user_id");
        Map<String, Object> shardingValues100 = Collections.<String, Object>singletonMap("t_order", 100L);
        Map<String, TableSnapshot> snapshot100 = snapshotEngine.takeBeforeSnapshot(tables, shardingColumns, shardingValues100);
        assertThat(snapshot100.get("t_order").getRowCount()).isEqualTo(2);

        // Snapshot for user_id = 200
        Map<String, Object> shardingValues200 = Collections.<String, Object>singletonMap("t_order", 200L);
        Map<String, TableSnapshot> snapshot200 = snapshotEngine.takeBeforeSnapshot(tables, shardingColumns, shardingValues200);
        assertThat(snapshot200.get("t_order").getRowCount()).isEqualTo(3);
    }

    @Test
    void beforeAfterSnapshotDiffWithShardingKey() {
        Set<String> tables = Collections.singleton("t_order");
        Map<String, String> shardingColumns = Collections.singletonMap("t_order", "user_id");
        Map<String, Object> shardingValues = Collections.<String, Object>singletonMap("t_order", 100L);

        // Take before snapshot
        Map<String, TableSnapshot> before = snapshotEngine.takeBeforeSnapshot(tables, shardingColumns, shardingValues);
        assertThat(before.get("t_order").getRowCount()).isEqualTo(2);

        // Insert new row with same sharding key
        jdbcTemplate.execute("INSERT INTO t_order (user_id, order_no, amount) VALUES (100, 'ORD006', 60.00)");

        // Also insert row with different sharding key (should not affect the diff)
        jdbcTemplate.execute("INSERT INTO t_order (user_id, order_no, amount) VALUES (300, 'ORD007', 70.00)");

        // Take after snapshot
        Map<String, TableSnapshot> after = snapshotEngine.takeAfterSnapshot(tables, shardingColumns, shardingValues);
        assertThat(after.get("t_order").getRowCount()).isEqualTo(3);

        // Compute diff - should only see 1 new row (the one with user_id=100)
        com.flowtest.core.snapshot.SnapshotDiff diff = snapshotEngine.computeDiff(before, after);
        assertThat(diff.getNewRowCount("t_order")).isEqualTo(1);
    }

    @Test
    void shardingKeyWithNoMatchingRowsReturnsEmptySnapshot() {
        Set<String> tables = Collections.singleton("t_order");
        Map<String, String> shardingColumns = Collections.singletonMap("t_order", "user_id");
        Map<String, Object> shardingValues = Collections.<String, Object>singletonMap("t_order", 999L);

        Map<String, TableSnapshot> snapshot = snapshotEngine.takeBeforeSnapshot(tables, shardingColumns, shardingValues);

        TableSnapshot orderSnapshot = snapshot.get("t_order");
        assertThat(orderSnapshot).isNotNull();
        assertThat(orderSnapshot.getRowCount()).isEqualTo(0);
        assertThat(orderSnapshot.getRowsByPrimaryKey()).isEmpty();
    }

    @Test
    void multipleTablesWithDifferentShardingKeys() {
        // Create another table
        jdbcTemplate.execute(
            "CREATE TABLE t_payment (" +
            "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  merchant_id VARCHAR(20) NOT NULL," +
            "  amount DECIMAL(10,2)" +
            ")"
        );
        jdbcTemplate.execute("INSERT INTO t_payment (merchant_id, amount) VALUES ('M001', 100.00)");
        jdbcTemplate.execute("INSERT INTO t_payment (merchant_id, amount) VALUES ('M001', 200.00)");
        jdbcTemplate.execute("INSERT INTO t_payment (merchant_id, amount) VALUES ('M002', 300.00)");

        Set<String> tables = new LinkedHashSet<>(Arrays.asList("t_order", "t_payment"));
        Map<String, String> shardingColumns = new HashMap<>();
        shardingColumns.put("t_order", "user_id");
        shardingColumns.put("t_payment", "merchant_id");

        Map<String, Object> shardingValues = new HashMap<>();
        shardingValues.put("t_order", 100L);
        shardingValues.put("t_payment", "M001");

        Map<String, TableSnapshot> snapshot = snapshotEngine.takeBeforeSnapshot(tables, shardingColumns, shardingValues);

        assertThat(snapshot.get("t_order").getRowCount()).isEqualTo(2);
        assertThat(snapshot.get("t_payment").getRowCount()).isEqualTo(2);

        // Cleanup
        jdbcTemplate.execute("DROP TABLE t_payment");
    }

    @Test
    void tableWithoutShardingKeyIgnoresShardingContext() {
        // Create a non-sharded table
        jdbcTemplate.execute(
            "CREATE TABLE t_config (" +
            "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  key_name VARCHAR(50)," +
            "  config_value VARCHAR(200)" +
            ")"
        );
        jdbcTemplate.execute("INSERT INTO t_config (key_name, config_value) VALUES ('config1', 'value1')");
        jdbcTemplate.execute("INSERT INTO t_config (key_name, config_value) VALUES ('config2', 'value2')");

        Set<String> tables = new LinkedHashSet<>(Arrays.asList("t_order", "t_config"));
        Map<String, String> shardingColumns = Collections.singletonMap("t_order", "user_id");
        Map<String, Object> shardingValues = Collections.<String, Object>singletonMap("t_order", 100L);

        Map<String, TableSnapshot> snapshot = snapshotEngine.takeBeforeSnapshot(tables, shardingColumns, shardingValues);

        // t_order should be filtered by sharding key
        assertThat(snapshot.get("t_order").getRowCount()).isEqualTo(2);
        // t_config has no sharding key configured, so all rows are returned
        assertThat(snapshot.get("t_config").getRowCount()).isEqualTo(2);

        // Cleanup
        jdbcTemplate.execute("DROP TABLE t_config");
    }
}
