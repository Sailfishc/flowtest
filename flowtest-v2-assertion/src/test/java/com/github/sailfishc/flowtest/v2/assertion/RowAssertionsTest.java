package com.github.sailfishc.flowtest.v2.assertion;

import com.github.sailfishc.flowtest.v2.spec.ModifiedRow;
import com.github.sailfishc.flowtest.v2.spec.RowKey;
import com.github.sailfishc.flowtest.v2.spec.RowSnapshot;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RowAssertionsTest {

    @Test
    void shouldAssertRowColumns() {
        RowSnapshot row = row(1L, "status", "CREATED", "tenant_id", 100L);

        assertThatCode(() -> RowAssertions.allOf(
            RowAssertions.columnEquals("status", "CREATED"),
            RowAssertions.columnEquals("tenant_id", 100L)
        ).verify(row)).doesNotThrowAnyException();
    }

    @Test
    void shouldAssertModifiedBeforeAndAfterColumns() {
        ModifiedRow row = new ModifiedRow(
            row(1L, "status", "CREATED"),
            row(1L, "status", "PAID")
        );

        assertThatCode(() -> ModifiedRowAssertions.changed("status", "CREATED", "PAID").verify(row))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenColumnDoesNotMatch() {
        RowSnapshot row = row(1L, "status", "CREATED");

        assertThatThrownBy(() -> RowAssertions.columnEquals("status", "PAID").verify(row))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("status");
    }

    private static RowSnapshot row(Long id, Object... kv) {
        Map<String, Object> columns = new LinkedHashMap<String, Object>();
        columns.put("id", id);
        for (int i = 0; i < kv.length; i += 2) {
            columns.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return new RowSnapshot(RowKey.of(id), columns);
    }
}
