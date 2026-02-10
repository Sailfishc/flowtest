package com.flowtest.core.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Routing configuration for one named datasource.
 * Holds an optional explicit table list, wildcard patterns, and auto-discovered tables.
 *
 * <p>Table entries containing {@code *} are treated as wildcard patterns.
 * For example, {@code t_order*} matches {@code t_order}, {@code t_order_item}, etc.
 * The {@code *} character matches zero or more characters.
 * Multiple {@code *} in a single pattern are supported (e.g., {@code *order*}).
 */
public class DataSourceRoute {

    private final String name;
    /** Exact table names (lowercase, no wildcards) */
    private final Set<String> tables;
    /** Wildcard patterns (lowercase, contain at least one '*') */
    private final List<String> patterns;
    private final Set<String> discoveredTables;

    /**
     * Creates a route with only a name (tables will be auto-discovered).
     *
     * @param name the datasource bean name
     */
    public DataSourceRoute(String name) {
        this(name, Collections.<String>emptySet());
    }

    /**
     * Creates a route with explicit table names and/or wildcard patterns.
     * Entries containing {@code *} are treated as patterns; others as exact names.
     *
     * @param name   the datasource bean name
     * @param tables explicit table names or wildcard patterns (will be lowercased)
     */
    public DataSourceRoute(String name, Set<String> tables) {
        this.name = name;
        this.tables = new LinkedHashSet<String>();
        this.patterns = new ArrayList<String>();
        for (String t : tables) {
            String lower = t.toLowerCase();
            if (lower.contains("*")) {
                this.patterns.add(lower);
            } else {
                this.tables.add(lower);
            }
        }
        this.discoveredTables = new LinkedHashSet<String>();
    }

    /**
     * Adds auto-discovered table names (lowercased).
     */
    public void addDiscoveredTables(Set<String> tableNames) {
        for (String t : tableNames) {
            discoveredTables.add(t.toLowerCase());
        }
    }

    /**
     * Checks if this route owns the given table via exact match.
     * Explicit tables take priority over discovered tables.
     * Does NOT check wildcard patterns — use {@link #matchesPattern(String)} for that.
     *
     * @param tableName the table name to check
     * @return true if this route owns the table by exact name
     */
    public boolean ownsTable(String tableName) {
        String lower = tableName.toLowerCase();
        if (!tables.isEmpty()) {
            return tables.contains(lower);
        }
        return discoveredTables.contains(lower);
    }

    /**
     * Checks if the given table name matches any wildcard pattern in this route.
     * The {@code *} character matches zero or more characters.
     *
     * @param tableName the table name to check
     * @return true if a pattern matches
     */
    public boolean matchesPattern(String tableName) {
        if (patterns.isEmpty()) {
            return false;
        }
        String lower = tableName.toLowerCase();
        for (String pattern : patterns) {
            if (wildcardMatch(lower, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Simple wildcard matching where {@code *} matches zero or more characters.
     * Uses a greedy left-to-right segment-matching algorithm.
     */
    static boolean wildcardMatch(String text, String pattern) {
        // Split pattern by '*' — each segment must appear in order within text
        String[] segments = pattern.split("\\*", -1);

        // Fast path: no wildcard at all
        if (segments.length == 1) {
            return text.equals(pattern);
        }

        int textPos = 0;

        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            if (seg.isEmpty()) {
                continue;
            }

            if (i == 0) {
                // First segment — text must start with it
                if (!text.startsWith(seg)) {
                    return false;
                }
                textPos = seg.length();
            } else if (i == segments.length - 1) {
                // Last segment — text must end with it
                if (!text.endsWith(seg)) {
                    return false;
                }
                // Make sure the ending doesn't overlap with already-consumed portion
                if (text.length() - seg.length() < textPos) {
                    return false;
                }
            } else {
                // Middle segment — must appear somewhere after current position
                int idx = text.indexOf(seg, textPos);
                if (idx < 0) {
                    return false;
                }
                textPos = idx + seg.length();
            }
        }

        return true;
    }

    /**
     * Returns all table names owned by this route (explicit + discovered).
     * Does not include wildcard patterns themselves.
     */
    public Set<String> getAllTables() {
        Set<String> all = new LinkedHashSet<String>(tables);
        all.addAll(discoveredTables);
        return Collections.unmodifiableSet(all);
    }

    public String getName() {
        return name;
    }

    /**
     * Returns exact table names (no wildcards).
     */
    public Set<String> getTables() {
        return Collections.unmodifiableSet(tables);
    }

    /**
     * Returns wildcard patterns configured for this route.
     */
    public List<String> getPatterns() {
        return Collections.unmodifiableList(patterns);
    }

    public Set<String> getDiscoveredTables() {
        return Collections.unmodifiableSet(discoveredTables);
    }

    /**
     * Returns true if this route has explicit tables or wildcard patterns.
     */
    public boolean hasExplicitTables() {
        return !tables.isEmpty() || !patterns.isEmpty();
    }
}
