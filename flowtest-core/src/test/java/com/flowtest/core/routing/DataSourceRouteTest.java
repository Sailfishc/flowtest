package com.flowtest.core.routing;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceRouteTest {

    @Test
    void explicitTablesAreLowercased() {
        Set<String> tables = new HashSet<String>(Arrays.asList("T_ORDER", "T_User"));
        DataSourceRoute route = new DataSourceRoute("ds1", tables);

        assertThat(route.getTables()).containsExactlyInAnyOrder("t_order", "t_user");
    }

    @Test
    void ownsTableMatchesExplicitTables() {
        Set<String> tables = new HashSet<String>(Arrays.asList("t_order", "t_product"));
        DataSourceRoute route = new DataSourceRoute("ds1", tables);

        assertThat(route.ownsTable("t_order")).isTrue();
        assertThat(route.ownsTable("T_ORDER")).isTrue();
        assertThat(route.ownsTable("t_user")).isFalse();
    }

    @Test
    void ownsTableMatchesDiscoveredTablesWhenNoExplicit() {
        DataSourceRoute route = new DataSourceRoute("ds1");
        route.addDiscoveredTables(new HashSet<String>(Arrays.asList("t_order", "t_product")));

        assertThat(route.ownsTable("t_order")).isTrue();
        assertThat(route.ownsTable("T_PRODUCT")).isTrue();
        assertThat(route.ownsTable("t_user")).isFalse();
    }

    @Test
    void explicitTablesTakePriorityOverDiscovered() {
        Set<String> explicit = new HashSet<String>(Arrays.asList("t_order"));
        DataSourceRoute route = new DataSourceRoute("ds1", explicit);
        // Even if we discover more tables, ownsTable only checks explicit set
        route.addDiscoveredTables(new HashSet<String>(Arrays.asList("t_product", "t_item")));

        assertThat(route.ownsTable("t_order")).isTrue();
        // discovered tables are not checked when explicit tables exist
        assertThat(route.ownsTable("t_product")).isFalse();
    }

    @Test
    void getAllTablesReturnsUnion() {
        Set<String> explicit = new HashSet<String>(Arrays.asList("t_order"));
        DataSourceRoute route = new DataSourceRoute("ds1", explicit);
        route.addDiscoveredTables(new HashSet<String>(Arrays.asList("t_product")));

        assertThat(route.getAllTables()).containsExactlyInAnyOrder("t_order", "t_product");
    }

    @Test
    void emptyRouteOwnsNothing() {
        DataSourceRoute route = new DataSourceRoute("ds1");
        assertThat(route.ownsTable("anything")).isFalse();
        assertThat(route.hasExplicitTables()).isFalse();
    }

    // --- Wildcard pattern tests ---

    @Test
    void wildcardEntriesAreSeparatedFromExactTables() {
        Set<String> mixed = new HashSet<String>(Arrays.asList("t_order", "t_user*", "t_log_*"));
        DataSourceRoute route = new DataSourceRoute("ds1", mixed);

        assertThat(route.getTables()).containsExactly("t_order");
        assertThat(route.getPatterns()).containsExactlyInAnyOrder("t_user*", "t_log_*");
    }

    @Test
    void matchesPatternWithSuffix() {
        Set<String> patterns = new HashSet<String>(Arrays.asList("t_order*"));
        DataSourceRoute route = new DataSourceRoute("ds1", patterns);

        assertThat(route.matchesPattern("t_order")).isTrue();
        assertThat(route.matchesPattern("t_order_item")).isTrue();
        assertThat(route.matchesPattern("t_order_detail")).isTrue();
        assertThat(route.matchesPattern("t_user")).isFalse();
    }

    @Test
    void matchesPatternWithPrefix() {
        Set<String> patterns = new HashSet<String>(Arrays.asList("*_log"));
        DataSourceRoute route = new DataSourceRoute("ds1", patterns);

        assertThat(route.matchesPattern("access_log")).isTrue();
        assertThat(route.matchesPattern("error_log")).isTrue();
        assertThat(route.matchesPattern("t_log_detail")).isFalse();
    }

    @Test
    void matchesPatternWithMiddleWildcard() {
        Set<String> patterns = new HashSet<String>(Arrays.asList("t_*_log"));
        DataSourceRoute route = new DataSourceRoute("ds1", patterns);

        assertThat(route.matchesPattern("t_access_log")).isTrue();
        assertThat(route.matchesPattern("t_error_log")).isTrue();
        assertThat(route.matchesPattern("t_log")).isFalse();
        assertThat(route.matchesPattern("t_access_log_detail")).isFalse();
    }

    @Test
    void matchesPatternCaseInsensitive() {
        Set<String> patterns = new HashSet<String>(Arrays.asList("T_ORDER*"));
        DataSourceRoute route = new DataSourceRoute("ds1", patterns);

        // Patterns are lowercased, so match is case-insensitive via the lower() call in matchesPattern
        assertThat(route.matchesPattern("T_ORDER_ITEM")).isTrue();
        assertThat(route.matchesPattern("t_order_item")).isTrue();
    }

    @Test
    void hasExplicitTablesIncludesPatterns() {
        Set<String> patternsOnly = new HashSet<String>(Arrays.asList("t_order*"));
        DataSourceRoute route = new DataSourceRoute("ds1", patternsOnly);

        // Route with only patterns should still report hasExplicitTables=true
        // to prevent auto-discovery from kicking in
        assertThat(route.hasExplicitTables()).isTrue();
        assertThat(route.getTables()).isEmpty();
        assertThat(route.getPatterns()).hasSize(1);
    }

    // --- wildcardMatch static method tests ---

    @Test
    void wildcardMatchHandlesEdgeCases() {
        // Just a star — matches everything
        assertThat(DataSourceRoute.wildcardMatch("anything", "*")).isTrue();
        assertThat(DataSourceRoute.wildcardMatch("", "*")).isTrue();

        // Double star
        assertThat(DataSourceRoute.wildcardMatch("abc", "**")).isTrue();

        // No wildcard — exact match
        assertThat(DataSourceRoute.wildcardMatch("abc", "abc")).isTrue();
        assertThat(DataSourceRoute.wildcardMatch("abc", "abx")).isFalse();

        // Star at end
        assertThat(DataSourceRoute.wildcardMatch("abc", "a*")).isTrue();
        assertThat(DataSourceRoute.wildcardMatch("abc", "ab*")).isTrue();
        assertThat(DataSourceRoute.wildcardMatch("abc", "abc*")).isTrue();
        assertThat(DataSourceRoute.wildcardMatch("abc", "abd*")).isFalse();

        // Star at beginning
        assertThat(DataSourceRoute.wildcardMatch("abc", "*c")).isTrue();
        assertThat(DataSourceRoute.wildcardMatch("abc", "*bc")).isTrue();
        assertThat(DataSourceRoute.wildcardMatch("abc", "*x")).isFalse();

        // Star in middle
        assertThat(DataSourceRoute.wildcardMatch("abc", "a*c")).isTrue();
        assertThat(DataSourceRoute.wildcardMatch("abbc", "a*c")).isTrue();
        assertThat(DataSourceRoute.wildcardMatch("ac", "a*c")).isTrue();
        assertThat(DataSourceRoute.wildcardMatch("abc", "a*x")).isFalse();
    }
}
