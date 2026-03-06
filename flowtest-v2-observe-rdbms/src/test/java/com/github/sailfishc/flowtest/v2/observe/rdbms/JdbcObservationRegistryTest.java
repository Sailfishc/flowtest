package com.github.sailfishc.flowtest.v2.observe.rdbms;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.sailfishc.flowtest.v2.spec.ObservationSpec;
import com.github.sailfishc.flowtest.v2.spec.RouteCondition;
import com.github.sailfishc.flowtest.v2.spec.RouteScope;
import com.github.sailfishc.flowtest.v2.spec.TableRouteScope;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcObservationRegistryTest {

    @Test
    void shouldRegisterAnnotatedEntityMapping() {
        JdbcObservationRegistry registry = new JdbcObservationRegistry().registerEntity(AnnotatedUser.class);

        JdbcEntityRegistration registration = registry.getEntityRegistrations().get(AnnotatedUser.class);
        assertThat(registration).isNotNull();
        assertThat(registration.getIdentity().getTableName()).isEqualTo("ft_user");
        assertThat(registration.getIdentity().getKeyColumns()).containsExactly("id");
        assertThat(registration.getPropertyColumns()).containsEntry("displayName", "display_name");
        assertThat(registration.getIgnoredProperties()).contains("transientFlag");
    }

    @Test
    void shouldResolveDynamicPhysicalTableFromRouteScope() {
        JdbcObservationRegistry registry = new JdbcObservationRegistry()
            .table("ft_order", "id")
            .dynamicByKey("bucket")
            .register();

        JdbcObservationRegistry.JdbcObservedResource resource = registry.resolve(
            ObservationSpec.table("ft_order", TableRouteScope.of("bucket", "a"), RouteScope.empty(), true)
        );

        assertThat(resource.getIdentity().getTableName()).isEqualTo("ft_order_a");
    }

    @Test
    void shouldResolveDynamicEntityTableFromAnnotation() {
        JdbcObservationRegistry registry = new JdbcObservationRegistry().registerEntity(DynamicUser.class);
        DynamicUser user = new DynamicUser();
        user.setId(1L);
        user.setBucket("a");

        JdbcEntityRegistration registration = registry.getEntityRegistrations().get(DynamicUser.class);

        assertThat(registration.resolveTableName(user)).isEqualTo("ft_user_a");
    }

    @Test
    void shouldSupportCustomIgnoreResolverForOrmAnnotations() {
        JdbcObservationRegistry registry = new JdbcObservationRegistry()
            .addIgnorePropertyResolver(JdbcIgnorePropertyResolvers.annotation(FakeTransient.class.getName()))
            .registerEntity(OrmUser.class);

        JdbcEntityRegistration registration = registry.getEntityRegistrations().get(OrmUser.class);

        assertThat(registration.getIgnoredProperties()).contains("shadowField");
    }

    @Test
    void shouldRegisterMybatisPlusEntityWithoutJdbcEntity() {
        JdbcObservationRegistry registry = new JdbcObservationRegistry().registerEntity(MybatisPlusUser.class);

        JdbcEntityRegistration registration = registry.getEntityRegistrations().get(MybatisPlusUser.class);

        assertThat(registration).isNotNull();
        assertThat(registration.getIdentity().getTableName()).isEqualTo("ft_mp_user");
        assertThat(registration.getIdentity().getKeyColumns()).containsExactly("user_id");
        assertThat(registration.getPropertyColumns())
            .containsEntry("userId", "user_id")
            .containsEntry("displayName", "display_name");
        assertThat(registration.getIgnoredProperties()).contains("bucket");
        assertThat(registration.resolveTableName(newMybatisUser("a"))).isEqualTo("ft_mp_user_a");
    }

    private MybatisPlusUser newMybatisUser(String bucket) {
        MybatisPlusUser user = new MybatisPlusUser();
        user.setUserId(1L);
        user.setDisplayName("Alice");
        user.setBucket(bucket);
        return user;
    }

    @JdbcEntity(table = "ft_user", keyColumns = {"id"})
    static final class AnnotatedUser {

        private Long id;

        @JdbcColumn("display_name")
        private String displayName;

        @JdbcIgnore
        private String transientFlag;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getTransientFlag() {
            return transientFlag;
        }

        public void setTransientFlag(String transientFlag) {
            this.transientFlag = transientFlag;
        }
    }

    @JdbcEntity(table = "ft_user", keyColumns = {"id"})
    @JdbcDynamicTable(property = "bucket")
    static final class DynamicUser {

        private Long id;

        @JdbcIgnore
        private String bucket;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }

    @JdbcEntity(table = "ft_user", keyColumns = {"id"})
    static final class OrmUser {

        private Long id;

        @FakeTransient
        private String shadowField;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getShadowField() {
            return shadowField;
        }

        public void setShadowField(String shadowField) {
            this.shadowField = shadowField;
        }
    }

    @TableName("ft_mp_user")
    @JdbcDynamicTable(property = "bucket")
    static final class MybatisPlusUser {

        @TableId("user_id")
        private Long userId;

        @TableField("display_name")
        private String displayName;

        @TableField(exist = false)
        private String bucket;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD})
    @interface FakeTransient {
    }
}
