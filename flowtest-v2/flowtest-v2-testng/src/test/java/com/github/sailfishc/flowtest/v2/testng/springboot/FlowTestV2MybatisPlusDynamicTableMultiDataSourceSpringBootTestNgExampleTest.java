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
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutor;
import com.github.sailfishc.flowtest.v2.runtime.ScenarioExecutorProvider;
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
 * Copyable example for Spring Boot + TestNG + MyBatis-Plus + dynamic table + multi-datasource routing.
 */
@SpringBootTest(
    classes = FlowTestV2MybatisPlusDynamicTableMultiDataSourceSpringBootTestNgExampleTest.TestApplication.class,
    properties = {
        "flowtest.v2.datasource.default-name=orderDs",
        "flowtest.v2.datasource.bindings[0].name=accountDs",
        "flowtest.v2.datasource.bindings[0].tables[0]=ft_user",
        "flowtest.v2.datasource.bindings[1].name=orderDs",
        "flowtest.v2.datasource.bindings[1].patterns[0]=ft_mp_order_dynamic_*"
    }
)
@Listeners(FlowTestV2Listener.class)
public class FlowTestV2MybatisPlusDynamicTableMultiDataSourceSpringBootTestNgExampleTest
    extends AbstractTestNGSpringContextTests implements ScenarioExecutorProvider {

    @Autowired
    @Qualifier("orderJdbcTemplate")
    private JdbcTemplate orderJdbcTemplate;

    @Autowired
    @Qualifier("accountJdbcTemplate")
    private JdbcTemplate accountJdbcTemplate;

    @Autowired
    private ScenarioExecutor springScenarioExecutor;

    @Autowired
    private CompositeOrderService compositeOrderService;

    @BeforeMethod
    public void setUpSchema() {
        orderJdbcTemplate.execute("drop table if exists ft_mp_order_dynamic_a");
        orderJdbcTemplate.execute("drop table if exists ft_mp_order_dynamic_b");
        accountJdbcTemplate.execute("drop table if exists ft_user");

        orderJdbcTemplate.execute("create table ft_mp_order_dynamic_a (id bigint primary key, tenant_id bigint, user_id bigint, status varchar(32))");
        orderJdbcTemplate.execute("create table ft_mp_order_dynamic_b (id bigint primary key, tenant_id bigint, user_id bigint, status varchar(32))");
        accountJdbcTemplate.execute("create table ft_user (id bigint primary key, tenant_id bigint, name varchar(64), balance bigint)");
    }

    @Test
    public void shouldHandleMybatisPlusDynamicTableAcrossMultipleDataSources() throws Exception {
        FixtureHandle<TestUser> user = FixtureHandle.named(TestUser.class, "user");

        FlowTestV2.scenario("spring-boot-testng-mybatis-plus-dynamic-table-multi-datasource")
            .given(g -> g.persist(user,
                idTrait(1L),
                tenantTrait(100L),
                nameTrait("Alice"),
                balanceTrait(100L)))
            .watch(w -> w
                .fixture(user)
                .table("ft_mp_order_dynamic")
                    .dynamicTableBy("bucket", "a")
                    .route("tenant_id", 100L))
            .when(() -> compositeOrderService.createOrder("a", 100L, 1L, 801L))
            .verify(ctx -> {
                ctx.success();
                assertThat(ctx.result()).isEqualTo(801L);
                assertThat(ctx.fixture(user).after().getBalance()).isEqualTo(80L);
                assertThat(ctx.entity(TestUser.class).modifiedCount()).isEqualTo(1L);
                assertThat(ctx.table("ft_mp_order_dynamic").insertedCount()).isEqualTo(1L);
            })
            .run();

        assertThat(queryForLong(accountJdbcTemplate, "select count(*) from ft_user")).isEqualTo(0L);
        assertThat(queryForLong(orderJdbcTemplate, "select count(*) from ft_mp_order_dynamic_a")).isEqualTo(0L);
        assertThat(queryForLong(orderJdbcTemplate, "select count(*) from ft_mp_order_dynamic_b")).isEqualTo(0L);
    }

    @Override
    public ScenarioExecutor createScenarioExecutor() {
        return springScenarioExecutor;
    }

    private long queryForLong(JdbcTemplate jdbcTemplate, String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value.longValue();
    }

    private FixtureTrait<TestUser> idTrait(final Long id) {
        return FixtureTrait.of(new java.util.function.Consumer<TestUser>() {
            @Override
            public void accept(TestUser user) {
                user.setId(id);
            }
        });
    }

    private FixtureTrait<TestUser> tenantTrait(final Long tenantId) {
        return FixtureTrait.of(new java.util.function.Consumer<TestUser>() {
            @Override
            public void accept(TestUser user) {
                user.setTenantId(tenantId);
            }
        });
    }

    private FixtureTrait<TestUser> nameTrait(final String name) {
        return FixtureTrait.of(new java.util.function.Consumer<TestUser>() {
            @Override
            public void accept(TestUser user) {
                user.setName(name);
            }
        });
    }

    private FixtureTrait<TestUser> balanceTrait(final Long balance) {
        return FixtureTrait.of(new java.util.function.Consumer<TestUser>() {
            @Override
            public void accept(TestUser user) {
                user.setBalance(balance);
            }
        });
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean("orderDs")
        @Primary
        public DataSource orderDataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:flowtest_v2_testng_mybatis_plus_order_ds;MODE=MYSQL;DB_CLOSE_DELAY=-1");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean("accountDs")
        public DataSource accountDataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:flowtest_v2_testng_mybatis_plus_account_ds;MODE=MYSQL;DB_CLOSE_DELAY=-1");
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
            return new JdbcObservationRegistry()
                .registerEntity(TestUser.class)
                .registerEntity(DynamicOrderEntity.class)
                .table("ft_mp_order_dynamic", "id")
                .dynamicByKey("bucket")
                .register();
        }

        @Bean
        public CompositeOrderService compositeOrderService(DynamicOrderMapper mapper,
                                                          @Qualifier("accountJdbcTemplate") JdbcTemplate accountJdbcTemplate) {
            return new CompositeOrderService(mapper, accountJdbcTemplate);
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

    static final class CompositeOrderService {

        private final DynamicOrderMapper mapper;
        private final JdbcTemplate accountJdbcTemplate;

        CompositeOrderService(DynamicOrderMapper mapper, JdbcTemplate accountJdbcTemplate) {
            this.mapper = mapper;
            this.accountJdbcTemplate = accountJdbcTemplate;
        }

        long createOrder(String bucket, long tenantId, long userId, long orderId) {
            accountJdbcTemplate.update("update ft_user set balance = balance - 20 where id = ?", userId);

            DynamicOrderEntity entity = new DynamicOrderEntity();
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
    interface DynamicOrderMapper extends BaseMapper<DynamicOrderEntity> {
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

    @JdbcEntity(table = "ft_user", keyColumns = {"id"})
    static final class TestUser {

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

    @JdbcDynamicTable(property = "bucket")
    @TableName("ft_mp_order_dynamic")
    static final class DynamicOrderEntity {

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
