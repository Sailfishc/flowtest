package com.flowtest.core.sharding;

import com.flowtest.core.TestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestContextShardingKeyTest {

    private TestContext context;

    @BeforeEach
    void setUp() {
        context = new TestContext();
    }

    @Test
    void recordAndRetrieveShardingKey() {
        context.recordShardingKey("t_order", "user_id", 12345L);

        assertThat(context.hasShardingKey("t_order")).isTrue();
        assertThat(context.getShardingKeyValue("t_order")).isEqualTo(12345L);
        assertThat(context.getShardingKeyColumn("t_order")).isEqualTo("user_id");
    }

    @Test
    void shardingKeyIsCaseInsensitive() {
        context.recordShardingKey("T_ORDER", "user_id", 12345L);

        assertThat(context.hasShardingKey("t_order")).isTrue();
        assertThat(context.getShardingKeyValue("T_ORDER")).isEqualTo(12345L);
        assertThat(context.getShardingKeyColumn("t_Order")).isEqualTo("user_id");
    }

    @Test
    void multipleTablesWithDifferentShardingKeys() {
        context.recordShardingKey("t_order", "user_id", 100L);
        context.recordShardingKey("t_payment", "merchant_id", "M001");

        assertThat(context.hasShardingKey("t_order")).isTrue();
        assertThat(context.hasShardingKey("t_payment")).isTrue();
        assertThat(context.getShardingKeyValue("t_order")).isEqualTo(100L);
        assertThat(context.getShardingKeyValue("t_payment")).isEqualTo("M001");
    }

    @Test
    void nullShardingKeyIsNotRecorded() {
        context.recordShardingKey("t_order", "user_id", null);

        assertThat(context.hasShardingKey("t_order")).isFalse();
        assertThat(context.getShardingKeyValue("t_order")).isNull();
    }

    @Test
    void nonExistentTableReturnsNull() {
        assertThat(context.hasShardingKey("t_unknown")).isFalse();
        assertThat(context.getShardingKeyValue("t_unknown")).isNull();
        assertThat(context.getShardingKeyColumn("t_unknown")).isNull();
    }

    @Test
    void getShardingKeyValuesReturnsAllValues() {
        context.recordShardingKey("t_order", "user_id", 100L);
        context.recordShardingKey("t_item", "user_id", 100L);

        assertThat(context.getShardingKeyValues()).hasSize(2);
        assertThat(context.getShardingKeyValues()).containsEntry("t_order", 100L);
        assertThat(context.getShardingKeyValues()).containsEntry("t_item", 100L);
    }

    @Test
    void getShardingKeyColumnsReturnsAllColumns() {
        context.recordShardingKey("t_order", "user_id", 100L);
        context.recordShardingKey("t_payment", "merchant_id", "M001");

        assertThat(context.getShardingKeyColumns()).hasSize(2);
        assertThat(context.getShardingKeyColumns()).containsEntry("t_order", "user_id");
        assertThat(context.getShardingKeyColumns()).containsEntry("t_payment", "merchant_id");
    }

    @Test
    void clearRemovesShardingKeys() {
        context.recordShardingKey("t_order", "user_id", 100L);
        assertThat(context.hasShardingKey("t_order")).isTrue();

        context.clear();

        assertThat(context.hasShardingKey("t_order")).isFalse();
        assertThat(context.getShardingKeyValues()).isEmpty();
        assertThat(context.getShardingKeyColumns()).isEmpty();
    }
}
