package com.flowtest.core.sharding;

import com.flowtest.core.annotation.ShardingKey;
import com.flowtest.core.fixture.EntityMetadata;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShardingKeyTest {

    @Test
    void detectsShardingKeyAnnotation() {
        EntityMetadata metadata = new EntityMetadata(OrderWithShardingKey.class);

        assertThat(metadata.hasShardingKey()).isTrue();
        assertThat(metadata.getShardingKeyField()).isNotNull();
        assertThat(metadata.getShardingKeyField().getName()).isEqualTo("userId");
        assertThat(metadata.getShardingKeyColumnName()).isEqualTo("user_id");
    }

    @Test
    void shardingKeyWithExplicitColumnName() {
        EntityMetadata metadata = new EntityMetadata(OrderWithCustomColumn.class);

        assertThat(metadata.hasShardingKey()).isTrue();
        assertThat(metadata.getShardingKeyColumnName()).isEqualTo("tenant_code");
    }

    @Test
    void noShardingKeyReturnsNull() {
        EntityMetadata metadata = new EntityMetadata(OrderWithoutShardingKey.class);

        assertThat(metadata.hasShardingKey()).isFalse();
        assertThat(metadata.getShardingKeyField()).isNull();
        assertThat(metadata.getShardingKeyColumnName()).isNull();
    }

    @Test
    void extractsShardingKeyValueFromEntity() {
        EntityMetadata metadata = new EntityMetadata(OrderWithShardingKey.class);

        OrderWithShardingKey order = new OrderWithShardingKey();
        order.setUserId(12345L);

        Object value = metadata.getShardingKeyValue(order);
        assertThat(value).isEqualTo(12345L);
    }

    @Test
    void extractsNullShardingKeyValue() {
        EntityMetadata metadata = new EntityMetadata(OrderWithShardingKey.class);

        OrderWithShardingKey order = new OrderWithShardingKey();
        // userId is null

        Object value = metadata.getShardingKeyValue(order);
        assertThat(value).isNull();
    }

    @Test
    void noShardingKeyReturnsNullValue() {
        EntityMetadata metadata = new EntityMetadata(OrderWithoutShardingKey.class);

        OrderWithoutShardingKey order = new OrderWithoutShardingKey();

        Object value = metadata.getShardingKeyValue(order);
        assertThat(value).isNull();
    }

    // Test entity classes

    static class OrderWithShardingKey {
        private Long id;

        @ShardingKey
        private Long userId;

        private String orderNo;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    }

    static class OrderWithCustomColumn {
        private Long id;

        @ShardingKey(column = "tenant_code")
        private String tenantId;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    }

    static class OrderWithoutShardingKey {
        private Long id;
        private Long userId;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
    }
}
