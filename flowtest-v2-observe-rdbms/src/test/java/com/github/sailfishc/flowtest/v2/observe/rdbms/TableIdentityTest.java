package com.github.sailfishc.flowtest.v2.observe.rdbms;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TableIdentityTest {

    @Test
    void createsIdentityWithOrderedKeyColumns() {
        TableIdentity identity = TableIdentity.of("t_order", "tenant_id", "id");

        assertThat(identity.getTableName()).isEqualTo("t_order");
        assertThat(identity.getKeyColumns()).containsExactly("tenant_id", "id");
    }

    @Test
    void rejectsMissingKeyColumns() {
        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                TableIdentity.of("t_order");
            }
        }).isInstanceOf(IllegalArgumentException.class);
    }
}
