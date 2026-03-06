package com.github.sailfishc.flowtest.v2.assertion;

import com.github.sailfishc.flowtest.v2.spec.ModifiedRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default collector implementation for {@link ModifiedRowListSpec}.
 */
final class DefaultModifiedRowListSpec implements ModifiedRowListSpec {

    private String sortColumn;
    private final Map<Integer, ModifiedRowAssertion> indexedAssertions = new LinkedHashMap<Integer, ModifiedRowAssertion>();
    private final List<ModifiedRowAssertion> eachAssertions = new ArrayList<ModifiedRowAssertion>();

    @Override
    public ModifiedRowListSpec sortBy(String column) {
        this.sortColumn = column;
        return this;
    }

    @Override
    public ModifiedRowListSpec row(int index, ModifiedRowAssertion assertion) {
        if (index < 0) {
            throw new IllegalArgumentException("Row index must be >= 0, got " + index);
        }
        if (indexedAssertions.containsKey(index)) {
            throw new IllegalArgumentException("Duplicate modified row index: " + index + ". Each index can only be asserted once.");
        }
        indexedAssertions.put(index, assertion);
        return this;
    }

    @Override
    public ModifiedRowListSpec eachRow(ModifiedRowAssertion assertion) {
        eachAssertions.add(assertion);
        return this;
    }

    /**
     * Builds a verifier function that operates on a list of modified rows.
     *
     * @throws IllegalStateException if no assertions were added
     */
    ModifiedRowListVerifier buildVerifier() {
        if (indexedAssertions.isEmpty() && eachAssertions.isEmpty()) {
            throw new IllegalStateException("ModifiedRowListSpec has no assertions. Add at least one row() or eachRow() assertion.");
        }
        final String sortCol = this.sortColumn;
        final Map<Integer, ModifiedRowAssertion> indexed = new LinkedHashMap<Integer, ModifiedRowAssertion>(indexedAssertions);
        final List<ModifiedRowAssertion> each = new ArrayList<ModifiedRowAssertion>(eachAssertions);

        return new ModifiedRowListVerifier() {
            @Override
            public void verify(List<ModifiedRow> rows) {
                List<ModifiedRow> effective = rows;
                if (sortCol != null) {
                    effective = sortRows(rows, sortCol);
                }
                verifyIndexed(effective, indexed);
                verifyEach(effective, each);
            }
        };
    }

    private static List<ModifiedRow> sortRows(List<ModifiedRow> rows, final String column) {
        List<ModifiedRow> sorted = new ArrayList<ModifiedRow>(rows);
        Collections.sort(sorted, new Comparator<ModifiedRow>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public int compare(ModifiedRow a, ModifiedRow b) {
                Object va = a.getAfter().getColumn(column);
                Object vb = b.getAfter().getColumn(column);
                if (va == null && vb == null) {
                    return 0;
                }
                if (va == null) {
                    return -1;
                }
                if (vb == null) {
                    return 1;
                }
                if (va instanceof Comparable && vb instanceof Comparable) {
                    return ((Comparable) va).compareTo(vb);
                }
                throw new AssertionError("Cannot sort by column '" + column
                    + "': values are not Comparable (" + va.getClass().getName() + ")");
            }
        });
        return sorted;
    }

    private static void verifyIndexed(List<ModifiedRow> rows, Map<Integer, ModifiedRowAssertion> indexed) {
        for (Map.Entry<Integer, ModifiedRowAssertion> entry : indexed.entrySet()) {
            int index = entry.getKey();
            if (index >= rows.size()) {
                throw new AssertionError("Modified row index " + index + " out of bounds, only " + rows.size() + " rows available");
            }
            entry.getValue().verify(rows.get(index));
        }
    }

    private static void verifyEach(List<ModifiedRow> rows, List<ModifiedRowAssertion> assertions) {
        for (ModifiedRowAssertion assertion : assertions) {
            for (int i = 0; i < rows.size(); i++) {
                try {
                    assertion.verify(rows.get(i));
                } catch (AssertionError ex) {
                    throw new AssertionError("Modified row " + i + " failed eachRow assertion: " + ex.getMessage(), ex);
                }
            }
        }
    }

    /**
     * Callback interface for verifying a list of modified rows.
     */
    interface ModifiedRowListVerifier {
        void verify(List<ModifiedRow> rows);
    }
}
