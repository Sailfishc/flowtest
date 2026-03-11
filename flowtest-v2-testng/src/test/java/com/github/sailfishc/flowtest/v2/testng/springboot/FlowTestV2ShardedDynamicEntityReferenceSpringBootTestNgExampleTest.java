package com.github.sailfishc.flowtest.v2.testng.springboot;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TableNameHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.github.sailfishc.flowtest.v2.FlowTestV2;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcDynamicTable;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcEntity;
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationRegistry;
import com.github.sailfishc.flowtest.v2.spec.FixtureHandle;
import com.github.sailfishc.flowtest.v2.spec.FixtureTrait;
import com.github.sailfishc.flowtest.v2.testng.FlowTestV2Listener;
import org.apache.ibatis.annotations.Mapper;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Same business scenario as the table-based reference, but using typed entity watch.
 *
 * <p>This version shows the "give FlowTest a Class and let it infer metadata" path:
 * no explicit table registration is needed for the dynamic order resource.</p>
 */
@SpringBootTest(
    classes = FlowTestV2ShardedDynamicEntityReferenceSpringBootTestNgExampleTest.TestApplication.class,
    properties = {
        "flowtest.v2.datasource.default-name=orderDs",
        "flowtest.v2.datasource.bindings[0].name=accountDs",
        "flowtest.v2.datasource.bindings[0].tables[0]=ft_user_profile",
        "flowtest.v2.datasource.bindings[1].name=orderDs",
        "flowtest.v2.datasource.bindings[1].patterns[0]=ft_trade_order_*"
    }
)
@Listeners(FlowTestV2Listener.class)
public class FlowTestV2ShardedDynamicEntityReferenceSpringBootTestNgExampleTest
    extends AbstractTestNGSpringContextTests {

    @Autowired
    @Qualifier("orderJdbcTemplate")
    private JdbcTemplate orderJdbcTemplate;

    @Autowired
    @Qualifier("accountJdbcTemplate")
    private JdbcTemplate accountJdbcTemplate;

    @Autowired
    private TradeOrderService tradeOrderService;

    @BeforeMethod
    public void setUpSchema() {
        orderJdbcTemplate.execute("drop table if exists ft_trade_order_a");
        orderJdbcTemplate.execute("drop table if exists ft_trade_order_b");
        accountJdbcTemplate.execute("drop table if exists ft_user_profile");

        orderJdbcTemplate.execute(
            "create table ft_trade_order_a (id bigint primary key, tenant_id bigint, user_id bigint, status varchar(32))");
        orderJdbcTemplate.execute(
            "create table ft_trade_order_b (id bigint primary key, tenant_id bigint, user_id bigint, status varchar(32))");
        accountJdbcTemplate.execute(
            "create table ft_user_profile (id bigint primary key, tenant_id bigint, name varchar(64), level varchar(32))");
    }

    @Test
    public void shouldCreateShardedDynamicOrderUsingEntityWatch() throws Exception {
        FixtureHandle<UserProfile> user = FixtureHandle.named(UserProfile.class, "user");

        FlowTestV2.scenario("spring-boot-testng-single-table-fixture-and-sharded-dynamic-entity")
            .given(g -> g.fixture(user,
                idTrait(1L),
                tenantTrait(100L),
                nameTrait("Alice"),
                levelTrait("VIP")))
            .observe(o -> o.entity(TradeOrderEntity.class, r -> r
                .dynamicTableBy("bucket", "a")
                .route("tenant_id", 100L)))
            .when(() -> tradeOrderService.createTradeOrder("a", 100L, 1L, 902L))
            .then(t -> t
                .success()
                .returns(902L)
                .fixture(user, u -> u.after(v -> {
                    assertThat(v.getName()).isEqualTo("Alice");
                    assertThat(v.getLevel()).isEqualTo("VIP");
                }))
                .entity(UserProfile.class, e -> e.modified(0))
                .entity(TradeOrderEntity.class, e -> e
                    .inserted(1)
                    .inspect(ctx -> {
                        assertThat(ctx.insertedOne().getColumn("id")).isEqualTo(902L);
                        assertThat(ctx.insertedOne().getColumn("tenant_id")).isEqualTo(100L);
                        assertThat(ctx.insertedOne().getColumn("user_id")).isEqualTo(1L);
                        assertThat(ctx.insertedOne().getColumn("status")).isEqualTo("CREATED");
                    })))
            .run();

        assertThat(queryForLong(accountJdbcTemplate, "select count(*) from ft_user_profile")).isEqualTo(0L);
        assertThat(queryForLong(orderJdbcTemplate, "select count(*) from ft_trade_order_a")).isEqualTo(0L);
        assertThat(queryForLong(orderJdbcTemplate, "select count(*) from ft_trade_order_b")).isEqualTo(0L);
    }

    private long queryForLong(JdbcTemplate jdbcTemplate, String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value.longValue();
    }

    private FixtureTrait<UserProfile> idTrait(final Long id) {
        return FixtureTrait.mutate(user -> user.setId(id));
    }

    private FixtureTrait<UserProfile> tenantTrait(final Long tenantId) {
        return FixtureTrait.mutate(user -> user.setTenantId(tenantId));
    }

    private FixtureTrait<UserProfile> nameTrait(final String name) {
        return FixtureTrait.mutate(user -> user.setName(name));
    }

    private FixtureTrait<UserProfile> levelTrait(final String level) {
        return FixtureTrait.mutate(user -> user.setLevel(level));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean("orderDs")
        @Primary
        public DataSource orderDataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:flowtest_v2_testng_entity_order_ds;MODE=MYSQL;DB_CLOSE_DELAY=-1");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean("accountDs")
        public DataSource accountDataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:flowtest_v2_testng_entity_account_ds;MODE=MYSQL;DB_CLOSE_DELAY=-1");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean("orderJdbcTemplate")
        public JdbcTemplate orderJdbcTemplate(@Qualifier("orderDs") DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean("accountJdbcTemplate")
        public JdbcTemplate accountJdbcTemplate(@Qualifier("accountDs") DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public JdbcObservationRegistry jdbcObservationRegistry() {
            return new JdbcObservationRegistry();
        }

        @Bean
        public TradeOrderService tradeOrderService(TradeOrderMapper mapper,
                                                   @Qualifier("accountJdbcTemplate") JdbcTemplate accountJdbcTemplate) {
            return new TradeOrderService(mapper, accountJdbcTemplate);
        }

        @Bean
        public MybatisPlusInterceptor mybatisPlusInterceptor() {
            DynamicTableNameInnerInterceptor dynamicInterceptor = new DynamicTableNameInnerInterceptor();
            dynamicInterceptor.setTableNameHandler(new TableNameHandler() {
                @Override
                public String dynamicTableName(String sql, String tableName) {
                    String bucket = DynamicTableContext.currentBucket();
                    if (bucket == null || bucket.trim().isEmpty()) {
                        return tableName;
                    }
                    return tableName + "_" + bucket.trim();
                }
            });
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(dynamicInterceptor);
            return interceptor;
        }
    }

    static final class TradeOrderService {

        private final TradeOrderMapper mapper;
        private final JdbcTemplate accountJdbcTemplate;

        TradeOrderService(TradeOrderMapper mapper, JdbcTemplate accountJdbcTemplate) {
            this.mapper = mapper;
            this.accountJdbcTemplate = accountJdbcTemplate;
        }

        long createTradeOrder(String bucket, long tenantId, long userId, long orderId) {
            Long userCount = accountJdbcTemplate.queryForObject(
                "select count(*) from ft_user_profile where id = ? and tenant_id = ?",
                Long.class,
                userId,
                tenantId
            );
            if (userCount == null || userCount.longValue() == 0L) {
                throw new IllegalStateException("User fixture not found for tenant " + tenantId + ", userId " + userId);
            }

            TradeOrderEntity entity = new TradeOrderEntity();
            entity.setId(orderId);
            entity.setTenantId(tenantId);
            entity.setUserId(userId);
            entity.setStatus("CREATED");
            entity.setBucket(bucket);

            DynamicTableContext.setBucket(bucket);
            try {
                mapper.insert(entity);
            } finally {
                DynamicTableContext.clear();
            }
            return orderId;
        }
    }

    @Mapper
    interface TradeOrderMapper extends BaseMapper<TradeOrderEntity> {
    }

    static final class DynamicTableContext {

        private static final ThreadLocal<String> BUCKET = new ThreadLocal<String>();

        private DynamicTableContext() {
        }

        static void setBucket(String bucket) {
            BUCKET.set(bucket);
        }

        static String currentBucket() {
            return BUCKET.get();
        }

        static void clear() {
            BUCKET.remove();
        }
    }

    @JdbcEntity(table = "ft_user_profile", keyColumns = {"id"})
    static final class UserProfile {

        private Long id;
        private Long tenantId;
        private String name;
        private String level;

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

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }
    }

    @TableName("ft_trade_order")
    @JdbcDynamicTable(property = "bucket")
    static final class TradeOrderEntity {

        @TableId
        private Long id;

        @TableField("tenant_id")
        private Long tenantId;

        @TableField("user_id")
        private Long userId;

        private String status;

        @TableField(exist = false)
        private String bucket;

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

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }
}
