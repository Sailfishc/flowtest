package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.traits.UserTraits;
import com.flowtest.core.TestFlow;
import com.flowtest.core.lifecycle.CleanupMode;
import com.flowtest.junit5.FlowTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for automatic cleanup functionality using SNAPSHOT_BASED mode.
 *
 * These tests verify that data created by persist() phase
 * is automatically cleaned up after each test when using @FlowTest(cleanup = SNAPSHOT_BASED).
 *
 * Note: @DirtiesContext is used to ensure test class isolation when running
 * with other test classes.
 */
@FlowTest(cleanup = CleanupMode.SNAPSHOT_BASED)
@SpringBootTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("自动清理功能测试")
class AutoCleanupTest {

    @Autowired
    TestFlow flow;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private static Long baselineUserCount;

    @BeforeAll
    static void recordBaseline(@Autowired JdbcTemplate jdbc) {
        baselineUserCount = jdbc.queryForObject("SELECT COUNT(*) FROM t_user", Long.class);
    }

    private Long getUserCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_user", Long.class);
    }

    @Test
    @Order(1)
    @DisplayName("第一个测试: 创建 persist 数据")
    void test01_createPersistData() {
        // Verify we start from baseline
        assertThat(getUserCount()).isEqualTo(baselineUserCount);

        // Create persist data
        flow.arrange()
            .add(User.class, UserTraits.normal(), UserTraits.balance(500.00))
            .persist();

        // Verify data was created
        assertThat(getUserCount()).isEqualTo(baselineUserCount + 1);
    }

    @Test
    @Order(2)
    @DisplayName("第二个测试: 验证前一个测试的 persist 数据已被自动清理")
    void test02_verifyPersistDataCleaned() {
        // Should be back to baseline - previous test's data was cleaned up
        assertThat(getUserCount()).isEqualTo(baselineUserCount);

        // Create new data in this test
        flow.arrange()
            .add(User.class, UserTraits.vip(), UserTraits.balance(1000.00))
            .persist();

        // Verify we have exactly one more user than baseline
        assertThat(getUserCount()).isEqualTo(baselineUserCount + 1);
    }

    @Test
    @Order(3)
    @DisplayName("第三个测试: 验证第二个测试的数据也被清理")
    void test03_verifySecondTestCleaned() {
        // Should be back to baseline
        assertThat(getUserCount()).isEqualTo(baselineUserCount);
    }

    @Test
    @Order(4)
    @DisplayName("第四个测试: 多实体批量创建后自动清理")
    void test04_batchEntitiesCleanup() {
        // Verify baseline
        assertThat(getUserCount()).isEqualTo(baselineUserCount);

        // Create multiple entities
        flow.arrange()
            .addMany(User.class, 5, UserTraits.normal(), UserTraits.balance(100.00))
            .persist();

        // Verify 5 users were created
        assertThat(getUserCount()).isEqualTo(baselineUserCount + 5);
    }

    @Test
    @Order(5)
    @DisplayName("第五个测试: 验证批量创建数据已被自动清理")
    void test05_verifyBatchDataCleaned() {
        // Should be back to baseline
        assertThat(getUserCount()).isEqualTo(baselineUserCount);
    }
}
