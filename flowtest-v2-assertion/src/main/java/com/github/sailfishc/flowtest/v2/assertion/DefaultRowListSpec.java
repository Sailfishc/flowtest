package com.github.sailfishc.flowtest.v2.assertion;

import com.github.sailfishc.flowtest.v2.spec.RowSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default collector implementation for {@link RowListSpec}.
 * Builds a {@link ResourceChangeAssertion} from the collected row expectations.
 */
final class DefaultRowListSpec implements RowListSpec {

    private String sortColumn;
    private final Map<Integer, RowAssertion> indexedAssertions = new LinkedHashMap<Integer, RowAssertion>();
    private final List<RowAssertion> eachAssertions = new ArrayList<RowAssertion>();
    private final List<RowAssertion> anyAssertions = new ArrayList<RowAssertion>();

    @Override
    public RowListSpec sortBy(String column) {
        this.sortColumn = column;
        return this;
    }

    @Override
    public RowListSpec row(int index, RowAssertion assertion) {
        if (index < 0) {
            throw new IllegalArgumentException("Row index must be >= 0, got " + index);
        }
        if (indexedAssertions.containsKey(index)) {
            throw new IllegalArgumentException("Duplicate row index: " + index + ". Each index can only be asserted once.");
        }
        indexedAssertions.put(index, assertion);
        return this;
    }

    @Override
    public RowListSpec eachRow(RowAssertion assertion) {
        eachAssertions.add(assertion);
        return this;
    }

    @Override
    public RowListSpec anyRow(RowAssertion assertion) {
        anyAssertions.add(assertion);
        return this;
    }

    /**
     * Builds a verifier function that operates on a list of rows (inserted, deleted, etc.).
     *
     * @throws IllegalStateException if no assertions were added
     */
    RowListVerifier buildVerifier() {
        if (indexedAssertions.isEmpty() && eachAssertions.isEmpty() && anyAssertions.isEmpty()) {
            throw new IllegalStateException("RowListSpec has no assertions. Add at least one row(), eachRow(), or anyRow() assertion.");
        }
        final String sortCol = this.sortColumn;
        final Map<Integer, RowAssertion> indexed = new LinkedHashMap<Integer, RowAssertion>(indexedAssertions);
        final List<RowAssertion> each = new ArrayList<RowAssertion>(eachAssertions);
        final List<RowAssertion> any = new ArrayList<RowAssertion>(anyAssertions);

        return new RowListVerifier() {
            @Override
            public void verify(List<RowSnapshot> rows) {
                List<RowSnapshot> effective = rows;
                if (sortCol != null) {
                    effective = sortRows(rows, sortCol);
                }
                verifyIndexed(effective, indexed);
                verifyEach(effective, each);
                verifyAny(effective, any);
            }
        };
    }

    private static List<RowSnapshot> sortRows(List<RowSnapshot> rows, final String column) {
        List<RowSnapshot> sorted = new ArrayList<RowSnapshot>(rows);
        Collections.sort(sorted, new Comparator<RowSnapshot>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public int compare(RowSnapshot a, RowSnapshot b) {
                Object va = a.getColumn(column);
                Object vb = b.getColumn(column);
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

    private static void verifyIndexed(List<RowSnapshot> rows, Map<Integer, RowAssertion> indexed) {
        for (Map.Entry<Integer, RowAssertion> entry : indexed.entrySet()) {
            int index = entry.getKey();
            if (index >= rows.size()) {
                throw new AssertionError("Row index " + index + " out of bounds, only " + rows.size() + " rows available");
            }
            entry.getValue().verify(rows.get(index));
        }
    }

    private static void verifyEach(List<RowSnapshot> rows, List<RowAssertion> assertions) {
        for (RowAssertion assertion : assertions) {
            for (int i = 0; i < rows.size(); i++) {
                try {
                    assertion.verify(rows.get(i));
                } catch (AssertionError ex) {
                    throw new AssertionError("Row " + i + " failed eachRow assertion: " + ex.getMessage(), ex);
                }
            }
        }
    }

    private static void verifyAny(List<RowSnapshot> rows, List<RowAssertion> assertions) {
        for (RowAssertion assertion : assertions) {
            boolean matched = false;
            AssertionError lastError = null;
            for (RowSnapshot row : rows) {
                try {
                    assertion.verify(row);
                    matched = true;
                    break;
                } catch (AssertionError ex) {
                    lastError = ex;
                }
            }
            if (!matched) {
                String message = "No row matched anyRow assertion";
                if (lastError != null) {
                    message += ": " + lastError.getMessage();
                }
                throw new AssertionError(message, lastError);
            }
        }
    }

    /**
     * Callback interface for verifying a list of rows.
     */
    interface RowListVerifier {
        void verify(List<RowSnapshot> rows);
    }
}
