package com.flowtest.assertj;

import org.assertj.db.api.ChangesAssert;
import org.assertj.db.type.Changes;
import org.assertj.db.type.Table;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Wrapper around AssertJ-DB's Changes API for FlowTest integration.
 * Tracks database changes between start and end points.
 *
 * <p>Example usage:
 * <pre>{@code
 * FlowTestChanges changes = new FlowTestChanges(dataSource);
 * changes.setStartPointNow();
 *
 * // Execute business logic
 * orderService.createOrder(...);
 *
 * changes.setEndPointNow();
 *
 * // Assert changes
 * assertThat(changes.getChanges())
 *     .hasNumberOfChanges(1)
 *     .changeOnTable("t_order")
 *         .isCreation();
 * }</pre>
 */
public class FlowTestChanges {

    private final DataSource dataSource;
    private final Set<String> tables = new HashSet<>();
    private Changes changes;
    private boolean started = false;
    private boolean ended = false;

    /**
     * Creates a new FlowTestChanges tracking all tables.
     */
    public FlowTestChanges(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Creates a new FlowTestChanges tracking specific tables.
     */
    public FlowTestChanges(DataSource dataSource, String... tables) {
        this.dataSource = dataSource;
        this.tables.addAll(Arrays.asList(tables));
    }

    /**
     * Adds tables to track.
     */
    public FlowTestChanges watchTables(String... tables) {
        this.tables.addAll(Arrays.asList(tables));
        return this;
    }

    /**
     * Sets the start point for change tracking.
     * Call this before executing the business logic.
     */
    public FlowTestChanges setStartPointNow() {
        if (started) {
            throw new IllegalStateException("Start point already set");
        }

        if (tables.isEmpty()) {
            changes = new Changes(dataSource);
        } else {
            // Convert table names to Table objects
            Table[] tableArray = tables.stream()
                .map(name -> new Table(dataSource, name))
                .toArray(Table[]::new);
            changes = new Changes(tableArray);
        }

        changes.setStartPointNow();
        started = true;
        return this;
    }

    /**
     * Sets the end point for change tracking.
     * Call this after executing the business logic.
     */
    public FlowTestChanges setEndPointNow() {
        if (!started) {
            throw new IllegalStateException("Start point not set. Call setStartPointNow() first.");
        }
        if (ended) {
            throw new IllegalStateException("End point already set");
        }

        changes.setEndPointNow();
        ended = true;
        return this;
    }

    /**
     * Gets the underlying AssertJ-DB Changes object.
     */
    public Changes getChanges() {
        if (!ended) {
            throw new IllegalStateException("End point not set. Call setEndPointNow() first.");
        }
        return changes;
    }

    /**
     * Creates an assertion for the changes.
     */
    public ChangesAssert assertChanges() {
        return new ChangesAssert(getChanges());
    }

    /**
     * Checks if tracking has started.
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * Checks if tracking has ended.
     */
    public boolean isEnded() {
        return ended;
    }

    /**
     * Resets the tracking state for reuse.
     */
    public FlowTestChanges reset() {
        changes = null;
        started = false;
        ended = false;
        return this;
    }
}
