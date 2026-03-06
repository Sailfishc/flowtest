package com.github.sailfishc.flowtest.v2.assertion;

import com.github.sailfishc.flowtest.v2.spec.ModifiedRow;
import com.github.sailfishc.flowtest.v2.spec.ResourceChange;
import com.github.sailfishc.flowtest.v2.spec.RowKey;
import com.github.sailfishc.flowtest.v2.spec.RowSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RowListSpecTest {

    // --- RowAssertions.columns() ---

    @Test
    void columnsShouldMatchMultipleColumns() {
        RowSnapshot row = row(1L, "A_ID", 1L, "DETAIL", "detail_1", "STATUS", "CREATED");

        assertThatCode(() ->
            RowAssertions.columns("A_ID", 1L, "DETAIL", "detail_1", "STATUS", "CREATED").verify(row)
        ).doesNotThrowAnyException();
    }

    @Test
    void columnsShouldFailOnMismatch() {
        RowSnapshot row = row(1L, "A_ID", 1L, "DETAIL", "wrong");

        assertThatThrownBy(() ->
            RowAssertions.columns("A_ID", 1L, "DETAIL", "detail_1").verify(row)
        ).isInstanceOf(AssertionError.class).hasMessageContaining("DETAIL");
    }

    @Test
    void columnsShouldRejectOddPairs() {
        assertThatThrownBy(() ->
            RowAssertions.columns("A_ID", 1L, "DETAIL")
        ).isInstanceOf(IllegalArgumentException.class);
    }

    // --- RowListSpec: sortBy + indexed rows ---

    @Test
    void shouldSortAndVerifyByIndex() {
        List<RowSnapshot> rows = Arrays.asList(
            row(3L, "DETAIL", "third"),
            row(1L, "DETAIL", "first"),
            row(2L, "DETAIL", "second")
        );
        ResourceChange change = insertedChange("test_table", rows);

        assertThatCode(() ->
            RowListAssertions.insertedRows(spec -> spec
                .sortBy("id")
                .row(0, RowAssertions.columns("id", 1L, "DETAIL", "first"))
                .row(1, RowAssertions.columns("id", 2L, "DETAIL", "second"))
                .row(2, RowAssertions.columns("id", 3L, "DETAIL", "third"))
            ).verify(change)
        ).doesNotThrowAnyException();
    }

    @Test
    void shouldFailOnIndexOutOfBounds() {
        List<RowSnapshot> rows = Collections.singletonList(row(1L, "DETAIL", "only"));
        ResourceChange change = insertedChange("test_table", rows);

        assertThatThrownBy(() ->
            RowListAssertions.insertedRows(spec -> spec
                .row(5, RowAssertions.columnEquals("DETAIL", "nope"))
            ).verify(change)
        ).isInstanceOf(AssertionError.class).hasMessageContaining("out of bounds");
    }

    // --- RowListSpec: eachRow ---

    @Test
    void eachRowShouldPassWhenAllMatch() {
        List<RowSnapshot> rows = Arrays.asList(
            row(1L, "STATUS", "CREATED"),
            row(2L, "STATUS", "CREATED")
        );
        ResourceChange change = insertedChange("test_table", rows);

        assertThatCode(() ->
            RowListAssertions.insertedRows(spec -> spec
                .eachRow(RowAssertions.columnEquals("STATUS", "CREATED"))
            ).verify(change)
        ).doesNotThrowAnyException();
    }

    @Test
    void eachRowShouldFailWhenOneMismatches() {
        List<RowSnapshot> rows = Arrays.asList(
            row(1L, "STATUS", "CREATED"),
            row(2L, "STATUS", "PAID")
        );
        ResourceChange change = insertedChange("test_table", rows);

        assertThatThrownBy(() ->
            RowListAssertions.insertedRows(spec -> spec
                .eachRow(RowAssertions.columnEquals("STATUS", "CREATED"))
            ).verify(change)
        ).isInstanceOf(AssertionError.class).hasMessageContaining("eachRow");
    }

    // --- RowListSpec: anyRow ---

    @Test
    void anyRowShouldPassWhenAtLeastOneMatches() {
        List<RowSnapshot> rows = Arrays.asList(
            row(1L, "STATUS", "CREATED"),
            row(2L, "STATUS", "PAID")
        );
        ResourceChange change = insertedChange("test_table", rows);

        assertThatCode(() ->
            RowListAssertions.insertedRows(spec -> spec
                .anyRow(RowAssertions.columnEquals("STATUS", "PAID"))
            ).verify(change)
        ).doesNotThrowAnyException();
    }

    @Test
    void anyRowShouldFailWhenNoneMatches() {
        List<RowSnapshot> rows = Arrays.asList(
            row(1L, "STATUS", "CREATED"),
            row(2L, "STATUS", "CREATED")
        );
        ResourceChange change = insertedChange("test_table", rows);

        assertThatThrownBy(() ->
            RowListAssertions.insertedRows(spec -> spec
                .anyRow(RowAssertions.columnEquals("STATUS", "PAID"))
            ).verify(change)
        ).isInstanceOf(AssertionError.class).hasMessageContaining("anyRow");
    }

    // --- ModifiedRowListSpec ---

    @Test
    void shouldVerifyModifiedRowsSortedByAfterImage() {
        List<ModifiedRow> modifiedRows = Arrays.asList(
            new ModifiedRow(
                row(2L, "STATUS", "CREATED"),
                row(2L, "STATUS", "PAID")
            ),
            new ModifiedRow(
                row(1L, "STATUS", "CREATED"),
                row(1L, "STATUS", "SHIPPED")
            )
        );
        ResourceChange change = modifiedChange("test_table", modifiedRows);

        assertThatCode(() ->
            RowListAssertions.modifiedRows(spec -> spec
                .sortBy("id")
                .row(0, ModifiedRowAssertions.changed("STATUS", "CREATED", "SHIPPED"))
                .row(1, ModifiedRowAssertions.changed("STATUS", "CREATED", "PAID"))
            ).verify(change)
        ).doesNotThrowAnyException();
    }

    // --- Edge cases (P1 fixes) ---

    @Test
    void shouldRejectDuplicateRowIndex() {
        assertThatThrownBy(() ->
            RowListAssertions.insertedRows(spec -> spec
                .row(0, RowAssertions.columnEquals("STATUS", "A"))
                .row(0, RowAssertions.columnEquals("STATUS", "B"))
            )
        ).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate row index");
    }

    @Test
    void shouldRejectEmptyRowListSpec() {
        assertThatThrownBy(() ->
            RowListAssertions.insertedRows(spec -> {
                // empty — no assertions added
            })
        ).isInstanceOf(IllegalStateException.class).hasMessageContaining("no assertions");
    }

    @Test
    void shouldRejectEmptyModifiedRowListSpec() {
        assertThatThrownBy(() ->
            RowListAssertions.modifiedRows(spec -> spec.sortBy("id"))
        ).isInstanceOf(IllegalStateException.class).hasMessageContaining("no assertions");
    }

    // --- Helpers ---

    private static RowSnapshot row(Long id, Object... kv) {
        Map<String, Object> columns = new LinkedHashMap<String, Object>();
        columns.put("id", id);
        for (int i = 0; i < kv.length; i += 2) {
            columns.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return new RowSnapshot(RowKey.of(id), columns);
    }

    private static ResourceChange insertedChange(String name, List<RowSnapshot> inserted) {
        return new ResourceChange(name,
            inserted,
            Collections.<RowSnapshot>emptyList(),
            Collections.<ModifiedRow>emptyList());
    }

    private static ResourceChange modifiedChange(String name, List<ModifiedRow> modified) {
        return new ResourceChange(name,
            Collections.<RowSnapshot>emptyList(),
            Collections.<RowSnapshot>emptyList(),
            modified);
    }
}
