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
import com.github.sailfishc.flowtest.v2.observe.rdbms.JdbcObservationRegistry;
import com.github.sailfishc.flowtest.v2.testng.FlowTestV2Listener;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Copyable example for Spring Boot + TestNG + MyBatis-Plus + dynamic table names.
 */
@SpringBootTest(
    classes = FlowTestV2MybatisPlusDynamicTableSpringBootTestNgExampleTest.TestApplication.class,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:flowtest_v2_testng_mybatis_plus_dynamic;MODE=MYSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password="
    }
)
@Listeners(FlowTestV2Listener.class)
public class FlowTestV2MybatisPlusDynamicTableSpringBootTestNgExampleTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DynamicOrderService dynamicOrderService;

    @BeforeMethod
    public void setUpSchema() {
        jdbcTemplate.execute("drop table if exists ft_mp_order_dynamic_a");
        jdbcTemplate.execute("drop table if exists ft_mp_order_dynamic_b");
        jdbcTemplate.execute("create table ft_mp_order_dynamic_a (id bigint primary key, tenant_id bigint, status varchar(32))");
        jdbcTemplate.execute("create table ft_mp_order_dynamic_b (id bigint primary key, tenant_id bigint, status varchar(32))");
    }

    @Test
    public void shouldExecuteScenarioWithMybatisPlusAndDynamicTable() throws Exception {
        jdbcTemplate.update("insert into ft_mp_order_dynamic_b(id, tenant_id, status) values (702, 200, 'HISTORICAL')");

        FlowTestV2.scenario("spring-boot-testng-mybatis-plus-dynamic-table")
            .watch(w -> w.table("ft_mp_order_dynamic")
                .dynamicTableBy("bucket", "a")
                .route("tenant_id", 100L))
            .when(() -> dynamicOrderService.createOrder("a", 100L, 701L))
            .verify(ctx -> {
                ctx.success();
                assertThat(ctx.result()).isEqualTo(701L);
                assertThat(ctx.table("ft_mp_order_dynamic").insertedCount()).isEqualTo(1L);
                assertThat(ctx.table("ft_mp_order_dynamic").insertedOne().getColumn("id")).isEqualTo(701L);
                assertThat(ctx.table("ft_mp_order_dynamic").insertedOne().getColumn("tenant_id")).isEqualTo(100L);
                assertThat(ctx.table("ft_mp_order_dynamic").insertedOne().getColumn("status")).isEqualTo("CREATED");
            })
            .run();

        assertThat(queryForLong("select count(*) from ft_mp_order_dynamic_a")).isEqualTo(0L);
        assertThat(queryForLong("select count(*) from ft_mp_order_dynamic_b")).isEqualTo(1L);
        assertThat(queryForString("select status from ft_mp_order_dynamic_b where id = 702")).isEqualTo("HISTORICAL");
    }

    private long queryForLong(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value.longValue();
    }

    private String queryForString(String sql) {
        return jdbcTemplate.queryForObject(sql, String.class);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        public JdbcObservationRegistry jdbcObservationRegistry() {
            return new JdbcObservationRegistry()
                .registerEntity(DynamicOrderEntity.class)
                .table("ft_mp_order_dynamic", "id")
                .dynamicByKey("bucket")
                .register();
        }

        @Bean
        public DynamicOrderService dynamicOrderService(DynamicOrderMapper mapper) {
            return new DynamicOrderService(mapper);
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

    static final class DynamicOrderService {

        private final DynamicOrderMapper mapper;

        DynamicOrderService(DynamicOrderMapper mapper) {
            this.mapper = mapper;
        }

        long createOrder(String bucket, long tenantId, long orderId) {
            DynamicOrderEntity entity = new DynamicOrderEntity();
            entity.setId(orderId);
            entity.setTenantId(tenantId);
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

    @JdbcDynamicTable(property = "bucket")
    @TableName("ft_mp_order_dynamic")
    static final class DynamicOrderEntity {

        @TableId
        private Long id;

        @TableField("tenant_id")
        private Long tenantId;

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
