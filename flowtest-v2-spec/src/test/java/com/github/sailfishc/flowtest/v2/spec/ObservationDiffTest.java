package com.github.sailfishc.flowtest.v2.spec;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ObservationDiffTest {

    @Test
    void shouldComputeInsertedDeletedAndModifiedRowsByIdentity() {
        ObservationSnapshot before = new ObservationSnapshot(Collections.singletonList(
            new ResourceSnapshot("orders", Arrays.asList(
                row(1L, "status", "NEW", "amount", 10),
                row(2L, "status", "NEW", "amount", 20)
            ))
        ));
        ObservationSnapshot after = new ObservationSnapshot(Collections.singletonList(
            new ResourceSnapshot("orders", Arrays.asList(
                row(2L, "status", "PAID", "amount", 20),
                row(3L, "status", "NEW", "amount", 30)
            ))
        ));

        ObservationDiff diff = ObservationDiff.between(before, after);

        ResourceChange orders = diff.getChange("orders");
        assertThat(orders.getInsertedCount()).isEqualTo(1);
        assertThat(orders.getDeletedCount()).isEqualTo(1);
        assertThat(orders.getModifiedCount()).isEqualTo(1);
        assertThat(orders.getInsertedRows().get(0).getColumn("id")).isEqualTo(3L);
        assertThat(orders.getDeletedRows().get(0).getColumn("id")).isEqualTo(1L);
        assertThat(orders.getModifiedRows().get(0).getBefore().getColumn("status")).isEqualTo("NEW");
        assertThat(orders.getModifiedRows().get(0).getAfter().getColumn("status")).isEqualTo("PAID");
    }

    private RowSnapshot row(Long id, Object... kv) {
        Map<String, Object> columns = new LinkedHashMap<String, Object>();
        columns.put("id", id);
        for (int i = 0; i < kv.length; i += 2) {
            columns.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return new RowSnapshot(RowKey.of(id), columns);
    }
}
