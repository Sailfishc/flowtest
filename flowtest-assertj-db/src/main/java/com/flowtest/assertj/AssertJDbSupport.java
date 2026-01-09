package com.flowtest.assertj;

import org.assertj.db.api.Assertions;
import org.assertj.db.api.ChangesAssert;
import org.assertj.db.type.Changes;

import javax.sql.DataSource;
import java.util.Set;

/**
 * Static utilities for AssertJ-DB integration.
 */
public final class AssertJDbSupport {

    private AssertJDbSupport() {
        // Utility class
    }

    /**
     * Creates a Changes tracker for the given data source.
     */
    public static FlowTestChanges changes(DataSource dataSource) {
        return new FlowTestChanges(dataSource);
    }

    /**
     * Creates a Changes tracker for specific tables.
     */
    public static FlowTestChanges changes(DataSource dataSource, String... tables) {
        return new FlowTestChanges(dataSource, tables);
    }

    /**
     * Creates a Changes tracker for specific tables.
     */
    public static FlowTestChanges changes(DataSource dataSource, Set<String> tables) {
        return new FlowTestChanges(dataSource, tables.toArray(new String[0]));
    }

    /**
     * Starts tracking changes on the given data source.
     * Convenience method that creates and starts a tracker in one call.
     */
    public static FlowTestChanges startTracking(DataSource dataSource) {
        return new FlowTestChanges(dataSource).setStartPointNow();
    }

    /**
     * Starts tracking changes on specific tables.
     */
    public static FlowTestChanges startTracking(DataSource dataSource, String... tables) {
        return new FlowTestChanges(dataSource, tables).setStartPointNow();
    }

    /**
     * Wraps AssertJ-DB's assertThat for Changes.
     */
    public static ChangesAssert assertThat(Changes changes) {
        return Assertions.assertThat(changes);
    }

    /**
     * Wraps AssertJ-DB's assertThat for FlowTestChanges.
     */
    public static ChangesAssert assertThat(FlowTestChanges changes) {
        return Assertions.assertThat(changes.getChanges());
    }
}
